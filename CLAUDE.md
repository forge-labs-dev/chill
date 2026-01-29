# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Chill is Twitter's extension library for the Kryo serialization framework. It provides serializers and integrations for Scala standard library types, Hadoop, Storm, Akka, and other JVM frameworks.

## Build Commands

```bash
# Compile all modules
sbt compile

# Run all tests
sbt test

# Test with a specific Scala version
sbt "++2.13.18 test"

# Run a single test class
sbt "testOnly com.twitter.chill.KryoSpec"

# Code formatting (check)
sbt "; +scalafmtCheckAll; scalafmtSbtCheck"

# Code formatting (apply)
sbt "; +scalafmtAll; scalafmtSbt"

# Linting with scalafix
sbt "; scalafixEnable; scalafix --check; test:scalafix --check"

# Binary compatibility check (MIMA)
sbt "++2.12.21 mimaReportBinaryIssues"

# Coverage report
sbt coverage clean test coverageReport

# Publish to local repository
sbt publishLocal
```

## Module Architecture

Multi-module SBT build with 11 submodules:

- **chill-scala** (aliased as `chill/`) - Core Scala serializers for standard library types (tuples, collections, regex, enumerations)
- **chill-java** - Java serializers and base infrastructure (KryoInstantiator, KryoPool, config system)
- **chill-akka** - Akka actor serialization support
- **chill-hadoop** - Hadoop serialization integration
- **chill-storm** - Apache Storm topology serialization
- **chill-bijection** - Twitter Bijection library integration
- **chill-algebird** - Twitter Algebird monoid/ring serializers
- **chill-scrooge** - Scrooge (Thrift code generation) support
- **chill-thrift** - Apache Thrift support
- **chill-protobuf** - Protocol Buffers support
- **chill-avro** - Apache Avro integration

Dependencies flow: specialized modules → chill-scala → chill-java

## Key Design Patterns

**KryoInstantiator (Factory Pattern)**: Base class for creating configured Kryo instances. Subclasses override `newKryo()` and compose through method chaining:
```java
new ScalaKryoInstantiator()
  .setClassLoader(cl)
  .setRegistrationRequired(false)
```

**IKryoRegistrar (Registrar Pattern)**: Interface for modular serializer registration with Kryo instances.

**KryoPool**: Thread-safe object pool for sharing Kryo instances across threads.

**ConfiguredInstantiator**: Configures Kryo from `Map[String, String]` for distributed frameworks (Hadoop, Storm, Akka).

## Cross-Version Support

- **Scala versions**: 2.12.21, 2.13.18
- **Java versions**: 17, 21
- Version-specific source directories: `src/main/scala-2.12-/` and `src/main/scala-2.13+/`
- Binary compatibility maintained with version 0.9.5 via MIMA

## Code Style

- Scalafmt with 110 character max line width
- Scalafix for linting (RemoveUnused, ProcedureSyntax rules)
- Apache 2.0 license headers required on all source files
