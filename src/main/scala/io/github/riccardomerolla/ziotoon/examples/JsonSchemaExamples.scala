package io.github.riccardomerolla.ziotoon.examples

import io.github.riccardomerolla.ziotoon._
import ToonValue._
import zio._
import zio.schema._

/**
 * Examples demonstrating JSON and schema integration.
 */
object JsonSchemaExamples extends ZIOAppDefault {
  
  // Example case class with schema
  case class User(id: Int, name: String, email: String, active: Boolean)
  object User {
    given schema: Schema[User] = DeriveSchema.gen[User]
  }
  
  case class Team(name: String, members: List[User])
  object Team {
    given schema: Schema[Team] = DeriveSchema.gen[Team]
  }
  
  def run = {
    val examples = for {
      _ <- Console.printLine("=" * 60)
      _ <- Console.printLine("ZIO-TOON JSON and Schema Integration Examples")
      _ <- Console.printLine("=" * 60)
      _ <- Console.printLine("")
      
      // Example 1: JSON to TOON conversion
      _ <- Console.printLine("Example 1: JSON to TOON Conversion")
      _ <- Console.printLine("-" * 60)
      jsonString = """{"name":"Alice","age":30,"active":true}"""
      _ <- Console.printLine(s"JSON: $jsonString")
      toonValue <- ToonJsonService.fromJson(jsonString)
      toonString <- ToonEncoderService.encode(toonValue)
      _ <- Console.printLine(s"TOON:\n$toonString")
      _ <- Console.printLine("")
      
      // Example 2: Token Savings Calculation
      _ <- Console.printLine("Example 2: Token Savings Calculation")
      _ <- Console.printLine("-" * 60)
      users = obj(
        "users" -> arr(
          obj("id" -> num(1), "name" -> str("Alice"), "role" -> str("admin")),
          obj("id" -> num(2), "name" -> str("Bob"), "role" -> str("developer")),
          obj("id" -> num(3), "name" -> str("Charlie"), "role" -> str("designer"))
        )
      )
      savings <- ToonJsonService.calculateSavings(users)
      _ <- Console.printLine(s"JSON size: ${savings.jsonSize} characters")
      _ <- Console.printLine(s"TOON size: ${savings.toonSize} characters")
      _ <- Console.printLine(s"Savings: ${savings.savings} characters (${savings.savingsPercent}%)")
      _ <- Console.printLine("")
      
      // Example 3: Schema-based encoding
      _ <- Console.printLine("Example 3: Schema-based Type-Safe Encoding")
      _ <- Console.printLine("-" * 60)
      user = User(1, "Alice Smith", "alice@example.com", true)
      _ <- Console.printLine(s"User object: $user")
      encodedUser <- ZIO.serviceWithZIO[ToonSchemaService](_.encode(user))
      _ <- Console.printLine(s"Encoded to TOON:\n$encodedUser")
      _ <- Console.printLine("")
      
      // Example 4: Schema-based decoding
      _ <- Console.printLine("Example 4: Schema-based Type-Safe Decoding")
      _ <- Console.printLine("-" * 60)
      _ <- Console.printLine(s"Decoding from TOON:\n$encodedUser")
      decodedUser <- ZIO.serviceWithZIO[ToonSchemaService](_.decode[User](encodedUser))
      _ <- Console.printLine(s"Decoded User: $decodedUser")
      _ <- Console.printLine("")
      
      // Example 5: Complex nested structures with schema
      _ <- Console.printLine("Example 5: Complex Nested Structures")
      _ <- Console.printLine("-" * 60)
      team = Team(
        name = "Engineering",
        members = List(
          User(1, "Alice", "alice@example.com", true),
          User(2, "Bob", "bob@example.com", true),
          User(3, "Charlie", "charlie@example.com", false)
        )
      )
      _ <- Console.printLine(s"Team object: $team")
      encodedTeam <- ZIO.serviceWithZIO[ToonSchemaService](_.encode(team))
      _ <- Console.printLine(s"Encoded to TOON:\n$encodedTeam")
      
      // Calculate savings for the team
      teamJson <- ToonJsonService.fromJson(encodedTeam).ignore *> ZIO.succeed(toonValue)
      teamToonValue <- ZIO.serviceWithZIO[ToonSchemaService](_.encode(team)).flatMap(s => 
        ToonDecoderService.decode(s)
      )
      teamSavings <- ToonJsonService.calculateSavings(teamToonValue)
      _ <- Console.printLine(s"\nToken savings: ${teamSavings.savingsPercent}%")
      _ <- Console.printLine("")
      
      _ <- Console.printLine("=" * 60)
      _ <- Console.printLine("All examples completed successfully!")
      _ <- Console.printLine("=" * 60)
    } yield ()
    
    // Provide all necessary layers
    val layers = (ToonEncoderService.live ++ ToonDecoderService.live ++ ToonJsonService.live) >+> ToonSchemaService.live
    
    examples.provideLayer(layers)
  }
}
