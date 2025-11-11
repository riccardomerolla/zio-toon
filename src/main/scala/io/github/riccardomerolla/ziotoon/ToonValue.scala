package io.github.riccardomerolla.ziotoon

import zio.Chunk

/**
 * Core data model for TOON values, representing the JSON data model
 * as specified in the TOON specification.
 */
sealed trait ToonValue

object ToonValue {
  
  /** Primitive TOON values */
  sealed trait Primitive extends ToonValue
  
  /** String value */
  final case class Str(value: String) extends Primitive
  
  /** Numeric value (stored as Double for simplicity, can be extended) */
  final case class Num(value: Double) extends Primitive
  
  /** Boolean value */
  final case class Bool(value: Boolean) extends Primitive
  
  /** Null value */
  case object Null extends Primitive
  
  /** Object value - ordered map from string keys to ToonValue */
  final case class Obj(fields: Chunk[(String, ToonValue)]) extends ToonValue {
    def toMap: Map[String, ToonValue] = fields.toMap
  }
  
  object Obj {
    def apply(fields: (String, ToonValue)*): Obj = 
      Obj(Chunk.fromIterable(fields))
    
    def fromMap(map: Map[String, ToonValue]): Obj = 
      Obj(Chunk.fromIterable(map))
    
    val empty: Obj = Obj(Chunk.empty)
  }
  
  /** 
   * Array value - ordered sequence of ToonValue 
   * The encoder will determine the best representation (inline, tabular, or list)
   */
  final case class Arr(elements: Chunk[ToonValue]) extends ToonValue {
    def length: Int = elements.length
    
    def isEmpty: Boolean = elements.isEmpty
    
    def isUniform: Boolean = elements match {
      case Chunk() => true
      case chunk if chunk.forall(_.isInstanceOf[Obj]) =>
        // Check if all objects have the same keys
        val objs = chunk.collect { case o: Obj => o }
        if (objs.isEmpty) true
        else {
          val firstKeys = objs.head.fields.map(_._1).toSet
          objs.tail.forall(obj => obj.fields.map(_._1).toSet == firstKeys)
        }
      case chunk => 
        // All elements are primitives of compatible types
        chunk.forall(_.isInstanceOf[Primitive])
    }
    
    def allPrimitives: Boolean = elements.forall(_.isInstanceOf[Primitive])
  }
  
  object Arr {
    def apply(elements: ToonValue*): Arr = 
      Arr(Chunk.fromIterable(elements))
    
    def empty: Arr = Arr(Chunk.empty)
  }
  
  // Helper methods for creating values
  def str(s: String): Str = Str(s)
  def num(n: Double): Num = Num(n)
  def num(n: Int): Num = Num(n.toDouble)
  def bool(b: Boolean): Bool = Bool(b)
  def obj(fields: (String, ToonValue)*): Obj = Obj(fields: _*)
  def arr(elements: ToonValue*): Arr = Arr(elements: _*)
}
