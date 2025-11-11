package io.github.riccardomerolla.ziotoon

import zio._
import zio.schema._
import zio.schema.codec.JsonCodec
import zio.json.ast.Json

/**
 * Service for zio-schema integration with TOON.
 * 
 * This service provides functionality to:
 * - Encode values from schemas to TOON format
 * - Decode TOON format to schema-based types
 * - Automatic derivation from case classes
 * 
 * Uses zio-schema-json as a bridge for conversion.
 * Follows ZIO best practices with typed errors and effect composition.
 */
trait ToonSchemaService {
  
  /**
   * Encode a value to TOON format using its schema.
   * 
   * @param value The value to encode
   * @param schema The schema for the value
   * @return A ZIO effect that produces the TOON string
   */
  def encode[A](value: A)(using schema: Schema[A]): IO[String, String]
  
  /**
   * Decode a TOON string to a value using its schema.
   * 
   * @param toonString The TOON format string to decode
   * @param schema The schema for the target type
   * @return A ZIO effect that either fails with an error or succeeds with the value
   */
  def decode[A](toonString: String)(using schema: Schema[A]): IO[String, A]
  
  /**
   * Encode a value to TOON format using its schema with custom encoder configuration.
   * 
   * @param value The value to encode
   * @param schema The schema for the value
   * @param config The encoder configuration
   * @return A ZIO effect that produces the TOON string
   */
  def encodeWithConfig[A](value: A, config: EncoderConfig)(using schema: Schema[A]): IO[String, String]
  
  /**
   * Decode a TOON string to a value using its schema with custom decoder configuration.
   * 
   * @param toonString The TOON format string to decode
   * @param schema The schema for the target type
   * @param config The decoder configuration
   * @return A ZIO effect that either fails with an error or succeeds with the value
   */
  def decodeWithConfig[A](toonString: String, config: DecoderConfig)(using schema: Schema[A]): IO[String, A]
}

object ToonSchemaService {
  
  /**
   * Live implementation of ToonSchemaService.
   */
  final case class Live(
    encoderConfig: EncoderConfig,
    decoderConfig: DecoderConfig,
    jsonService: ToonJsonService
  ) extends ToonSchemaService {
    
    private val encoder = new ToonEncoder(encoderConfig)
    private val decoder = new ToonDecoder(decoderConfig)
    
    def encode[A](value: A)(using schema: Schema[A]): IO[String, String] =
      for {
        // Convert value to JSON using zio-schema-json
        jsonString <- ZIO.attempt(JsonCodec.jsonEncoder(schema).encodeJson(value, None).toString).mapError(_.getMessage)
        // Convert JSON to ToonValue
        toonValue <- jsonService.fromJson(jsonString)
        // Encode ToonValue to TOON string
        toonString <- ZIO.succeed(encoder.encode(toonValue))
      } yield toonString
    
    def decode[A](toonString: String)(using schema: Schema[A]): IO[String, A] =
      for {
        // Decode TOON string to ToonValue
        toonValue <- ZIO.fromEither(decoder.decode(toonString).left.map(_.message))
        // Convert ToonValue to JSON
        jsonString <- jsonService.toJson(toonValue)
        // Convert JSON to value using zio-schema-json
        decodeResult <- ZIO.attempt(JsonCodec.jsonDecoder(schema).decodeJson(jsonString)).mapError(_.getMessage)
        result <- ZIO.fromEither(decodeResult.left.map(identity))
      } yield result
    
    def encodeWithConfig[A](value: A, config: EncoderConfig)(using schema: Schema[A]): IO[String, String] =
      for {
        // Convert value to JSON using zio-schema-json
        jsonString <- ZIO.attempt(JsonCodec.jsonEncoder(schema).encodeJson(value, None).toString).mapError(_.getMessage)
        // Convert JSON to ToonValue
        toonValue <- jsonService.fromJson(jsonString)
        // Encode ToonValue to TOON string with custom config
        toonString <- ZIO.succeed(new ToonEncoder(config).encode(toonValue))
      } yield toonString
    
    def decodeWithConfig[A](toonString: String, config: DecoderConfig)(using schema: Schema[A]): IO[String, A] =
      for {
        // Decode TOON string to ToonValue with custom config
        toonValue <- ZIO.fromEither(new ToonDecoder(config).decode(toonString).left.map(_.message))
        // Convert ToonValue to JSON
        jsonString <- jsonService.toJson(toonValue)
        // Convert JSON to value using zio-schema-json
        decodeResult <- ZIO.attempt(JsonCodec.jsonDecoder(schema).decodeJson(jsonString)).mapError(_.getMessage)
        result <- ZIO.fromEither(decodeResult.left.map(identity))
      } yield result
  }
  
  /**
   * ZLayer that provides a ToonSchemaService with default configuration.
   */
  val live: ZLayer[ToonJsonService, Nothing, ToonSchemaService] =
    ZLayer.fromFunction((jsonService: ToonJsonService) => 
      Live(EncoderConfig.default, DecoderConfig.default, jsonService)
    )
  
  /**
   * ZLayer that provides a ToonSchemaService with custom configuration.
   */
  def configured(
    encoderConfig: EncoderConfig = EncoderConfig.default,
    decoderConfig: DecoderConfig = DecoderConfig.default
  ): ZLayer[ToonJsonService, Nothing, ToonSchemaService] =
    ZLayer.fromFunction((jsonService: ToonJsonService) => 
      Live(encoderConfig, decoderConfig, jsonService)
    )
}
