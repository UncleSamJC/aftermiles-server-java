# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build System

This project uses Gradle as its build system with Java 17. Key commands:

- `./gradlew build` - Build the project
- `./gradlew test` - Run tests using JUnit 5
- `./gradlew checkstyle` - Run code style checks (configured in `gradle/checkstyle.xml`)
- `./gradlew clean` - Clean build artifacts
- `./gradlew copyDependencies` - Copy runtime dependencies to `target/lib`

The main JAR is built to `target/` directory with implementation version 6.10.0.

## Running the Application

- Development: `java -jar target/traccar-server-6.10.0.jar debug.xml`
- Production: `java -jar target/traccar-server-6.10.0.jar setup/traccar.xml`

Configuration files are XML-based using Java properties format. The `debug.xml` includes development settings like web console access and enhanced logging.

## Architecture Overview

Traccar is a GPS tracking server that supports 200+ protocols and 2000+ device models. The architecture follows these key patterns:

### Dependency Injection
- Uses Google Guice for dependency injection
- Main modules: `MainModule`, `DatabaseModule`, `WebModule`
- Services are singleton-scoped and managed through `LifecycleObject` interface

### Protocol Handling
- Protocol decoders in `src/main/java/org/traccar/protocol/`
- Each protocol has dedicated decoder/encoder classes extending base classes
- Frame decoders handle message framing before protocol-specific parsing
- All protocols extend `BaseProtocol` and implement `Protocol` interface

### Core Services
- `ServerManager` - Manages tracker servers for different protocols
- `WebServer` - Jersey-based REST API and web interface
- `DatabaseStorage` - Primary storage using Liquibase for migrations
- `BroadcastService` - Inter-instance communication (multicast/Redis)
- `ScheduleManager` - Task scheduling

### Data Processing Pipeline
Position data flows through handlers in sequence:
1. `TimeHandler` - Timestamp validation
2. `HemisphereHandler` - Coordinate correction
3. `DistanceHandler` - Distance calculation
4. `FilterHandler` - Data filtering
5. `GeolocationHandler` - Cell tower/WiFi positioning
6. `GeocoderHandler` - Reverse geocoding
7. `DatabaseHandler` - Persistence

### Configuration System
- XML-based configuration using `Config` class
- Keys defined in `Keys` class with type safety
- Port-specific configuration using `PortConfigSuffix`

## Testing

- Test classes extend `ProtocolTest` base class
- Protocol tests typically verify message parsing using `verifyPosition()` methods
- Use `./gradlew test` to run all tests
- Individual protocol tests can be run by class name

## Key Packages

- `org.traccar.protocol.*` - Protocol implementations
- `org.traccar.handler.*` - Data processing handlers  
- `org.traccar.model.*` - Data models (Position, Device, Event, etc.)
- `org.traccar.api.*` - REST API resources
- `org.traccar.database.*` - Database and storage layer
- `org.traccar.web.*` - Web server and HTTP handling
- `org.traccar.geocoder.*` - Geocoding service implementations
- `org.traccar.forward.*` - Data forwarding (AMQP, Kafka, MQTT, etc.)