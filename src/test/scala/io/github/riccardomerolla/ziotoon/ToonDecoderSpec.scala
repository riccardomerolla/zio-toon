package io.github.riccardomerolla.ziotoon

import zio.test._
import zio.test.Assertion._
import ToonValue._

object ToonDecoderSpec extends ZIOSpecDefault {
  
  def spec = suite("ToonDecoder")(
    
    suite("Primitives")(
      test("decode string") {
        val input = "hello"
        val result = ToonDecoder.decode(input)
        assertTrue(result == Right(str("hello")))
      },
      
      test("decode quoted string") {
        val input = "\"hello world\""
        val result = ToonDecoder.decode(input)
        assertTrue(result == Right(str("hello world")))
      },
      
      test("decode number") {
        val input = "42"
        val result = ToonDecoder.decode(input)
        assertTrue(result == Right(num(42)))
      },
      
      test("decode boolean") {
        val input = "true"
        val result = ToonDecoder.decode(input)
        assertTrue(result == Right(bool(true)))
      },
      
      test("decode null") {
        val input = "null"
        val result = ToonDecoder.decode(input)
        assertTrue(result == Right(Null))
      }
    ),
    
    suite("Objects")(
      test("decode simple object") {
        val input = """name: Alice
age: 30"""
        val result = ToonDecoder.decode(input)
        val expected = obj(
          "name" -> str("Alice"),
          "age" -> num(30)
        )
        assertTrue(result == Right(expected))
      },
      
      test("decode nested object") {
        val input = """user:
  name: Bob
  email: bob@example.com"""
        val result = ToonDecoder.decode(input)
        val expected = obj(
          "user" -> obj(
            "name" -> str("Bob"),
            "email" -> str("bob@example.com")
          )
        )
        assertTrue(result == Right(expected))
      }
    ),
    
    suite("Arrays")(
      test("decode empty array") {
        val input = "[0]:"
        val result = ToonDecoder.decode(input)
        assertTrue(result == Right(arr()))
      },
      
      test("decode inline primitive array with key") {
        val input = """tags[3]: admin,ops,dev"""
        val result = ToonDecoder.decode(input)
        val expected = obj(
          "tags" -> arr(str("admin"), str("ops"), str("dev"))
        )
        assertTrue(result == Right(expected))
      },
      
      test("decode tabular array") {
        val input = """users[2]{id,name,role}:
  1,Alice,admin
  2,Bob,user"""
        val result = ToonDecoder.decode(input)
        val expected = obj(
          "users" -> arr(
            obj("id" -> num(1), "name" -> str("Alice"), "role" -> str("admin")),
            obj("id" -> num(2), "name" -> str("Bob"), "role" -> str("user"))
          )
        )
        assertTrue(result == Right(expected))
      }
    ),
    
    suite("Round-trip")(
      test("encode then decode simple object") {
        val value = obj(
          "name" -> str("Alice"),
          "age" -> num(30),
          "active" -> bool(true)
        )
        val encoded = ToonEncoder.encode(value)
        val decoded = ToonDecoder.decode(encoded)
        assertTrue(decoded == Right(value))
      },
      
      test("encode then decode tabular array") {
        val value = obj(
          "users" -> arr(
            obj("id" -> num(1), "name" -> str("Alice"), "role" -> str("admin")),
            obj("id" -> num(2), "name" -> str("Bob"), "role" -> str("user"))
          )
        )
        val encoded = ToonEncoder.encode(value)
        val decoded = ToonDecoder.decode(encoded)
        assertTrue(decoded == Right(value))
      }
    )
  )
}
