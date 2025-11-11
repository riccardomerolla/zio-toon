package io.github.riccardomerolla.toon4s

import ToonValue._
import zio.Chunk

/**
 * Encoder for converting ToonValue to TOON format string.
 */
class ToonEncoder(config: EncoderConfig = EncoderConfig.default) {
  
  import StringUtils._
  
  private val indent = " " * config.indentSize
  
  /**
   * Encode a ToonValue to TOON format string.
   */
  def encode(value: ToonValue): String = {
    encodeValue(value, 0, isRootContext = true).mkString("\n")
  }
  
  /**
   * Encode a value at a given depth.
   * Returns lines without trailing newline.
   */
  private def encodeValue(value: ToonValue, depth: Int, isRootContext: Boolean): Chunk[String] = {
    value match {
      case obj: Obj => 
        if (isRootContext) encodeRootObject(obj)
        else encodeObject(obj, depth)
        
      case arr: Arr => 
        if (isRootContext) encodeRootArray(arr)
        else encodeArray(arr, depth, None)
        
      case prim: Primitive => 
        Chunk.single(encodePrimitive(prim))
    }
  }
  
  /**
   * Encode a primitive value.
   */
  private def encodePrimitive(prim: Primitive): String = prim match {
    case Str(s)   => quoteIfNeeded(s, config.delimiter)
    case Num(n)   => formatNumber(n)
    case Bool(b)  => b.toString
    case Null     => "null"
  }
  
  /**
   * Format a number according to TOON canonical form (Section 2).
   * - No exponent notation
   * - No leading zeros (except "0")
   * - No trailing zeros in fractional part
   * - If fractional part is zero, emit as integer
   * - -0 becomes 0
   */
  private def formatNumber(n: Double): String = {
    if (n.isNaN || n.isInfinity) return "null"
    if (n == 0.0 || n == -0.0) return "0"
    
    // Check if it's an integer value
    if (n == n.toLong.toDouble && n.abs < Long.MaxValue.toDouble) {
      n.toLong.toString
    } else {
      // Format with sufficient precision, remove trailing zeros
      val formatted = f"$n%.15f"
      val withoutTrailing = formatted.replaceAll("0+$", "").replaceAll("\\.$", "")
      withoutTrailing
    }
  }
  
  /**
   * Encode root-level object (no indentation for first level fields).
   */
  private def encodeRootObject(obj: Obj): Chunk[String] = {
    obj.fields.flatMap { case (key, value) =>
      encodeField(key, value, 0)
    }
  }
  
  /**
   * Encode an object at a given depth.
   */
  private def encodeObject(obj: Obj, depth: Int): Chunk[String] = {
    obj.fields.flatMap { case (key, value) =>
      encodeField(key, value, depth)
    }
  }
  
  /**
   * Encode a field (key-value pair) at a given depth.
   */
  private def encodeField(key: String, value: ToonValue, depth: Int): Chunk[String] = {
    val indentation = indent * depth
    val quotedKey = quoteKeyIfNeeded(key)
    
    value match {
      case prim: Primitive =>
        Chunk.single(s"$indentation$quotedKey: ${encodePrimitive(prim)}")
        
      case obj: Obj =>
        val header = s"$indentation$quotedKey:"
        header +: encodeObject(obj, depth + 1)
        
      case arr: Arr =>
        encodeArray(arr, depth, Some(key))
    }
  }
  
  /**
   * Encode an array.
   */
  private def encodeArray(arr: Arr, depth: Int, keyOpt: Option[String]): Chunk[String] = {
    if (arr.isEmpty) {
      val indentation = indent * depth
      val header = keyOpt match {
        case Some(key) => s"$indentation${quoteKeyIfNeeded(key)}[0]:"
        case None => s"$indentation[0]:"
      }
      return Chunk.single(header)
    }
    
    // Determine array type
    if (arr.allPrimitives) {
      encodeInlineArray(arr, depth, keyOpt)
    } else if (arr.isUniform && arr.elements.head.isInstanceOf[Obj]) {
      encodeTabularArray(arr, depth, keyOpt)
    } else {
      encodeListArray(arr, depth, keyOpt)
    }
  }
  
  /**
   * Encode root-level array.
   */
  private def encodeRootArray(arr: Arr): Chunk[String] = {
    encodeArray(arr, 0, None)
  }
  
  /**
   * Encode an inline primitive array: key[N]: v1,v2,v3
   */
  private def encodeInlineArray(arr: Arr, depth: Int, keyOpt: Option[String]): Chunk[String] = {
    val indentation = indent * depth
    val delimChar = config.delimiter.char
    
    val values = arr.elements.map {
      case prim: Primitive => encodePrimitive(prim)
      case _ => encodePrimitive(Null) // Shouldn't happen for inline arrays
    }.mkString(delimChar.toString)
    
    val delimSymbol = if (config.delimiter.isComma) "" else config.delimiter.symbol
    
    val header = keyOpt match {
      case Some(key) => 
        s"$indentation${quoteKeyIfNeeded(key)}[${arr.length}$delimSymbol]: $values"
      case None => 
        s"$indentation[${arr.length}$delimSymbol]: $values"
    }
    
    Chunk.single(header)
  }
  
