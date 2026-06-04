# Design Notes — Kafka + Protobuf Pipeline

A short tour of the decisions made and why, organized against the four
evaluation criteria from the readme.

---

## 1. AI Fluency — fixing what the boilerplate prompt got wrong

The recommended starting prompt asked for **MySQL + Spring Boot 3 + JDBC + Kafka SASL_SSL/PLAIN**. Several things didn't survive contact with the actual environment:

| Issue | What the boilerplate gave | What was actually needed |
|---|---|---|
| **DB choice** | MySQL JDBC URL + driver | Readme said "SQL Lite" and `interview.db` already existed. Switched to `org.xerial:sqlite-jdbc`, JDBC URL `jdbc:sqlite:interview.db`. |
| **Spring DataSource auto-config** | Pulled in `spring-boot-starter-jdbc`, which eagerly tries to wire a Hikari `DataSource` and fails at startup with "Failed to determine a suitable driver class" because we never set `spring.datasource.url`. | Dropped the JDBC starter entirely — we're using `DriverManager` directly inside `DatabaseManager`, so Spring's auto-wired pool was dead weight. |
| **Eager Kafka env-var validation** | Constructor of `KafkaConfig` threw `IllegalStateException` if `CONFLUENT_BOOTSTRAP` was unset, so `db-test` couldn't run without Kafka creds. | Made env validation **lazy** — read in constructor, validate only when `producerProperties()` / `adminProperties()` is called. |
| **Bootstrap port** | The creds file says `pkc-921jm.us-east-2.aws.confluent.cloud:443 (kafka rest)`. AI tools tend to copy that verbatim. | `:443` is the **REST** endpoint. The Kafka client (SASL_SSL) wants **`:9092`**. Verified by `AdminClient.describeCluster()` returning the expected `clusterId=lkc-kdqgz2`. |
| **Plain `ByteArraySerializer` for protobuf** | A naive setup just calls `event.toByteArray()` and uses `ByteArraySerializer`. That works but bypasses Schema Registry — and the readme explicitly calls out Schema Registry + the `interview_` subject prefix. | Switched to `io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer` with `auto.register.schemas=true`. Schema gets registered under subject `interview_device_events-value` (TopicNameStrategy), satisfying the prefix rule for free. |
| **Confluent SDK transitive `slf4j-reload4j`** | Pulls in a logging backend that conflicts with Spring Boot's logback. | Excluded `org.slf4j:slf4j-reload4j` from the `kafka-protobuf-serializer` dep. |

---

## 2. System Design — Protobuf type selection & DB schema mapping

### Protobuf ([devices.proto](app/src/main/resources/devices.proto))

| JSON field | proto3 type | Why |
|---|---|---|
| `device_id` (UUID) | `string` | UUIDs are textual; not worth bit-packing. |
| `user_id` | `int64` | Per the readme hint — IDs may exceed 2^31. |
| `lat` / `lon` | `double` | `float` loses precision below ~7 decimals; the sample value `37.774929` already needs 6 decimals of fidelity. `double` is the safe default. |
| `accuracy_meters`, `altitude_meters`, `speed_mps`, `heading_degrees` | `double` | Same reasoning — physical measurements with sub-meter / sub-degree precision. |
| `tags` | `repeated string` | Variable-length collection. |
| `metadata` | `map<string, string>` | Key→value pairs of unknown keys; idiomatic proto3 map. |
| `is_foreground` | `bool` | Native proto type. |
| `battery_pct` | `int32` | 0–100 fits trivially; varint encodes to 1 byte. |
| `timestamp_ms` | `int64` | Per the readme hint — millis since epoch will overflow int32 in 1970. |
| `session_id` | `string` | Opaque token. |

**Field numbers** are dense `1..15` — all single-byte tags in the wire format (tag byte uses field number << 3, so numbers 1–15 fit in 1 byte). Keeps payloads compact.

### SQLite schema ([DeviceEventRepository.java](app/src/main/java/com/coderpad/app/db/DeviceEventRepository.java))

