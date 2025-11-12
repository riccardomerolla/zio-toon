# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- GitHub Actions workflow for CI/CD
- Automated publishing to Maven Central via sbt-ci-release

### Changed

### Deprecated

### Removed

### Fixed

### Security

## [0.1.0] - TBD

### Added
- Initial release of zio-toon
- TOON (Token-Oriented Object Notation) encoder and decoder
- ZIO-based service pattern with dependency injection
- JSON bidirectional conversion with zio-json integration
- ZIO Schema support for automatic encoding/decoding
- Streaming support with ZStream
- Retry policies for resilient operations
- Comprehensive test suite with ZIO Test
- JMH benchmarks for performance testing
- Type-safe error handling with domain-specific error ADTs
- Resource-safe operations with ZIO Scope

### Features
- Token-efficient serialization (30-60% reduction vs JSON)
- Explicit schema with array lengths
- Tabular format for uniform arrays
- Human-readable indentation-based format
- Pure functional API with ZIO effects
- Composable services via ZLayer

---

[Unreleased]: https://github.com/riccardomerolla/zio-toon/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/riccardomerolla/zio-toon/releases/tag/v0.1.0

