package io.github.riccardomerolla.ziotoon

import zio.test._
import zio.test.Assertion._

import ToonError._
import ToonValue._

object ToonDecoderSpec extends ZIOSpecDefault {

  def spec = suite("ToonDecoder")(
    suite("Primitives")(
      test("decode string") {
        val input  = "hello"
        val result = ToonDecoder.decode(input)
        assertTrue(result == Right(str("hello")))
      },
      test("decode quoted string") {
        val input  = "\"hello world\""
        val result = ToonDecoder.decode(input)
        assertTrue(result == Right(str("hello world")))
      },
      test("decode number") {
        val input  = "42"
        val result = ToonDecoder.decode(input)
        assertTrue(result == Right(num(42)))
      },
      test("decode boolean") {
        val input  = "true"
        val result = ToonDecoder.decode(input)
        assertTrue(result == Right(bool(true)))
      },
      test("decode null") {
        val input  = "null"
        val result = ToonDecoder.decode(input)
        assertTrue(result == Right(Null))
      },
      test("fail on invalid number literal") {
        val input  = "1e99999"
        val result = ToonDecoder.decode(input)
        assertTrue(result == Left(InvalidNumber("1e99999", 1)))
      },
    ),
    suite("Objects")(
      test("decode simple object") {
        val input    = """name: Alice
age: 30"""
        val result   = ToonDecoder.decode(input)
        val expected = obj(
          "name" -> str("Alice"),
          "age"  -> num(30),
        )
        assertTrue(result == Right(expected))
      },
      test("decode nested object") {
        val input    = """user:
  name: Bob
  email: bob@example.com"""
        val result   = ToonDecoder.decode(input)
        val expected = obj(
          "user" -> obj(
            "name"  -> str("Bob"),
            "email" -> str("bob@example.com"),
          )
        )
        assertTrue(result == Right(expected))
      },
    ),
    suite("Arrays")(
      test("decode empty array") {
        val input  = "[0]:"
        val result = ToonDecoder.decode(input)
        assertTrue(result == Right(arr()))
      },
      test("decode array with unknown length header") {
        val input  =
          s"""[?]:
             |  - 1
             |  - 2
             |""".stripMargin
        val result = ToonDecoder.decode(input)
        assertTrue(result == Right(arr(num(1), num(2))))
      },
      test("decode empty array with unknown length header") {
        val input  =
          s"""[?]:
             |""".stripMargin
        val result = ToonDecoder.decode(input)
        assertTrue(result == Right(arr()))
      },
      test("decode inline primitive array with key") {
        val input    = """tags[3]: admin,ops,dev"""
        val result   = ToonDecoder.decode(input)
        val expected = obj(
          "tags" -> arr(str("admin"), str("ops"), str("dev"))
        )
        assertTrue(result == Right(expected))
      },
      test("decode tabular array") {
        val input    = """users[2]{id,name,role}:
  1,Alice,admin
  2,Bob,user"""
        val result   = ToonDecoder.decode(input)
        val expected = obj(
          "users" -> arr(
            obj("id" -> num(1), "name" -> str("Alice"), "role" -> str("admin")),
            obj("id" -> num(2), "name" -> str("Bob"), "role"   -> str("user")),
          )
        )
        assertTrue(result == Right(expected))
      },
    ),
    suite("Guard rails")(
      test("fail when depth exceeds configured limit") {
        val input  =
          """root:
  child:
    leaf: 1"""
        val config = DecoderConfig(maxDepth = Some(1))
        val result = ToonDecoder.decode(input, config)
        assertTrue(result == Left(DepthLimitExceeded(1, 3)))
      },
      test("fail when array length exceeds configured limit") {
        val input  = "values[?]: 1,2,3"
        val config = DecoderConfig(maxArrayLength = Some(2))
        val result = ToonDecoder.decode(input, config)
        assertTrue(result == Left(ArrayLengthLimitExceeded(2, 3, "inline array", 1)))
      },
      test("fail when string exceeds configured length") {
        val input  = "id: abcd"
        val config = DecoderConfig(maxStringLength = Some(3))
        val result = ToonDecoder.decode(input, config)
        assertTrue(result == Left(StringTooLong(3, 4, 1)))
      },
    ),
    suite("Round-trip")(
      test("encode then decode simple object") {
        val value   = obj(
          "name"   -> str("Alice"),
          "age"    -> num(30),
          "active" -> bool(true),
        )
        val encoded = ToonEncoder.encode(value)
        val decoded = ToonDecoder.decode(encoded)
        assertTrue(decoded == Right(value))
      },
      test("encode then decode tabular array") {
        val value   = obj(
          "users" -> arr(
            obj("id" -> num(1), "name" -> str("Alice"), "role" -> str("admin")),
            obj("id" -> num(2), "name" -> str("Bob"), "role"   -> str("user")),
          )
        )
        val encoded = ToonEncoder.encode(value)
        val decoded = ToonDecoder.decode(encoded)
        assertTrue(decoded == Right(value))
      },
    ),
  )
}