| Proto field | SQLite type | Notes |
|---|---|---|
| `device_id` | `TEXT NOT NULL` | |
| `user_id` | `INTEGER NOT NULL` | SQLite `INTEGER` is 64-bit → safe for proto `int64`. |
| `event_type` | `TEXT NOT NULL` | |
| `lat` → `latitude`, `lon` → `longitude` | `REAL NOT NULL` | SQLite `REAL` is 8-byte IEEE 754 → matches proto `double`. |
| `accuracy_meters`, `altitude_meters`, `speed_mps`, `heading_degrees` | `REAL` (nullable) | |
| `battery_pct` | `INTEGER` | |
| `timestamp_ms` | `INTEGER NOT NULL` | |
| `session_id` | `TEXT` | |
| `tags` | `TEXT` | Serialized as JSON array. SQLite has no native array type; JSON is the most queryable encoding. |
| `metadata` | `TEXT` | Serialized as JSON object. Same reasoning. |
| `is_foreground` | `INTEGER` | 0 / 1 (SQLite has no `BOOLEAN` type). |

**The migration was idempotent.** The pre-existing `interview.db` had only 13 of the 16 columns I needed. Rather than `DROP + CREATE` (which would have nuked the existing 20 rows), the code does `CREATE TABLE IF NOT EXISTS` followed by `ALTER TABLE ADD COLUMN` for each missing column, gated by a `PRAGMA table_info()` check. Re-running the harness shows zero ALTER statements — the migration is a no-op once the schema is current.

**Field-name divergence** (`lat` / `lon` in proto, `latitude` / `longitude` in DB): I kept the existing DB column names so the pre-existing 20 rows remained queryable with the same names. The mapping happens explicitly in `DeviceEventRepository.insert()`.

### Spring wiring

`@SpringBootApplication` with `spring.main.web-application-type=none` — this is a CLI, not a server. Components are autowired via constructor injection (`DatabaseManager`, `DeviceEventRepository`, `KafkaConfig`, `DeviceEventProducer`, `RandomEventGenerator`).

---

## 3. Resilience

### "Topic Already Exists"
Handled in [DeviceEventProducer.ensureTopic()](app/src/main/java/com/coderpad/app/kafka/DeviceEventProducer.java). The pattern:

```java
try {
    admin.createTopics(List.of(topic)).all().get(20, TimeUnit.SECONDS);
    log.info("Created topic '{}' (partitions={}, RF={})", TOPIC, PARTITIONS, REPLICATION);
} catch (ExecutionException ee) {
    if (ee.getCause() instanceof TopicExistsException) {
        log.info("Topic '{}' already exists — continuing", TOPIC);
    } else {
        throw ee;
    }
}
```

Two subtleties:

1. `KafkaFuture.get()` wraps the real exception in `ExecutionException` — must unwrap with `.getCause()`. AI suggestions often catch `TopicExistsException` directly, which silently never matches.
2. `replicationFactor=3` is required by Confluent Cloud Basic clusters. RF=1 will be rejected at topic-create time, so getting this wrong shows up loudly here, not at first send.

### Delivery callbacks
`producer.send(record, callback)` is the only way to know whether an event actually landed:

```java
producer.send(record, (md, ex) -> {
    if (ex != null) { failed.incrementAndGet(); log.error(...); }
    else            { sent.incrementAndGet();   log.info("Sent ... {}-p{}@offset={}", ...); }
});
```

Counters are `AtomicLong` — callbacks fire on the producer's IO thread, so a plain `long++` would race. The harness reports both counters in its summary and `System.exit(1)` if either failure count is non-zero, so a failed send fails the run loudly.

`producer.flush()` is called before reading the counters — `send()` is async, and without flushing, the summary would print before any callback had a chance to run.

### Other reliability hardening
- `acks=all` and `enable.idempotence=true` on the producer — survives single-broker outages without duplicates.
- `request.timeout.ms=15000` on AdminClient — fail fast if the cluster is unreachable, rather than the default 30s.
- `@PreDestroy` on `DatabaseManager` and `DeviceEventProducer` — clean shutdown when Spring's context closes (no leaked sockets, no lost in-flight records).

