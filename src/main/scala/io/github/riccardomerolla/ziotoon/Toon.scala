package io.github.riccardomerolla.ziotoon

import zio._

/**
 * Main TOON API object with convenience methods.
 * 
 * This object provides both pure (Either-based) and effect-based (ZIO) methods.
 * The ZIO methods follow effect-oriented programming principles where effects
 * are immutable blueprints that describe operations.
 */
object Toon {
  
  /**
   * Encode a ToonValue to TOON format string.
   * 
   * This is a pure method that directly executes encoding.
   * For effect-based encoding with dependency injection, use the service accessor.
   */
  def encode(value: ToonValue, config: EncoderConfig = EncoderConfig.default): String =
    new ToonEncoder(config).encode(value)
  
  /**
   * Decode a TOON format string into a ToonValue.
   * 
   * This is a pure method that directly executes decoding.
   * For effect-based decoding with dependency injection, use the service accessor.
   */
  def decode(input: String, config: DecoderConfig = DecoderConfig.default): Either[ToonError, ToonValue] =
    new ToonDecoder(config).decode(input)
  
  /**
   * Encode a ToonValue to TOON format string using the encoder service from environment.
   * 
   * This method requires ToonEncoderService in the environment.
   * Use this for dependency injection and composable effects.
   */
  def encode(value: ToonValue): ZIO[ToonEncoderService, Nothing, String] =
    ToonEncoderService.encode(value)
  
  /**
   * Decode a TOON format string using the decoder service from environment.
   * 
   * This method requires ToonDecoderService in the environment.
   * Use this for dependency injection and composable effects.
   */
  def decode(input: String): ZIO[ToonDecoderService, ToonError, ToonValue] =
    ToonDecoderService.decode(input)
  
  /**
   * Round-trip encode and decode using services from environment.
   * 
   * This follows the ZIO best practice of composing effects through for-comprehension.
   * Effects are immutable blueprints - the actual execution is deferred.
   */
  def roundTrip(value: ToonValue): ZIO[ToonEncoderService & ToonDecoderService, ToonError, ToonValue] =
    for {
      encoded <- ToonEncoderService.encode(value)
      decoded <- ToonDecoderService.decode(encoded)
    } yield decoded
  
  /**
   * Default application layer that provides both encoder and decoder services.
   */
  val live: ULayer[ToonEncoderService & ToonDecoderService] =
    ToonEncoderService.live ++ ToonDecoderService.live
  
  /**
   * Application layer with custom configuration.
   */
  def configured(
    encoderConfig: EncoderConfig = EncoderConfig.default,
    decoderConfig: DecoderConfig = DecoderConfig.default
  ): ULayer[ToonEncoderService & ToonDecoderService] =
    ToonEncoderService.configured(encoderConfig) ++ ToonDecoderService.configured(decoderConfig)
}
