package io.github.riccardomerolla.ziotoon

import zio._
import zio.test._
import zio.test.Assertion._
import ToonValue._

/**
 * Tests for ToonJsonService following ZIO best practices.
 */
object ToonJsonServiceSpec extends ZIOSpecDefault {
  
  def spec = suite("ToonJsonService")(
    
    suite("JSON conversion")(
      test("convert simple ToonValue to JSON") {
        val value = obj("name" -> str("Alice"), "age" -> num(30))
        for {
          json <- ToonJsonService.toJson(value)
        } yield assertTrue(json.contains("\"name\"") && json.contains("\"Alice\"") && json.contains("30"))
      }.provideLayer(ToonJsonService.live),
      
      test("convert JSON to ToonValue") {
        val json = """{"name":"Bob","age":25}"""
        for {
          value <- ToonJsonService.fromJson(json)
        } yield assertTrue(value == obj("name" -> str("Bob"), "age" -> num(25)))
      }.provideLayer(ToonJsonService.live),
      
      test("round-trip JSON conversion") {
        val original = obj(
          "name" -> str("Charlie"),
          "age" -> num(35),
          "active" -> bool(true)
        )
        for {
          json <- ToonJsonService.toJson(original)
          recovered <- ToonJsonService.fromJson(json)
        } yield assertTrue(recovered == original)
      }.provideLayer(ToonJsonService.live),
      
      test("convert array to JSON") {
        val value = obj("tags" -> arr(str("admin"), str("user")))
        for {
          json <- ToonJsonService.toJson(value)
        } yield assertTrue(json.contains("\"tags\"") && json.contains("["))
      }.provideLayer(ToonJsonService.live),
      
      test("convert nested objects to JSON") {
        val value = obj(
          "user" -> obj(
            "name" -> str("Dave"),
            "profile" -> obj("bio" -> str("Developer"))
          )
        )
        for {
          json <- ToonJsonService.toJson(value)
        } yield assertTrue(json.contains("\"user\"") && json.contains("\"profile\""))
      }.provideLayer(ToonJsonService.live)
    ),
    
    suite("Pretty JSON")(
      test("convert ToonValue to pretty JSON") {
        val value = obj("name" -> str("Eve"))
        for {
          json <- ToonJsonService.toPrettyJson(value)
        } yield assertTrue(json.contains("\n"))
      }.provideLayer(ToonJsonService.live)
    ),
    
    suite("Token savings calculation")(
      test("calculate savings for simple object") {
        val value = obj("name" -> str("Alice"), "age" -> num(30))
        for {
          savings <- ToonJsonService.calculateSavings(value)
        } yield assertTrue(savings.jsonSize > 0 && savings.toonSize > 0)
      }.provideLayer(ToonJsonService.live ++ ToonEncoderService.live),
      
      test("calculate savings for tabular data") {
        val value = obj(
          "users" -> arr(
            obj("id" -> num(1), "name" -> str("Alice"), "role" -> str("admin")),
            obj("id" -> num(2), "name" -> str("Bob"), "role" -> str("user")),
            obj("id" -> num(3), "name" -> str("Charlie"), "role" -> str("developer"))
          )
        )
        for {
          savings <- ToonJsonService.calculateSavings(value)
        } yield assertTrue(
          savings.savings > 0 &&
          savings.savingsPercent > 0 &&
          savings.jsonSize > savings.toonSize
        )
      }.provideLayer(ToonJsonService.live ++ ToonEncoderService.live),
      
      test("savings calculation returns correct structure") {
        val value = obj("test" -> str("value"))
        for {
          savings <- ToonJsonService.calculateSavings(value)
        } yield assertTrue(
          savings.jsonSize >= 0 &&
          savings.toonSize >= 0 &&
          savings.savings == (savings.jsonSize - savings.toonSize)
        )
      }.provideLayer(ToonJsonService.live ++ ToonEncoderService.live)
    ),
    
    suite("Error handling")(
      test("handle invalid JSON") {
        val invalidJson = """{"name": "incomplete"""
        for {
          result <- ToonJsonService.fromJson(invalidJson).either
        } yield assertTrue(result.isLeft)
      }.provideLayer(ToonJsonService.live)
    )
  )
}
