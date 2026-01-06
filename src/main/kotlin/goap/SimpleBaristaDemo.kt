package goap

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.PlannerAIAgent
import ai.koog.agents.planner.goap.goap
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
import kotlin.reflect.typeOf
import kotlin.time.Duration.Companion.seconds

object SimpleBaristaDemo {
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
          precondition = { state -> state.numberOfBeans >= REQUIRED_BEANS && state.hasWater && state.brewTemp < REQUIRED_BREW_TEMP },
          belief = { state -> state.copy(brewTemp = state.brewTemp + BREW_INCREMENT) },
          cost = { 2.0 }
        ) { ctx, state ->
          println("Brewing coffee... (this takes a moment) Coffee temperature: ${state.brewTemp}")
          Thread.sleep(1.seconds.inWholeMilliseconds)
          state.copy(brewTemp = state.brewTemp + BREW_INCREMENT)
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

    // Run the agent
    simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")).use { executor ->
      val agentConfig = AIAgentConfig(
        prompt = prompt("barista") { system("You are a helpful barista making coffee.") },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 20
      )

      val agent =
        PlannerAIAgent(
          promptExecutor = executor,
          strategy = AIAgentPlannerStrategy("coffee-maker", planner),
          agentConfig = agentConfig
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