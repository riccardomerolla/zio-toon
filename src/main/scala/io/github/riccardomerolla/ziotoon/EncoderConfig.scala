package io.github.riccardomerolla.ziotoon

/** Configuration options for TOON encoding.
  *
  * This is a pure data type (case class) following functional programming principles. Immutable configuration is
  * provided at construction time.
  *
  * ==Usage==
  *
  * {{{
  * // Use default configuration
  * val defaultConfig = EncoderConfig.default
  *
  * // Custom configuration
  * val customConfig = EncoderConfig(
  *   indentSize = 4,
  *   delimiter = Delimiter.Tab
  * )
  *
  * // Use with encoder
  * val encoder = new ToonEncoder(customConfig)
  *
  * // Or with service
  * val layer = ToonEncoderService.configured(customConfig)
  * }}}
  *
  * @param indentSize
  *   Number of spaces per indentation level (default: 2)
  * @param delimiter
  *   Document delimiter for arrays (comma, tab, or pipe)
  */
final case class EncoderConfig(
    indentSize: Int = 2,
    delimiter: Delimiter = Delimiter.Comma,
  )

object EncoderConfig {

  /** Default encoder configuration.
    *
    *   - 2-space indentation
    *   - Comma delimiter
    */
  val default: EncoderConfig = EncoderConfig()
}

/** Supported delimiters for TOON arrays.
  *
  * Following TOON specification, arrays can use different delimiters for better readability and compatibility.
  *
  * ==Delimiter Selection==
  *
  *   - Comma: Default, most compact
  *   - Tab: Better for tabular data
  *   - Pipe: Better visual separation
  *
  * @param char
  *   The character used for delimiting
  * @param symbol
  *   The symbol used in array headers
  */
enum Delimiter(val char: Char, val symbol: String) {

  /** Comma delimiter (default, most compact) */
  case Comma extends Delimiter(',', "")

  /** Tab delimiter (better for tabular data) */
  case Tab extends Delimiter('\t', "\t")

  /** Pipe delimiter (better visual separation) */
  case Pipe extends Delimiter('|', "|")

  /** Check if this is a comma delimiter. */
  def isComma: Boolean = this == Comma
}
