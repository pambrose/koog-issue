# Koog GOAP Issue

This repository contains a demo of an issue in the Koog GOAP library.

The issue can be found in src/main/kotlingoap/ToolCallBaristaDemo.kt.
The **Brew coffee** action invokes **ctx.subtask()** near line 103,
which is supposed to result in a tool call to increase the temperature. When run, you can see the tool call
being successfully made, but the subtask fails in **AIAgentFunctionalContext.sendToolResult()** after appending
the default finishTool results and calling requestLLM().

The result error is:

```
Exception in thread "main" ai.koog.prompt.executor.clients.LLMClientException: Error from client: OpenAILLMClient
Error from client: OpenAILLMClient
Status code: 400
Error body:
{
  "error": {
    "message": "Invalid parameter: messages with role 'tool' must be a response to a preceeding message with 'tool_calls'.",
    "type": "invalid_request_error",
    "param": "messages.[6].role",
    "code": null
  }
}
```