package com.coderpad.app.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Component
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    private final String bootstrap;
    private final String apiKey;
    private final String apiSecret;
    private final String schemaRegistryUrl;
    private final String schemaRegistryAuth;

    public KafkaConfig() {
        this.bootstrap = System.getenv("CONFLUENT_BOOTSTRAP");
        this.apiKey = System.getenv("CONFLUENT_API_KEY");
        this.apiSecret = System.getenv("CONFLUENT_API_SECRET");
        this.schemaRegistryUrl = System.getenv("SR_URL");
        String srKey = System.getenv("SR_API_KEY");
        String srSecret = System.getenv("SR_API_SECRET");
        this.schemaRegistryAuth = (srKey != null && srSecret != null) ? srKey + ":" + srSecret : null;
    }

    private void requireKafkaCreds() {
        if (isBlank(bootstrap)) throw new IllegalStateException("Missing required env var: CONFLUENT_BOOTSTRAP");
        if (isBlank(apiKey))    throw new IllegalStateException("Missing required env var: CONFLUENT_API_KEY");
        if (isBlank(apiSecret)) throw new IllegalStateException("Missing required env var: CONFLUENT_API_SECRET");
    }

    public Properties protobufProducerProperties() {
        requireKafkaCreds();
        if (isBlank(schemaRegistryUrl) || schemaRegistryAuth == null) {
            throw new IllegalStateException("Schema Registry env vars (SR_URL/SR_API_KEY/SR_API_SECRET) required for protobuf producer");
        }
        Properties p = baseSaslProperties();
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer");
        p.put("schema.registry.url", schemaRegistryUrl);
        p.put("basic.auth.credentials.source", "USER_INFO");
        p.put("basic.auth.user.info", schemaRegistryAuth);
        p.put("auto.register.schemas", true);
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        p.put(ProducerConfig.CLIENT_ID_CONFIG, "interview-producer");
        return p;
    }

    public Properties adminProperties() {
        requireKafkaCreds();
        Properties p = baseSaslProperties();
        p.put(AdminClientConfig.CLIENT_ID_CONFIG, "interview-admin");
        p.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 15000);
        return p;
    }

    public String schemaRegistryUrl() { return schemaRegistryUrl; }
    public String schemaRegistryBasicAuth() { return schemaRegistryAuth; }

    public void verifyConnection() throws Exception {
        log.info("Connecting to Confluent at {}", bootstrap);
        try (AdminClient admin = AdminClient.create(adminProperties())) {
            DescribeClusterResult res = admin.describeCluster();
            String clusterId = res.clusterId().get(15, TimeUnit.SECONDS);
            int nodeCount = res.nodes().get(15, TimeUnit.SECONDS).size();
            log.info("Kafka liveness OK — clusterId={}, nodes={}", clusterId, nodeCount);
        }
    }

    private Properties baseSaslProperties() {
        Properties p = new Properties();
        p.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        p.put(SaslConfigs.SASL_MECHANISM, "PLAIN");
        p.put(SaslConfigs.SASL_JAAS_CONFIG, String.format(
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                apiKey, apiSecret));
        p.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "https");
        return p;
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
