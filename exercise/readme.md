# Interview Exercise: Kafka + Protobuf Data Pipeline

# Overview
This exercise evaluates your ability to build a real-time data pipeline. You will build a system that generates random device telemetry, stores it in a relational database, and produces it to a Kafka cluster using Protobuf serialization.

## 🛠️ Ground Rules & Environment
Language: Java with Spring Boot (preferred).

AI Policy: AI assistance is highly encouraged. We want to see how you prompt, debug AI-generated code, and explain the logic.

Credentials: All connection strings for the Database and Kafka are provided via Environment Variables.

Verification: Don't just write code—verify it. Use simple "SELECT 1" queries or metadata checks to ensure connections are alive before moving to the next step.

## 📂 Suggested Project Structure
To keep your logic decoupled, we recommend this layout:


```
src
├── main
│   ├── java/com/coderpad/app
│   │   ├── Main.java           <-- Entry point & Test Harness Loop
│   │   ├── config/             <-- DB & Kafka Connection logic
│   │   ├── db/                 <-- Table creation & JDBC Repository
│   │   ├── kafka/              <-- Producer & Serializer logic
│   │   └── model/              <-- Generated Protobuf classes
│   └── resources/
│       └── devices.proto       <-- Your Protobuf definition
```

## 🤖 Step 1: Bootstrap with AI
Since we want to see how you leverage AI, use your assistant to generate the boilerplate.

Recommended Starting Prompt:

"Generate a Spring Boot 3 Java project structure that reads MySQL and Kafka credentials from Environment Variables. Create a DatabaseManager class using JDBC to test a connection and a KafkaConfig class that handles SASL_SSL/PLAIN authentication for Confluent Cloud."

## 📝 Problem Breakdown

### Database Connection (SQL Lite)
Task: Connect to the DB and create a table capable of storing the DeviceEvent payload described below.

### Kafka & Schema Registry (Confluent)
Auth: Confluent Cloud requires security.protocol=SASL_SSL and sasl.mechanisms=PLAIN.

Bootstrap Server: Found in CONFLUENT_BOOTSTRAP.

Schema Registry: URL is in SR_URL.

Naming Restriction: Please prefix your Kafka Topic and Schema Subject with interview_ (e.g., interview_device_events).

### Protobuf Schema Definition
Convert the following JSON structure into a proto3 schema file:


```{
  "device_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "user_id": 458291,
  "event_type": "location_update",
  "lat": 37.774929,
  "lon": -122.419418,
  "accuracy_meters": 12.5,
  "altitude_meters": 15.2,
  "speed_mps": 0.0,
  "heading_degrees": 270.5,
  "tags": ["home", "work", "frequent_location"],
  "metadata": {
    "app_version": "4.2.1",
    "os": "iOS",
    "carrier": "T-Mobile",
    "wifi_ssid": "HomeNetwork"
  },
  "is_foreground": true,
  "battery_pct": 87,
  "timestamp_ms": 1712000000000,
  "session_id": "sess_abc123xyz"
}```
Hint: Use int64 for timestamps and IDs, and appropriate types for coordinates.

### Data Generator & Test Harness
Write a loop that generates N random events and performs the following for each:

Generate: Create a DeviceEvent with plausible random data (valid Lat/Lon, current timestamp).

Persist: Insert the record into your MySQL table.

Stream: Produce the event to Kafka. Use the device_id as the Partition Key to ensure order-per-device.

## ✅ What We’re Evaluating
AI Fluency: Can you fix "hallucinations" or outdated library suggestions the AI provides?

System Design: Correct Protobuf type selection and DB schema mapping.

Resilience: Handling "Topic Already Exists" errors and using Kafka delivery callbacks.

Independence: Can you navigate the environment and verify your progress at each stage?
