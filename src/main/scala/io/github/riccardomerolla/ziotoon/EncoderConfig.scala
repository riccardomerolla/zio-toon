package io.github.riccardomerolla.ziotoon

/**
 * Configuration options for TOON encoding.
 * 
 * @param indentSize Number of spaces per indentation level (default: 2)
 * @param delimiter Document delimiter for arrays (comma, tab, or pipe)
 * @param keyFolding Whether to enable key folding (default: off)
 */
final case class EncoderConfig(
  indentSize: Int = 2,
  delimiter: Delimiter = Delimiter.Comma,
  keyFolding: KeyFolding = KeyFolding.Off
)

object EncoderConfig {
  val default: EncoderConfig = EncoderConfig()
}

/**
 * Supported delimiters for TOON arrays.
 */
enum Delimiter(val char: Char, val symbol: String) {
  case Comma extends Delimiter(',', "")
  case Tab extends Delimiter('\t', "\t")
  case Pipe extends Delimiter('|', "|")
  
  def isComma: Boolean = this == Comma
}

/**
 * Key folding options.
 */
enum KeyFolding {
  case Off
  case Safe
}
