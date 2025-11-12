package io.github.riccardomerolla.ziotoon

import zio._
import zio.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for ToonSchemaService following ZIO best practices.
  */
object ToonSchemaServiceSpec extends ZIOSpecDefault {

  // Define test case classes with derived schemas
  case class Person(name: String, age: Int)
  object Person {
    given schema: Schema[Person] = DeriveSchema.gen[Person]
  }

  case class Address(street: String, city: String)
  object Address {
    given schema: Schema[Address] = DeriveSchema.gen[Address]
  }

  case class User(id: Int, person: Person, address: Address)
  object User {
    given schema: Schema[User] = DeriveSchema.gen[User]
  }

  case class TaggedUser(name: String, tags: List[String])
  object TaggedUser {
    given schema: Schema[TaggedUser] = DeriveSchema.gen[TaggedUser]
  }

  val testLayer = ToonJsonService.live >>> ToonSchemaService.live

  def spec = suite("ToonSchemaService")(
    suite("Encoding with schema")(
      test("encode simple case class") {
        val person = Person("Alice", 30)
        for {
          toonString <- ZIO.serviceWithZIO[ToonSchemaService](_.encode(person))
        } yield assertTrue(toonString.nonEmpty && toonString.contains("Alice"))
      }.provideLayer(testLayer),
      test("encode case class with nested object") {
        val user = User(
          id = 1,
          person = Person("Bob", 25),
          address = Address("Main St", "NYC"),
        )
        for {
          toonString <- ZIO.serviceWithZIO[ToonSchemaService](_.encode(user))
        } yield assertTrue(toonString.contains("Bob") && toonString.contains("Main St"))
      }.provideLayer(testLayer),
      test("encode case class with list") {
        val user = TaggedUser("Charlie", List("admin", "user"))
        for {
          toonString <- ZIO.serviceWithZIO[ToonSchemaService](_.encode(user))
        } yield assertTrue(toonString.contains("Charlie") && toonString.contains("admin"))
      }.provideLayer(testLayer),
    ),
    suite("Decoding with schema")(
      test("decode simple case class") {
        val toonString = "name: Alice\nage: 30"
        for {
          person <- ZIO.serviceWithZIO[ToonSchemaService](_.decode[Person](toonString))
        } yield assertTrue(person.name == "Alice" && person.age == 30)
      }.provideLayer(testLayer),
      test("round-trip encode and decode") {
        val original = Person("Dave", 40)
        for {
          encoded <- ZIO.serviceWithZIO[ToonSchemaService](_.encode(original))
          decoded <- ZIO.serviceWithZIO[ToonSchemaService](_.decode[Person](encoded))
        } yield assertTrue(decoded == original)
      }.provideLayer(testLayer),
      test("round-trip with nested objects") {
        val original = User(
          id = 2,
          person = Person("Eve", 28),
          address = Address("Broadway", "LA"),
        )
        for {
          encoded <- ZIO.serviceWithZIO[ToonSchemaService](_.encode(original))
          decoded <- ZIO.serviceWithZIO[ToonSchemaService](_.decode[User](encoded))
        } yield assertTrue(decoded == original)
      }.provideLayer(testLayer),
    ),
    suite("Custom configuration")(
      test("encode with custom encoder config") {
        val person = Person("Frank", 35)
        for {
          toonString <- ZIO.serviceWithZIO[ToonSchemaService](
                          _.encodeWithConfig(person, EncoderConfig(indentSize = 4))
                        )
        } yield assertTrue(toonString.nonEmpty)
      }.provideLayer(testLayer),
      test("decode with custom decoder config") {
        val toonString = "name: Grace\nage: 33"
        for {
          person <- ZIO.serviceWithZIO[ToonSchemaService](
                      _.decodeWithConfig[Person](toonString, DecoderConfig(strictMode = false))
                    )
        } yield assertTrue(person.name == "Grace")
      }.provideLayer(testLayer),
    ),
    suite("Error handling")(
      test("handle decode errors") {
        val invalidToon = "invalid: toon: format: test"
        for {
          result <- ZIO.serviceWithZIO[ToonSchemaService](_.decode[Person](invalidToon)).either
        } yield assertTrue(result.isLeft)
      }.provideLayer(testLayer)
    ),
  )
}
