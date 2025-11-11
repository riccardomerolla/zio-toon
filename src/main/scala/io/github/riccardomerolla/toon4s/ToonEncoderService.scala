package io.github.riccardomerolla.toon4s

import zio._

/**
 * Service for encoding ToonValue to TOON format strings.
 * 
 * This is the service interface following ZIO best practices.
 * Effects are pure blueprints that describe encoding operations.
 */
trait ToonEncoderService {
  /**
   * Encode a ToonValue to TOON format string.
   * 
   * @param value The ToonValue to encode
   * @return A ZIO effect that produces the encoded string
   */
  def encode(value: ToonValue): UIO[String]
}

object ToonEncoderService {
  
  /**
   * Live implementation of ToonEncoderService.
   */
  final case class Live(config: EncoderConfig) extends ToonEncoderService {
    private val encoder = new ToonEncoder(config)
    
    def encode(value: ToonValue): UIO[String] =
      ZIO.succeed(encoder.encode(value))
  }
  
  /**
   * ZLayer that provides a ToonEncoderService with default configuration.
   */
  val live: ULayer[ToonEncoderService] =
    ZLayer.succeed(Live(EncoderConfig.default))
  
  /**
   * ZLayer that provides a ToonEncoderService with custom configuration.
   */
  def configured(config: EncoderConfig): ULayer[ToonEncoderService] =
    ZLayer.succeed(Live(config))
  
  /**
   * Accessor method to encode a value using the service from the environment.
   */
  def encode(value: ToonValue): ZIO[ToonEncoderService, Nothing, String] =
    ZIO.serviceWithZIO[ToonEncoderService](_.encode(value))
}
