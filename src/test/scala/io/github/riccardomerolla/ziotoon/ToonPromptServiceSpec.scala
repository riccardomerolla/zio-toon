package io.github.riccardomerolla.ziotoon

import zio._
import zio.test._
import zio.test.Assertion._

/** Specs for ToonPromptService covering fenced and inline JSON replacements.
  */
object ToonPromptServiceSpec extends ZIOSpecDefault {

  private val layer =
    (ToonEncoderService.live ++ ToonJsonService.live) >>>
      ToonPromptService.live

  def spec: Spec[TestEnvironment, Any] = suite("ToonPromptService")(
    test("converts JSON code fence to TOON fence") {
      val prompt =
        """Explain this payload:
          |```json
          |{"name":"Alice","age":30}
          |```
          |Thanks!
          |""".stripMargin

      ToonPromptService
        .rewritePrompt(prompt)
        .map { result =>
          assertTrue(result.contains("```toon")) &&
          assertTrue(result.contains("name: Alice")) &&
          assertTrue(result.contains("age: 30")) &&
          assertTrue(!result.contains("```json"))
        }
    }.provideLayer(layer),
    test("converts inline JSON object outside fences") {
      val prompt = """Please review {"user":"Alice","active":true} before proceeding."""

      ToonPromptService
        .rewritePrompt(prompt)
        .map { result =>
          assertTrue(result.contains("user: Alice")) &&
          assertTrue(result.contains("active: true")) &&
          assertTrue(!result.contains("{\"user\""))
        }
    }.provideLayer(layer),
    test("skips inline JSON inside non-json code fence") {
      val prompt =
        """Check this block:
          |```scala
          |val json = {"skip":"me"}
          |```
          |But convert this too {"process":"me"}
          |""".stripMargin

      ToonPromptService
        .rewritePrompt(prompt)
        .map { result =>
          assertTrue(result.contains("""{"skip":"me"}""")) &&
          assertTrue(result.contains("process: me"))
        }
    }.provideLayer(layer),
    test("handles multiple JSON segments") {
      val prompt =
        """First: {"id":1}
          |```json
          |{"values":[1,2,3]}
          |```
          |Second: {"id":2}
          |""".stripMargin

      ToonPromptService
        .rewritePrompt(prompt)
        .map { result =>
          assertTrue(!result.contains("""{"id":1}""")) &&
          assertTrue(!result.contains("""{"id":2}""")) &&
          assertTrue(result.contains("```toon")) &&
          assertTrue(result.contains("id: 1")) &&
          assertTrue(result.contains("id: 2")) &&
          assertTrue(result.contains("[3]: 1,2,3"))
        }
    }.provideLayer(layer),
    test("ignores invalid inline JSON but converts valid ones") {
      val prompt = """Ignore this {invalid json but keep} yet convert {"ok":false}."""

      ToonPromptService
        .rewritePrompt(prompt)
        .map { result =>
          assertTrue(result.contains("{invalid json but keep}")) &&
          assertTrue(result.contains("ok: false"))
        }
    }.provideLayer(layer),
    test("rewrites complex prompt with mixed sections") {
      val prompt =
        """System:
          |- obey policies
          |
          |Examples:
          |```json
          |{"input":{"text":"hello"},"expected":{"answer":"world"}}
          |```
          |
          |User provided context { "session": { "id": 42, "tags": ["alpha","beta"] } }
          |
          |```python
          |payload = {"ignore":"this"}
          |```
          |
          |Before responding confirm {"confirmation":true}
          |""".stripMargin

      ToonPromptService
        .rewritePrompt(prompt)
        .map { result =>
          assertTrue(result.contains("```toon")) &&
          assertTrue(result.contains("input:")) &&
          assertTrue(result.contains("expected:")) &&
          assertTrue(result.contains("session:")) &&
          assertTrue(result.contains("id: 42")) &&
          assertTrue(result.contains("[2]: alpha,beta")) &&
          assertTrue(result.contains("confirmation: true")) &&
          assertTrue(result.contains("```python")) && // untouched fence
          assertTrue(result.contains("""payload = {"ignore":"this"}"""))
        }
    }.provideLayer(layer),
    test("fails when fenced JSON is invalid") {
      val prompt =
        """```json
          |{invalid json}
          |```
          |""".stripMargin

      ToonPromptService
        .rewritePrompt(prompt)
        .exit
        .map(result => assert(result)(fails(isSubtype[ToonPromptService.PromptError](anything))))
    }.provideLayer(layer),
  )
}
