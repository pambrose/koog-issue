package goap

import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.agent.subtask
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.PlannerAIAgent
import ai.koog.agents.planner.goap.goap
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf
import kotlin.time.Duration.Companion.seconds

object ToolCallBaristaDemo {
  object IncreaseTemp : SimpleTool<IncreaseTemp.Args>(
    argsSerializer = Args.serializer(),
    name = "increase_temp",
    description = "Service tool, used by a node to increase the temperature."
  ) {
    @Serializable
    data class Args(
      @property:LLMDescription("Number of degrees to increase the temperature by")
      val degrees: Int,
    ) {
      companion object {
      }
    }

    override suspend fun execute(args: Args): String {
      println("Increasing temperature by ${args.degrees} degrees...")
      delay(1.seconds)
      return args.degrees.toString()
    }
  }

  const val REQUIRED_BEANS = 5
  const val REQUIRED_BREW_TEMP = 150
  const val BREW_INCREMENT = 10

  // Define a state for making coffee
  data class CoffeeState(
    val numberOfBeans: Int = 0,
    val hasWater: Boolean = false,
    val brewTemp: Int = 100,
    val hasMilk: Boolean = false,
    val isReady: Boolean = false,
  )

  @JvmStatic
  fun main(args: Array<String>) {
    // Create GOAP planner for making coffee
    val planner =
      goap<CoffeeState>(typeOf<CoffeeState>()) {
        action(
          name = "Get coffee beans",
          precondition = { state -> state.numberOfBeans < REQUIRED_BEANS },
          belief = { state -> state.copy(numberOfBeans = state.numberOfBeans + 1) },
          cost = { 1.0 }
        ) { ctx, state ->
          println("Getting coffee beans from the pantry... Number of beans: ${state.numberOfBeans}")
          state.copy(numberOfBeans = state.numberOfBeans + 1)
        }

        action(
          name = "Add water",
          precondition = { state -> !state.hasWater },
          belief = { state -> state.copy(hasWater = true) },
          cost = { 1.0 }
        ) { ctx, state ->
          println("Adding fresh water to the coffee maker...")
          state.copy(hasWater = true)
        }

        action(
          name = "Brew coffee",
          precondition = { state ->
            state.numberOfBeans >= REQUIRED_BEANS && state.hasWater && state.brewTemp < REQUIRED_BREW_TEMP
          },
          belief = { state -> state.copy(brewTemp = state.brewTemp + BREW_INCREMENT) },
          cost = { 2.0 }
        ) { ctx, state ->
          println("Brewing coffee... (this takes a moment) Coffee temperature: ${state.brewTemp}")
          val originalPrompt = ctx.llm.prompt

          ctx.llm.writeSession {
            rewritePrompt {
              prompt("temperature-controller") {
                system("You are a temperature controller. Use the available tools to adjust the temperature.")
              }
            }
          }

          // The problem is here: the subtask is failing in AIAgentFunctionalContext.sendToolResult after
          // appending the default finishTool results and calling requestLLM()
          val result =
            ctx.subtask<String, String>(
              input = "Turn up the temperature by $BREW_INCREMENT degrees",
              runMode = ToolCalls.SEQUENTIAL,
              tools = listOf(IncreaseTemp),
            ) { it }

          ctx.llm.writeSession {
            rewritePrompt { originalPrompt }
          }

          state.copy(brewTemp = state.brewTemp + result.toInt())
        }

        action(
          name = "Add milk",
          precondition = { state -> state.brewTemp >= REQUIRED_BREW_TEMP && !state.hasMilk },
          belief = { state -> state.copy(hasMilk = true, isReady = true) },
          cost = { 1.0 }
        ) { ctx, state ->
          println("Adding milk to coffee...")
          state.copy(hasMilk = true, isReady = true)
        }

        action(
          name = "Serve black coffee",
          precondition = { state -> state.brewTemp >= REQUIRED_BREW_TEMP && !state.isReady },
          belief = { state -> state.copy(isReady = true) },
          cost = { 0.5 }
        ) { ctx, state ->
          println("Serving coffee black (no milk)...")
          state.copy(isReady = true)
        }

        goal(
          name = "Coffee ready",
          description = "Coffee is ready to drink",
          condition = { state -> state.isReady }
        )
      }

    // Create and run the agent
    simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")).use { executor ->
      val agentConfig = AIAgentConfig(
        prompt = prompt("barista") {
          system("You are a helpful barista making coffee.")
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 20
      )

      val agent =
        PlannerAIAgent(
          promptExecutor = executor,
          strategy = AIAgentPlannerStrategy("coffee-maker", planner),
          agentConfig = agentConfig,
          toolRegistry = ToolRegistry { tool(IncreaseTemp) }
        )

      println("Starting to make coffee...")
      runBlocking {
        val result = agent.run(CoffeeState())
        println("\nFinal state: $result")
        println("Coffee is ready!")
      }
    }
  }
}