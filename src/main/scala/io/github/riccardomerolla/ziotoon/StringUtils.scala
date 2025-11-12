package io.github.riccardomerolla.ziotoon

/** Utilities for string quoting and escaping according to TOON specification.
  */
private[ziotoon] object StringUtils {

  /** Escape a string according to TOON rules (Section 7.1). Only these sequences must be escaped: \, ", \n, \r, \t
    */
  def escape(s: String): String = {
    val sb = new StringBuilder(s.length + 10)
    s.foreach {
      case '\\' => sb.append("\\\\")
      case '"'  => sb.append("\\\"")
      case '\n' => sb.append("\\n")
      case '\r' => sb.append("\\r")
      case '\t' => sb.append("\\t")
      case c    => sb.append(c)
    }
    sb.toString
  }

  /** Unescape a string according to TOON rules.
    */
  def unescape(s: String): Either[String, String] = {
    val sb = new StringBuilder(s.length)
    var i  = 0
    while (i < s.length)
      s.charAt(i) match {
        case '\\' if i + 1 < s.length =>
          s.charAt(i + 1) match {
            case '\\' => sb.append('\\'); i += 2
            case '"'  => sb.append('"'); i += 2
            case 'n'  => sb.append('\n'); i += 2
            case 'r'  => sb.append('\r'); i += 2
            case 't'  => sb.append('\t'); i += 2
            case c    => return Left(s"Invalid escape sequence: \\$c")
          }
        case '\\'                     => return Left("Unterminated escape sequence")
        case c                        => sb.append(c); i += 1
      }
    Right(sb.toString)
  }

  /** Check if a string needs quoting according to TOON rules (Section 7.2). A string must be quoted if:
    *   - It is empty
    *   - It has leading or trailing whitespace
    *   - It equals "true", "false", or "null"
    *   - It looks numeric
    *   - It contains special characters: :, ", \, [, ], {, }
    *   - It contains control characters
    *   - It contains the relevant delimiter
    *   - It equals "-" or starts with "-"
    */
  def needsQuoting(s: String, delimiter: Delimiter): Boolean = {
    if (s.isEmpty) return true
    if (s.charAt(0).isWhitespace || s.charAt(s.length - 1).isWhitespace) return true
    if (s == "true" || s == "false" || s == "null") return true
    if (s == "-" || s.startsWith("-")) return true

    // Check if numeric-like
    if (isNumericLike(s)) return true

    // Check for special characters
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (
        c == ':' || c == '"' || c == '\\' ||
        c == '[' || c == ']' || c == '{' || c == '}' ||
        c == '\n' || c == '\r' || c == '\t' ||
        c == delimiter.char
      ) {
        return true
      }
      i += 1
    }

    false
  }

  /** Check if a string looks numeric.
    */
  private def isNumericLike(s: String): Boolean = {
    // Match: /^-?\d+(?:\.\d+)?(?:e[+-]?\d+)?$/i or /^0\d+$/
    if (s.matches("^-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?$")) return true
    if (s.matches("^0\\d+$")) return true // leading zeros like "05"
    false
  }

  /** Quote a string if needed.
    */
  def quoteIfNeeded(s: String, delimiter: Delimiter): String =
    if (needsQuoting(s, delimiter)) {
      "\"" + escape(s) + "\""
    }
    else {
      s
    }

  /** Check if a key is a valid unquoted key. Keys may be unquoted if they match: ^[A-Za-z_][A-Za-z0-9_.]*$
    */
  def isValidUnquotedKey(s: String): Boolean =
    s.nonEmpty && s.matches("^[A-Za-z_][A-Za-z0-9_.]*$")

  /** Quote a key if needed.
    */
  def quoteKeyIfNeeded(key: String): String =
    if (isValidUnquotedKey(key)) {
      key
    }
    else {
      "\"" + escape(key) + "\""
    }
}
