package io.github.riccardomerolla.ziotoon

import zio.test._
import zio.test.Assertion._

import ToonValue._

object ToonEncoderSpec extends ZIOSpecDefault {

  def spec = suite("ToonEncoder")(
    suite("Primitives")(
      test("encode string") {
        val value   = str("hello")
        val encoded = ToonEncoder.encode(value)
        assertTrue(encoded == "hello")
      },
      test("encode string with internal space") {
        val value   = str("hello world")
        val encoded = ToonEncoder.encode(value)
        assertTrue(encoded == "hello world")
      },
      test("encode quoted string with leading space") {
        val value   = str(" hello")
        val encoded = ToonEncoder.encode(value)
        assertTrue(encoded == "\" hello\"")
      },
      test("encode string that looks like keyword") {
        val value   = str("true")
        val encoded = ToonEncoder.encode(value)
        assertTrue(encoded == "\"true\"")
      },
      test("encode number") {
        val value   = num(42)
        val encoded = ToonEncoder.encode(value)
        assertTrue(encoded == "42")
      },
      test("encode decimal") {
        val value   = num(3.14)
        val encoded = ToonEncoder.encode(value)
        assertTrue(encoded.startsWith("3.14"))
      },
      test("encode boolean true") {
        val value   = bool(true)
        val encoded = ToonEncoder.encode(value)
        assertTrue(encoded == "true")
      },
      test("encode null") {
        val encoded = ToonEncoder.encode(Null)
        assertTrue(encoded == "null")
      },
    ),
    suite("Objects")(
      test("encode simple object") {
        val value    = obj(
          "name" -> str("Alice"),
          "age"  -> num(30),
        )
        val encoded  = ToonEncoder.encode(value)
        val expected = """name: Alice
age: 30"""
        assertTrue(encoded == expected)
      },
      test("encode nested object") {
        val value    = obj(
          "user" -> obj(
            "name"  -> str("Bob"),
            "email" -> str("bob@example.com"),
          )
        )
        val encoded  = ToonEncoder.encode(value)
        val expected = """user:
  name: Bob
  email: bob@example.com"""
        assertTrue(encoded == expected)
      },
    ),
    suite("Arrays")(
      test("encode empty array") {
        val value   = arr()
        val encoded = ToonEncoder.encode(value)
        assertTrue(encoded == "[0]:")
      },
      test("encode inline primitive array") {
        val value    = obj(
          "tags" -> arr(str("admin"), str("ops"), str("dev"))
        )
        val encoded  = ToonEncoder.encode(value)
        val expected = "tags[3]: admin,ops,dev"
        assertTrue(encoded == expected)
      },
      test("encode tabular array") {
        val value    = obj(
          "users" -> arr(
            obj("id" -> num(1), "name" -> str("Alice"), "role" -> str("admin")),
            obj("id" -> num(2), "name" -> str("Bob"), "role"   -> str("user")),
          )
        )
        val encoded  = ToonEncoder.encode(value)
        val expected = """users[2]{id,name,role}:
  1,Alice,admin
  2,Bob,user"""
        assertTrue(encoded == expected)
      },
      test("encode tabular array with booleans") {
        val value    = obj(
          "users" -> arr(
            obj("id" -> num(1), "name" -> str("Alice"), "role"   -> str("admin"), "active"     -> bool(true)),
            obj("id" -> num(2), "name" -> str("Bob"), "role"     -> str("developer"), "active" -> bool(true)),
            obj("id" -> num(3), "name" -> str("Charlie"), "role" -> str("designer"), "active"  -> bool(false)),
          )
        )
        val encoded  = ToonEncoder.encode(value)
        val expected = """users[3]{id,name,role,active}:
  1,Alice,admin,true
  2,Bob,developer,true
  3,Charlie,designer,false"""
        assertTrue(encoded == expected)
      },
    ),
  )
}
