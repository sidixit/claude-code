package com.coderpad.app.kafka;

import com.coderpad.app.config.KafkaConfig;
import com.coderpad.app.model.DeviceEvent;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DeviceEventProducer {

    private static final Logger log = LoggerFactory.getLogger(DeviceEventProducer.class);

    public static final String TOPIC = "interview_device_events";
    private static final int PARTITIONS = 3;
    private static final short REPLICATION = 3;

    private final KafkaConfig kafkaConfig;
    private Producer<String, DeviceEvent> producer;

    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public DeviceEventProducer(KafkaConfig kafkaConfig) {
        this.kafkaConfig = kafkaConfig;
    }

    public void ensureTopic() throws Exception {
        Properties adminProps = kafkaConfig.adminProperties();
        try (AdminClient admin = AdminClient.create(adminProps)) {
            NewTopic topic = new NewTopic(TOPIC, PARTITIONS, REPLICATION);
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
        }
    }

    public synchronized Producer<String, DeviceEvent> getProducer() {
        if (producer == null) {
            producer = new KafkaProducer<>(kafkaConfig.protobufProducerProperties());
            log.info("Initialized KafkaProducer for topic '{}'", TOPIC);
        }
        return producer;
    }

    public void send(DeviceEvent event) {
        ProducerRecord<String, DeviceEvent> record =
                new ProducerRecord<>(TOPIC, event.getDeviceId(), event);
        getProducer().send(record, (md, ex) -> {
            if (ex != null) {
                failed.incrementAndGet();
                log.error("Send failed for device={}", event.getDeviceId(), ex);
            } else {
                sent.incrementAndGet();
                log.info("Sent device={} → {}-p{}@offset={}",
                        event.getDeviceId(), md.topic(), md.partition(), md.offset());
            }
        });
    }

    public void flush() {
        if (producer != null) producer.flush();
    }

    public long getSent()   { return sent.get(); }
    public long getFailed() { return failed.get(); }

    @PreDestroy
    public void close() {
        if (producer != null) {
            producer.flush();
            producer.close();
            log.info("KafkaProducer closed");
        }
    }
}