  /**
   * Encode a tabular array: key[N]{f1,f2}: with rows
   */
  private def encodeTabularArray(arr: Arr, depth: Int, keyOpt: Option[String]): Chunk[String] = {
    val indentation = indent * depth
    val rowIndentation = indent * (depth + 1)
    
    val objs = arr.elements.collect { case o: Obj => o }
    if (objs.isEmpty) return Chunk.empty
    
    // Extract field names from first object
    val fieldNames = objs.head.fields.map(_._1)
    val quotedFields = fieldNames.map(quoteKeyIfNeeded)
    
    val delimChar = config.delimiter.char
    val delimSymbol = if (config.delimiter.isComma) "" else config.delimiter.symbol
    val fieldList = quotedFields.mkString(delimChar.toString)
    
    val header = keyOpt match {
      case Some(key) => 
        s"$indentation${quoteKeyIfNeeded(key)}[${arr.length}$delimSymbol]{$fieldList}:"
      case None => 
        s"$indentation[${arr.length}$delimSymbol]{$fieldList}:"
    }
    
    // Encode rows
    val rows = objs.map { obj =>
      val values = fieldNames.map { fieldName =>
        obj.fields.find(_._1 == fieldName) match {
          case Some((_, prim: Primitive)) => encodePrimitive(prim)
          case Some((_, _)) => "null" // Complex values in tabular not supported, use null
          case None => "null" // Missing field
        }
      }
      s"$rowIndentation${values.mkString(delimChar.toString)}"
    }
    
    header +: rows
  }
  
  /**
   * Encode a list-style array with items marked by "-"
   */
  private def encodeListArray(arr: Arr, depth: Int, keyOpt: Option[String]): Chunk[String] = {
    val indentation = indent * depth
    val itemIndentation = indent * (depth + 1)
    
    val delimSymbol = if (config.delimiter.isComma) "" else config.delimiter.symbol
    
    val header = keyOpt match {
      case Some(key) => 
        s"$indentation${quoteKeyIfNeeded(key)}[${arr.length}$delimSymbol]:"
      case None => 
        s"$indentation[${arr.length}$delimSymbol]:"
    }
    
    val items = arr.elements.flatMap {
      case obj: Obj if obj.fields.isEmpty =>
        // Empty object: just a hyphen
        Chunk.single(s"$itemIndentation-")
        
      case obj: Obj =>
        // Object with fields
        val firstField = obj.fields.head
        val remainingFields = obj.fields.tail
        
        // First field on hyphen line
        val firstLine = firstField._2 match {
          case prim: Primitive =>
            s"$itemIndentation- ${quoteKeyIfNeeded(firstField._1)}: ${encodePrimitive(prim)}"
          case nestedObj: Obj =>
            s"$itemIndentation- ${quoteKeyIfNeeded(firstField._1)}:" +: 
              encodeObject(nestedObj, depth + 2)
            val header = s"$itemIndentation- ${quoteKeyIfNeeded(firstField._1)}:"
            header +: encodeObject(nestedObj, depth + 2)
          case nestedArr: Arr =>
            encodeArray(nestedArr, depth + 1, Some(s"- ${quoteKeyIfNeeded(firstField._1)}"))
        }
        
        // Remaining fields at depth + 1
        val first = firstField._2 match {
          case prim: Primitive =>
            Chunk.single(s"$itemIndentation- ${quoteKeyIfNeeded(firstField._1)}: ${encodePrimitive(prim)}")
          case nestedObj: Obj =>
            val header = s"$itemIndentation- ${quoteKeyIfNeeded(firstField._1)}:"
            header +: encodeObject(nestedObj, depth + 2)
          case nestedArr: Arr =>
            encodeArray(nestedArr, depth + 1, Some(s"- ${quoteKeyIfNeeded(firstField._1)}"))
        }
        
        val rest = remainingFields.flatMap { case (key, value) =>
          encodeField(key, value, depth + 1)
        }
        
        first ++ rest
        
      case prim: Primitive =>
        Chunk.single(s"$itemIndentation- ${encodePrimitive(prim)}")
        
      case arr: Arr =>
        // Nested array
        encodeArray(arr, depth + 1, Some("-"))
    }
    
    header +: items
  }
}

object ToonEncoder {
  def apply(config: EncoderConfig = EncoderConfig.default): ToonEncoder = 
    new ToonEncoder(config)
  
  /**
   * Convenience method to encode a value.
   */
  def encode(value: ToonValue, config: EncoderConfig = EncoderConfig.default): String = 
    ToonEncoder(config).encode(value)
}
