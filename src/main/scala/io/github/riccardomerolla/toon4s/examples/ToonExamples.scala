package io.github.riccardomerolla.toon4s.examples

import io.github.riccardomerolla.toon4s._
import ToonValue._
import zio._

/**
 * Examples demonstrating the usage of toon4s.
 */
object ToonExamples extends ZIOAppDefault {
  
  def run = for {
    _ <- printExample("Basic Primitives", basicPrimitives)
    _ <- printExample("Simple Object", simpleObject)
    _ <- printExample("Nested Objects", nestedObjects)
    _ <- printExample("Inline Arrays", inlineArrays)
    _ <- printExample("Tabular Arrays", tabularArrays)
    _ <- printExample("Tab Delimiter", tabDelimiter)
    _ <- printExampleIO("Round-trip", roundTrip)
  } yield ExitCode.success
  
  def printExample(title: String, example: UIO[String]): UIO[Unit] = {
    for {
      _ <- Console.printLine(s"\n=== $title ===").orDie
      result <- example
      _ <- Console.printLine(result).orDie
    } yield ()
  }
  
  def printExampleIO(title: String, example: IO[ToonError, String]): UIO[Unit] = {
    for {
      _ <- Console.printLine(s"\n=== $title ===").orDie
      result <- example.catchAll(err => ZIO.succeed(s"Error: ${err.message}"))
      _ <- Console.printLine(result).orDie
    } yield ()
  }
  
  def basicPrimitives: UIO[String] = ZIO.succeed {
    val examples = List(
      ("String", str("hello")),
      ("String with spaces", str("hello world")),
      ("Number", num(42)),
      ("Decimal", num(3.14)),
      ("Boolean true", bool(true)),
      ("Boolean false", bool(false)),
      ("Null", Null)
    )
    
    examples.map { case (desc, value) =>
      s"$desc: ${Toon.encode(value)}"
    }.mkString("\n")
  }
  
  def simpleObject: UIO[String] = ZIO.succeed {
    val user = obj(
      "name" -> str("Alice"),
      "age" -> num(30),
      "active" -> bool(true)
    )
    
    Toon.encode(user)
  }
  
  def nestedObjects: UIO[String] = ZIO.succeed {
    val data = obj(
      "user" -> obj(
        "name" -> str("Bob"),
        "contact" -> obj(
          "email" -> str("bob@example.com"),
          "phone" -> str("+1-555-1234")
        )
      )
    )
    
    Toon.encode(data)
  }
  
  def inlineArrays: UIO[String] = ZIO.succeed {
    val data = obj(
      "tags" -> arr(str("admin"), str("ops"), str("dev")),
      "numbers" -> arr(num(1), num(2), num(3), num(4), num(5)),
      "flags" -> arr(bool(true), bool(false), bool(true))
    )
    
    Toon.encode(data)
  }
  
  def tabularArrays: UIO[String] = ZIO.succeed {
    val data = obj(
      "users" -> arr(
        obj("id" -> num(1), "name" -> str("Alice"), "role" -> str("admin"), "active" -> bool(true)),
        obj("id" -> num(2), "name" -> str("Bob"), "role" -> str("developer"), "active" -> bool(true)),
        obj("id" -> num(3), "name" -> str("Charlie"), "role" -> str("designer"), "active" -> bool(false))
      )
    )
    
    val toon = Toon.encode(data)
    
    // Compare with JSON size
    val jsonEquivalent = """{
  "users": [
    {"id": 1, "name": "Alice", "role": "admin", "active": true},
    {"id": 2, "name": "Bob", "role": "developer", "active": true},
    {"id": 3, "name": "Charlie", "role": "designer", "active": false}
  ]
}"""
    
    s"TOON format (${toon.length} chars):\n$toon\n\nJSON equivalent (${jsonEquivalent.length} chars):\n$jsonEquivalent\n\nSize reduction: ${100 - (toon.length * 100 / jsonEquivalent.length)}%"
  }
  
  def tabDelimiter: UIO[String] = ZIO.succeed {
    val data = obj(
      "data" -> arr(
        obj("a" -> num(1), "b" -> num(2), "c" -> num(3)),
        obj("a" -> num(4), "b" -> num(5), "c" -> num(6))
      )
    )
    
    val config = EncoderConfig(delimiter = Delimiter.Tab)
    val toon = Toon.encode(data, config)
    
    s"Using tab delimiter:\n$toon"
  }
  
  def roundTrip: IO[ToonError, String] = {
    val original = obj(
      "user" -> obj(
        "name" -> str("Alice"),
        "age" -> num(30),
        "tags" -> arr(str("admin"), str("ops"))
      ),
      "metadata" -> obj(
        "version" -> num(1),
        "active" -> bool(true)
      )
    )
    
    for {
      encoded <- Toon.encodeZ(original)
      decoded <- Toon.decodeZ(encoded)
      matches = decoded == original
    } yield {
      "Original value encoded to TOON:\n" + encoded + "\n\nDecoded back matches original: " + matches
    }
  }
}
