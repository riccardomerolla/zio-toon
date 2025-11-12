package io.github.riccardomerolla.ziotoon

import zio._
import zio.json._
import zio.json.ast.Json

/** Result of token savings calculation between JSON and TOON formats.
  *
  * This is a pure data type representing comparison statistics.
  *
  * @param jsonSize
  *   The size of the JSON representation in characters
  * @param toonSize
  *   The size of the TOON representation in characters
  * @param savings
  *   The absolute difference in characters (jsonSize - toonSize)
  * @param savingsPercent
  *   The percentage of tokens saved (0-100)
  */
final case class TokenSavings(
    jsonSize: Int,
    toonSize: Int,
    savings: Int,
    savingsPercent: Double,
  ) {
  override def toString: String =
    s"JSON: $jsonSize chars, TOON: $toonSize chars, Savings: $savings chars (${savingsPercent}%)"
}

/** Service for JSON-TOON integration.
  *
  * This service provides functionality to:
  *   - Encode ToonValue to JSON string
  *   - Decode JSON string to ToonValue
  *   - Calculate token savings between JSON and TOON formats
  *
  * Following ZIO best practices:
  *   - Effects are pure blueprints
  *   - Errors are typed in error channel
  *   - Service is provided via ZLayer
  *   - Effects compose naturally
  *
  * ==Usage==
  *
  * {{{
  * val program = for {
  *   toonValue <- ToonJsonService.fromJson(jsonString)
  *   encoded <- ToonEncoderService.encode(toonValue)
  *   savings <- ToonJsonService.calculateSavings(toonValue)
  *   _ <- Console.printLine(s"Savings: $savings")
  * } yield encoded
  *
  * program.provide(
  *   ToonJsonService.live,
  *   ToonEncoderService.live
  * )
  * }}}
  *
  * ==Error Handling==
  *
  * JSON decoding errors are typed as String in the error channel:
  *
  * {{{
  * ToonJsonService.fromJson(invalidJson).catchAll { error =>
  *   ZIO.logWarning(s"JSON parse failed: $error") *>
  *   ZIO.succeed(ToonValue.Null)
  * }
  * }}}
  */
trait ToonJsonService {

  /** Encode a ToonValue to a JSON string.
    *
    * @param value
    *   The ToonValue to encode
    * @return
    *   A ZIO effect that produces the JSON string
    */
  def toJson(value: ToonValue): UIO[String]

  /** Decode a JSON string to a ToonValue.
    *
    * @param json
    *   The JSON string to decode
    * @return
    *   A ZIO effect that either fails with a string error or succeeds with ToonValue
    */
  def fromJson(json: String): IO[String, ToonValue]

  /** Calculate token savings between JSON and TOON formats.
    *
    * This method encodes the value to both pretty-printed JSON and TOON formats, then calculates the difference in
    * character count and percentage savings.
    *
    * @param value
    *   The ToonValue to analyze
    * @return
    *   A ZIO effect that produces TokenSavings statistics
    */
  def calculateSavings(value: ToonValue): URIO[ToonEncoderService, TokenSavings]

  /** Encode a ToonValue to pretty-printed JSON string with configurable indentation.
    *
    * @param value
    *   The ToonValue to encode
    * @param indent
    *   The number of spaces for indentation (default: 2)
    * @return
    *   A ZIO effect that produces the pretty-printed JSON string
    */
  def toPrettyJson(value: ToonValue, indent: Int = 2): UIO[String]
}

object ToonJsonService {

  /** Live implementation of ToonJsonService.
    */
  final case class Live() extends ToonJsonService {

    def toJson(value: ToonValue): UIO[String] =
      ZIO.succeed(toonValueToJsonAst(value).toJson)

    def fromJson(json: String): IO[String, ToonValue] =
      ZIO.fromEither(json.fromJson[Json].map(jsonAstToToonValue))

    def calculateSavings(value: ToonValue): URIO[ToonEncoderService, TokenSavings] =
      for {
        jsonStr       <- toPrettyJson(value)
        toonStr       <- ToonEncoderService.encode(value)
        jsonSize       = jsonStr.length
        toonSize       = toonStr.length
        savings        = jsonSize - toonSize
        savingsPercent = if (jsonSize > 0) savings.toDouble / jsonSize * 100 else 0.0
      } yield TokenSavings(jsonSize, toonSize, savings, savingsPercent)

    def toPrettyJson(value: ToonValue, indent: Int = 2): UIO[String] =
      ZIO.succeed(toonValueToJsonAst(value).toJsonPretty)

    /** Convert ToonValue to zio-json AST.
      */
    private def toonValueToJsonAst(value: ToonValue): Json = value match {
      case ToonValue.Str(s)        => Json.Str(s)
      case ToonValue.Num(n)        =>
        // Handle integers vs decimals
        if (n.isWhole) Json.Num(n.toLong)
        else Json.Num(n)
      case ToonValue.Bool(b)       => Json.Bool(b)
      case ToonValue.Null          => Json.Null
      case ToonValue.Obj(fields)   =>
        Json.Obj(fields.map { case (k, v) => k -> toonValueToJsonAst(v) }*)
      case ToonValue.Arr(elements) =>
        Json.Arr(elements.map(toonValueToJsonAst)*)
    }

    /** Convert zio-json AST to ToonValue.
      */
    private def jsonAstToToonValue(json: Json): ToonValue = json match {
      case Json.Str(s)        => ToonValue.Str(s)
      case Json.Num(n)        => ToonValue.Num(n.doubleValue)
      case Json.Bool(b)       => ToonValue.Bool(b)
      case Json.Null          => ToonValue.Null
      case Json.Obj(fields)   =>
        ToonValue.Obj(Chunk.fromIterable(fields.toSeq.map { case (k, v) => k -> jsonAstToToonValue(v) }))
      case Json.Arr(elements) =>
        ToonValue.Arr(Chunk.fromIterable(elements.map(jsonAstToToonValue)))
    }
  }

  /** ZLayer that provides a ToonJsonService.
    */
  val live: ULayer[ToonJsonService] =
    ZLayer.succeed(Live())

  /** Accessor method to convert ToonValue to JSON using the service from environment.
    */
  def toJson(value: ToonValue): ZIO[ToonJsonService, Nothing, String] =
    ZIO.serviceWithZIO[ToonJsonService](_.toJson(value))

  /** Accessor method to convert JSON to ToonValue using the service from environment.
    */
  def fromJson(json: String): ZIO[ToonJsonService, String, ToonValue] =
    ZIO.serviceWithZIO[ToonJsonService](_.fromJson(json))

  /** Accessor method to calculate token savings using the service from environment.
    */
  def calculateSavings(value: ToonValue): ZIO[ToonJsonService & ToonEncoderService, Nothing, TokenSavings] =
    ZIO.serviceWithZIO[ToonJsonService](_.calculateSavings(value))

  /** Accessor method to convert ToonValue to pretty-printed JSON using the service from environment.
    */
  def toPrettyJson(value: ToonValue, indent: Int = 2): ZIO[ToonJsonService, Nothing, String] =
    ZIO.serviceWithZIO[ToonJsonService](_.toPrettyJson(value, indent))
}
