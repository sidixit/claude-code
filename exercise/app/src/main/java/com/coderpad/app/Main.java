package com.coderpad.app;

import com.coderpad.app.config.DatabaseManager;
import com.coderpad.app.config.KafkaConfig;
import com.coderpad.app.db.DeviceEventRepository;
import com.coderpad.app.kafka.DeviceEventProducer;
import com.coderpad.app.model.DeviceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private final DatabaseManager databaseManager;
    private final DeviceEventRepository deviceEventRepository;
    private final KafkaConfig kafkaConfig;
    private final DeviceEventProducer deviceEventProducer;
    private final RandomEventGenerator generator;

    public Main(DatabaseManager databaseManager,
                DeviceEventRepository deviceEventRepository,
                KafkaConfig kafkaConfig,
                DeviceEventProducer deviceEventProducer,
                RandomEventGenerator generator) {
        this.databaseManager = databaseManager;
        this.deviceEventRepository = deviceEventRepository;
        this.kafkaConfig = kafkaConfig;
        this.deviceEventProducer = deviceEventProducer;
        this.generator = generator;
    }

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String mode = args.length > 0 ? args[0] : "all";
        log.info("Running in mode: {}", mode);

        switch (mode) {
            case "db-test"    -> runDbTest();
            case "kafka-test" -> runKafkaTest();
            case "harness"    -> runHarness(args);
            case "all" -> {
                runDbTest();
                runKafkaTest();
            }
            default -> {
                log.error("Unknown mode '{}'. Use: db-test | kafka-test | harness <N> | all", mode);
                System.exit(2);
            }
        }
    }

    private void runDbTest() throws Exception {
        log.info("=== DB connection test ===");
        databaseManager.verifyConnection();
        deviceEventRepository.ensureSchema();
        deviceEventRepository.describeTable();
        log.info("=== DB test passed ===");
    }

    private void runKafkaTest() throws Exception {
        log.info("=== Kafka connection test ===");
        kafkaConfig.verifyConnection();
        log.info("=== Kafka test passed ===");
    }

    private void runHarness(String[] args) throws Exception {
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        log.info("=== Harness: generating {} events ===", n);

        databaseManager.verifyConnection();
        deviceEventRepository.ensureSchema();
        kafkaConfig.verifyConnection();
        deviceEventProducer.ensureTopic();

        long started = System.currentTimeMillis();
        long inserted = 0;
        long insertFailed = 0;
        for (int i = 0; i < n; i++) {
            DeviceEvent event = generator.generate();
            try {
                long id = deviceEventRepository.insert(event);
                inserted++;
                log.debug("Inserted row id={} device={}", id, event.getDeviceId());
            } catch (Exception ex) {
                insertFailed++;
                log.error("Insert failed for device={}", event.getDeviceId(), ex);
            }
            deviceEventProducer.send(event);
        }
        deviceEventProducer.flush();
        long elapsed = System.currentTimeMillis() - started;

        log.info("=== Harness summary ===");
        log.info("  generated:       {}", n);
        log.info("  db inserted:     {}", inserted);
        log.info("  db failed:       {}", insertFailed);
        log.info("  kafka delivered: {}", deviceEventProducer.getSent());
        log.info("  kafka failed:    {}", deviceEventProducer.getFailed());
        log.info("  elapsed:         {} ms", elapsed);

        if (insertFailed > 0 || deviceEventProducer.getFailed() > 0) {
            System.exit(1);
        }
    }
}
