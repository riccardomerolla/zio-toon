package io.github.riccardomerolla.toon4s

import zio._
import zio.test._
import zio.test.Assertion._
import ToonValue._

object ToonZIOSpec extends ZIOSpecDefault {
  
  def spec = suite("TOON ZIO Integration")(
    
    suite("ToonEncoderZ")(
      test("encode with ZIO effect") {
        val value = obj("name" -> str("Alice"), "age" -> num(30))
        for {
          encoded <- ToonEncoderZ.encode(value)
        } yield assertTrue(encoded == "name: Alice\nage: 30")
      },
      
      test("make encoder") {
        for {
          encoder <- ToonEncoderZ.make()
          encoded = encoder.encode(obj("test" -> str("value")))
        } yield assertTrue(encoded == "test: value")
      }
    ),
    
    suite("ToonDecoderZ")(
      test("decode with ZIO effect") {
        val input = "name: Alice\nage: 30"
        val expected = obj("name" -> str("Alice"), "age" -> num(30))
        for {
          decoded <- ToonDecoderZ.decode(input)
        } yield assertTrue(decoded == expected)
      },
      
      test("decode error handling") {
        val input = "key1: value1\nkey2"  // Missing colon in strict mode
        val config = DecoderConfig(strictMode = true)
        for {
          result <- ToonDecoderZ.decode(input, config).either
        } yield assertTrue(result.isLeft)
      },
      
      test("make decoder") {
        val input = "test: value"
        for {
          decoder <- ToonDecoderZ.make()
          result = decoder.decode(input)
        } yield assertTrue(result == Right(obj("test" -> str("value"))))
      }
    ),
    
    suite("Toon convenience API")(
      test("encode convenience method") {
        val value = obj("key" -> str("value"))
        val encoded = Toon.encode(value)
        assertTrue(encoded == "key: value")
      },
      
      test("decode convenience method") {
        val input = "key: value"
        val result = Toon.decode(input)
        assertTrue(result == Right(obj("key" -> str("value"))))
      },
      
      test("encodeZ convenience method") {
        val value = obj("key" -> str("value"))
        for {
          encoded <- Toon.encodeZ(value)
        } yield assertTrue(encoded == "key: value")
      },
      
      test("decodeZ convenience method") {
        val input = "key: value"
        for {
          decoded <- Toon.decodeZ(input)
        } yield assertTrue(decoded == obj("key" -> str("value")))
      },
      
      test("roundTrip convenience method") {
        val original = obj(
          "name" -> str("Alice"),
          "age" -> num(30),
          "active" -> bool(true)
        )
        val result = Toon.roundTrip(original)
        assertTrue(result == Right(original))
      },
      
      test("roundTripZ convenience method") {
        val original = obj(
          "name" -> str("Bob"),
          "tags" -> arr(str("admin"), str("ops"))
        )
        for {
          roundTripped <- Toon.roundTripZ(original)
        } yield assertTrue(roundTripped == original)
      }
    ),
    
    suite("Round-trip with tabular arrays")(
      test("tabular array round-trip") {
        val original = obj(
          "users" -> arr(
            obj("id" -> num(1), "name" -> str("Alice"), "role" -> str("admin")),
            obj("id" -> num(2), "name" -> str("Bob"), "role" -> str("user"))
          )
        )
        for {
          roundTripped <- Toon.roundTripZ(original)
        } yield assertTrue(roundTripped == original)
      }
    )
  )
}
