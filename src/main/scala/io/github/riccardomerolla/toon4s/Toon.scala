package io.github.riccardomerolla.toon4s

import zio._

/**
 * Main TOON API object with convenience methods.
 */
object Toon {
  
  /**
   * Encode a ToonValue to TOON format string.
   */
  def encode(value: ToonValue, config: EncoderConfig = EncoderConfig.default): String =
    ToonEncoder(config).encode(value)
  
  /**
   * Decode a TOON format string into a ToonValue.
   */
  def decode(input: String, config: DecoderConfig = DecoderConfig.default): Either[ToonError, ToonValue] =
    ToonDecoder(config).decode(input)
  
  /**
   * Encode a ToonValue to TOON format string (ZIO effect).
   */
  def encodeZ(value: ToonValue, config: EncoderConfig = EncoderConfig.default): UIO[String] =
    ToonEncoderZ.encode(value, config)
  
  /**
   * Decode a TOON format string into a ToonValue (ZIO effect).
   */
  def decodeZ(input: String, config: DecoderConfig = DecoderConfig.default): IO[ToonError, ToonValue] =
    ToonDecoderZ.decode(input, config)
  
  /**
   * Round-trip encode and decode.
   */
  def roundTrip(value: ToonValue, 
                encConfig: EncoderConfig = EncoderConfig.default,
                decConfig: DecoderConfig = DecoderConfig.default): Either[ToonError, ToonValue] = {
    val encoded = encode(value, encConfig)
    decode(encoded, decConfig)
  }
  
  /**
   * Round-trip encode and decode (ZIO effect).
   */
  def roundTripZ(value: ToonValue,
                 encConfig: EncoderConfig = EncoderConfig.default,
                 decConfig: DecoderConfig = DecoderConfig.default): IO[ToonError, ToonValue] = {
    for {
      encoded <- encodeZ(value, encConfig)
      decoded <- decodeZ(encoded, decConfig)
    } yield decoded
  }
}