---

## 4. Independence — verifying at each stage

The readme explicitly called out: *"Don't just write code—verify it. Use simple SELECT 1 queries or metadata checks."* The app has explicit liveness checks at every boundary:

| Stage | Verification | Implementation |
|---|---|---|
| **JDBC connection** | `SELECT 1` and assert it returns `1` | [DatabaseManager.verifyConnection()](app/src/main/java/com/coderpad/app/config/DatabaseManager.java) |
| **DB schema** | `PRAGMA table_info(device_events)` printed after `ensureSchema()` so you can eyeball the columns | [DeviceEventRepository.describeTable()](app/src/main/java/com/coderpad/app/db/DeviceEventRepository.java) |
| **Kafka cluster** | `AdminClient.describeCluster()` — returns `clusterId` + node count | [KafkaConfig.verifyConnection()](app/src/main/java/com/coderpad/app/config/KafkaConfig.java) |
| **Topic existence** | `AdminClient.createTopics()` (idempotent — handles `TopicExistsException`) | [DeviceEventProducer.ensureTopic()](app/src/main/java/com/coderpad/app/kafka/DeviceEventProducer.java) |
| **Per-event delivery** | Callback on every `producer.send()` records success/failure with topic-partition-offset | [DeviceEventProducer.send()](app/src/main/java/com/coderpad/app/kafka/DeviceEventProducer.java) |
| **Schema Registry round-trip** | `auto.register.schemas=true` — first send registers the schema; if it fails (auth, naming), the very first event throws | implicit via `KafkaProtobufSerializer` |

Each mode is independently runnable so you can isolate failures:

```bash
java -jar app/target/app-0.0.1.jar db-test       # DB only — no Confluent creds needed
java -jar app/target/app-0.0.1.jar kafka-test    # Kafka liveness only
java -jar app/target/app-0.0.1.jar harness 100   # full pipeline, N events
```

### Partition-order check (visual)

The readme requires `device_id` as the partition key for order-per-device. The `RandomEventGenerator` deliberately picks from a small pool of 5 device IDs so most runs produce repeated keys, and the producer logs `device → topic-pN@offset=K`. Sample output from a 10-event run:

```
device=11111111-2222-... → interview_device_events-p1@offset=17
device=11111111-2222-... → interview_device_events-p1@offset=18
device=11111111-2222-... → interview_device_events-p1@offset=19   ← all on p1, monotonic
device=64c82cf3-70ba-... → interview_device_events-p3@offset=10
device=64c82cf3-70ba-... → interview_device_events-p3@offset=11
device=64c82cf3-70ba-... → interview_device_events-p3@offset=12   ← all on p3, monotonic
```

Same key ⇒ same partition ⇒ guaranteed delivery order on the consumer side. Verified, not just claimed.

---

## File map

```
exercise/
├── DESIGN_NOTES.md                              ← this file
├── interview.db                                 ← SQLite, migrated to full schema
├── kafka-creds.md                               ← Confluent creds (provided)
├── readme.md                                    ← exercise spec
└── app/
    ├── pom.xml                                  ← Spring Boot 3.3.4, kafka 3.7.1, protobuf 3.25.5, confluent 7.6.1
    └── src/main/
        ├── resources/
        │   ├── application.properties           ← disables web server (CLI app)
        │   └── devices.proto                    ← proto3 schema
        └── java/com/coderpad/app/
            ├── Main.java                        ← entry point + harness loop
            ├── RandomEventGenerator.java        ← plausible random DeviceEvents
            ├── config/
            │   ├── DatabaseManager.java         ← JDBC + SELECT 1
            │   └── KafkaConfig.java             ← SASL_SSL/PLAIN + schema-registry config
            ├── db/
            │   └── DeviceEventRepository.java   ← schema migration + insert()
            ├── kafka/
            │   └── DeviceEventProducer.java     ← topic create + send w/ callback
            └── model/                           ← (generated by protoc into target/, not src/)
```
