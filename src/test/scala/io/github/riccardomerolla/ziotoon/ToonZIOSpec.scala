package io.github.riccardomerolla.ziotoon

import zio._
import zio.test._
import zio.test.Assertion._

import ToonValue._

/** Tests for ZIO integration following ZIO best practices.
  *
  * Key principles demonstrated:
  *   - Services provided via ZLayer at test suite level
  *   - Effects composed through for-comprehension
  *   - Typed errors handled in error channel
  *   - Tests are deterministic and repeatable
  */
object ToonZIOSpec extends ZIOSpecDefault {

  def spec = suite("TOON ZIO Integration")(
    suite("ToonEncoderService")(
      test("encode with service from environment") {
        val value = obj("name" -> str("Alice"), "age" -> num(30))
        for {
          encoded <- ToonEncoderService.encode(value)
        } yield assertTrue(encoded == "name: Alice\nage: 30")
      }.provideLayer(ToonEncoderService.live),
      test("encode with custom configuration") {
        val value = obj("data" -> arr(str("a"), str("b")))
        for {
          encoded <- ToonEncoderService.encode(value)
        } yield assertTrue(encoded.contains("\t"))
      }.provideLayer(ToonEncoderService.configured(EncoderConfig(delimiter = Delimiter.Tab))),
    ),
    suite("ToonDecoderService")(
      test("decode with service from environment") {
        val input    = "name: Alice\nage: 30"
        val expected = obj("name" -> str("Alice"), "age" -> num(30))
        for {
          decoded <- ToonDecoderService.decode(input)
        } yield assertTrue(decoded == expected)
      }.provideLayer(ToonDecoderService.live),
      test("decode error handling with typed errors") {
        val input = "key1: value1\nkey2" // Missing colon in strict mode
        for {
          result <- ToonDecoderService.decode(input).exit
        } yield assert(result)(fails(isSubtype[ToonError.MissingColon](anything)))
      }.provideLayer(ToonDecoderService.live),
      test("decode with custom configuration") {
        val input = "test: value"
        for {
          decoded <- ToonDecoderService.decode(input)
        } yield assertTrue(decoded == obj("test" -> str("value")))
      }.provideLayer(ToonDecoderService.configured(DecoderConfig(strictMode = false))),
    ),
    suite("Toon API with services")(
      test("encode with service from environment") {
        val value = obj("key" -> str("value"))
        for {
          encoded <- Toon.encode(value)
        } yield assertTrue(encoded == "key: value")
      }.provideLayer(ToonEncoderService.live),
      test("decode with service from environment") {
        val input = "key: value"
        for {
          decoded <- Toon.decode(input)
        } yield assertTrue(decoded == obj("key" -> str("value")))
      }.provideLayer(ToonDecoderService.live),
      test("roundTrip with composed services") {
        val original = obj(
          "name"   -> str("Alice"),
          "age"    -> num(30),
          "active" -> bool(true),
        )
        for {
          roundTripped <- Toon.roundTrip(original)
        } yield assertTrue(roundTripped == original)
      }.provideLayer(Toon.live),
      test("roundTrip with custom configuration") {
        val original = obj(
          "name" -> str("Bob"),
          "tags" -> arr(str("admin"), str("ops")),
        )
        for {
          roundTripped <- Toon.roundTrip(original)
        } yield assertTrue(roundTripped == original)
      }.provideLayer(
        Toon.configured(
          encoderConfig = EncoderConfig(indentSize = 4),
          decoderConfig = DecoderConfig(strictMode = true),
        )
      ),
    ),
    suite("Round-trip with tabular arrays")(
      test("tabular array round-trip using services") {
        val original = obj(
          "users" -> arr(
            obj("id" -> num(1), "name" -> str("Alice"), "role" -> str("admin")),
            obj("id" -> num(2), "name" -> str("Bob"), "role"   -> str("user")),
          )
        )
        for {
          roundTripped <- Toon.roundTrip(original)
        } yield assertTrue(roundTripped == original)
      }.provideLayer(Toon.live)
    ),
    suite("Error handling with typed errors")(
      test("handle specific error types") {
        val invalidInput = "key1: value1\nkey2" // Missing colon
        for {
          result <- Toon.decode(invalidInput).catchSome {
                      case ToonError.MissingColon(line) =>
                        ZIO.succeed(obj("error" -> str(s"Missing colon at line $line")))
                    }
        } yield result match {
          case Obj(fields) => assertTrue(fields.nonEmpty)
          case _           => assertTrue(false)
        }
      }.provideLayer(ToonDecoderService.live),
      test("error propagation in effect channel") {
        val invalidInput = "key1: value1\nkey2"
        for {
          exit <- Toon.decode(invalidInput).exit
        } yield assert(exit)(fails(isSubtype[ToonError](anything)))
      }.provideLayer(ToonDecoderService.live),
    ),
  )
}
