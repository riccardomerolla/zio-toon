package io.github.riccardomerolla.ziotoon

import zio._
import zio.stream._
import zio.test._
import zio.test.Assertion._

import ToonValue._

/** Tests for ToonStreamService following ZIO best practices.
  */
object ToonStreamServiceSpec extends ZIOSpecDefault {

  // Build the complete layer stack: encoder and decoder as base, then stream service on top
  val layers: ZLayer[Any, Nothing, ToonStreamService & ToonEncoderService & ToonDecoderService] = {
    val base = ToonEncoderService.live ++ ToonDecoderService.live
    base >+> ToonStreamService.live
  }

  def spec: Spec[Any, Any] = suite("ToonStreamService")(
    suite("Encoding Streams")(
      test("encode stream of values") {
        val values = ZStream(
          str("hello"),
          num(42),
          bool(true),
        )

        for {
          encoded <- ToonStreamService.encodeStream(values).runCollect
        } yield assertTrue(
          encoded.length == 3 &&
          encoded(0) == "hello" &&
          encoded(1) == "42" &&
          encoded(2) == "true"
        )
      }.provideLayer(layers),
      test("encode empty stream") {
        val values = ZStream.empty

        for {
          encoded <- ToonStreamService.encodeStream(values).runCollect
        } yield assertTrue(encoded.isEmpty)
      }.provideLayer(layers),
      test("encode stream of objects") {
        val objects = ZStream(
          obj("name" -> str("Alice")),
          obj("name" -> str("Bob")),
          obj("name" -> str("Charlie")),
        )

        for {
          encoded <- ToonStreamService.encodeStream(objects).runCollect
        } yield assertTrue(
          encoded.length == 3 &&
          encoded.forall(_.contains("name:"))
        )
      }.provideLayer(layers),
    ),
    suite("Decoding Streams")(
      test("decode stream of TOON strings") {
        val input = ZStream(
          "name: Alice",
          "age: 30",
          "active: true",
        )

        for {
          decoded <- ToonStreamService.decodeStream(input).runCollect
        } yield assertTrue(
          decoded.length == 3 &&
          decoded(0) == obj("name" -> str("Alice"))
        )
      }.provideLayer(layers),
      test("decode stream handles errors") {
        val input = ZStream(
          "valid: value",
          "key: \"unterminated string", // Invalid: unterminated string
          "name: Bob",
        )

        for {
          result <- ToonStreamService.decodeStream(input).runCollect.exit
        } yield assert(result)(fails(isSubtype[ToonError](anything)))
      }.provideLayer(layers),
      test("decode empty stream") {
        val input = ZStream.empty

        for {
          decoded <- ToonStreamService.decodeStream(input).runCollect
        } yield assertTrue(decoded.isEmpty)
      }.provideLayer(layers),
      test("decode line stream splits on blank lines") {
        val lines = ZStream(
          "name: Alice",
          "",
          "name: Bob",
        )

        for {
          decoded <- ToonStreamService.decodeLineStream(lines).runCollect
        } yield assertTrue(
          decoded.length == 2 &&
          decoded(0) == obj("name" -> str("Alice")) &&
          decoded(1) == obj("name" -> str("Bob"))
        )
      }.provideLayer(layers),
    ),
    suite("Round-trip Streams")(
      test("round-trip stream preserves values") {
        val original = ZStream(
          obj("id" -> num(1), "name" -> str("Alice")),
          obj("id" -> num(2), "name" -> str("Bob")),
          obj("id" -> num(3), "name" -> str("Charlie")),
        )

        for {
          roundTripped      <- ToonStreamService.roundTripStream(original).runCollect
          originalCollected <- original.runCollect
        } yield assertTrue(roundTripped == originalCollected)
      }.provideLayer(layers),
      test("round-trip with primitives") {
        val primitives = ZStream(
          str("hello"),
          num(42),
          bool(true),
          Null,
        )

        for {
          roundTripped <- ToonStreamService.roundTripStream(primitives).runCollect
          original     <- primitives.runCollect
        } yield assertTrue(roundTripped == original)
      }.provideLayer(layers),
    ),
    suite("Array Encoding")(
      test("encode array stream") {
        val elements = ZStream(
          str("a"),
          str("b"),
          str("c"),
        )

        for {
          encoded <- ToonStreamService.encodeArrayStream(elements, None).runCollect
          decoded <- ToonDecoderService.decode(encoded.mkString)
        } yield assertTrue(
          decoded == arr(str("a"), str("b"), str("c"))
        )
      }.provideLayer(layers),
      test("encode keyed array stream") {
        val elements = ZStream(
          str("x"),
          str("y"),
          str("z"),
        )

        for {
          encoded <- ToonStreamService.encodeArrayStream(elements, Some("items")).runCollect
          decoded <- ToonDecoderService.decode(encoded.mkString)
        } yield assertTrue(
          decoded == obj("items" -> arr(str("x"), str("y"), str("z")))
        )
      }.provideLayer(layers),
      test("array stream emits unknown length header") {
        val elements = ZStream(num(1), num(2))

        for {
          encoded <- ToonStreamService.encodeArrayStream(elements, Some("items")).runCollect
        } yield assertTrue(encoded.mkString.contains("[?"))
      }.provideLayer(layers),
    ),
    suite("Performance")(
      test("handle large stream efficiently") {
        val largeStream = ZStream.fromIterable(1 to 1000).map(i => obj("id" -> num(i), "value" -> str(s"item-$i")))

        for {
          encoded <- ToonStreamService.encodeStream(largeStream).runCollect
        } yield assertTrue(encoded.length == 1000)
      }.provideLayer(layers)
    ),
  )
}
