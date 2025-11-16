package io.github.riccardomerolla.ziotoon

import scala.collection.immutable.VectorMap

import zio.Chunk

/** Core data model for TOON values, representing the JSON data model as specified in the TOON specification.
  *
  * This is a pure ADT (Algebraic Data Type) following functional programming principles:
  *   - Immutable data structures
  *   - Type-safe representation
  *   - Pattern matching for exhaustive handling
  *
  * ==Overview==
  *
  * TOON values mirror JSON's data model:
  *   - Primitives: String, Number, Boolean, Null
  *   - Containers: Object (ordered key-value pairs), Array (ordered sequence)
  *
  * ==Usage==
  *
  * {{{
  * import ToonValue._
  *
  * // Create values using helper methods
  * val person = obj(
  *   "name" -> str("Alice"),
  *   "age" -> num(30),
  *   "active" -> bool(true),
  *   "email" -> Null
  * )
  *
  * val tags = arr(str("admin"), str("ops"), str("dev"))
  *
  * // Pattern match for exhaustive handling
  * person match {
  *   case Obj(fields) => // handle object
  *   case Arr(elements) => // handle array
  *   case Str(s) => // handle string
  *   case Num(n) => // handle number
  *   case Bool(b) => // handle boolean
  *   case Null => // handle null
  * }
  * }}}
  *
  * ==Type Safety==
  *
  * The ADT ensures type safety at compile time:
  *   - Invalid constructions are compile errors
  *   - Pattern matching is exhaustive (compiler warns if cases are missing)
  *   - No runtime type casting needed
  */
sealed trait ToonValue

object ToonValue {

  /** Primitive TOON values */
  sealed trait Primitive extends ToonValue

  /** String value */
  final case class Str(value: String) extends Primitive

  /** Numeric value (stored as BigDecimal for precision and canonical formatting) */
  final case class Num(value: BigDecimal) extends Primitive

  /** Boolean value */
  final case class Bool(value: Boolean) extends Primitive

  /** Null value */
  case object Null extends Primitive

  /** Object value - ordered map from string keys to ToonValue */
  final case class Obj(fields: VectorMap[String, ToonValue]) extends ToonValue {
    def toMap: Map[String, ToonValue]          = fields.toMap
    def toChunk: Chunk[(String, ToonValue)]    = Chunk.fromIterable(fields)
    def iterator: Iterator[(String, ToonValue)] = fields.iterator
  }

  object Obj {
    def apply(fields: (String, ToonValue)*): Obj =
      Obj(VectorMap.from(fields))

    def fromMap(map: Map[String, ToonValue]): Obj =
      Obj(VectorMap.from(map))

    val empty: Obj = Obj(VectorMap.empty)
  }

  /** Array value - ordered sequence of ToonValue The encoder will determine the best representation (inline, tabular,
    * or list)
    */
  final case class Arr(elements: Chunk[ToonValue]) extends ToonValue {
    def length: Int = elements.length

    def isEmpty: Boolean = elements.isEmpty

    def isUniform: Boolean = elements match {
      case Chunk()                                    => true
      case chunk if chunk.forall(_.isInstanceOf[Obj]) =>
        // Check if all objects have the same keys
        val objs = chunk.collect { case o: Obj => o }
        if (objs.isEmpty) true
        else {
          val firstKeys = objs.head.fields.keySet
          objs.tail.forall(obj => obj.fields.keySet == firstKeys)
        }
      case chunk                                      =>
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
  def str(s: String): Str                    = Str(s)
  def num(n: BigDecimal): Num                = Num(n)
  def num(n: Double): Num                    = Num(BigDecimal(n))
  def num(n: Int): Num                       = Num(BigDecimal(n))
  def bool(b: Boolean): Bool                 = Bool(b)
  def obj(fields: (String, ToonValue)*): Obj = Obj(fields: _*)
  def arr(elements: ToonValue*): Arr         = Arr(elements: _*)
}
