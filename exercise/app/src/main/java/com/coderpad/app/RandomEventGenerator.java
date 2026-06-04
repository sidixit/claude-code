package com.coderpad.app;

import com.coderpad.app.model.DeviceEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RandomEventGenerator {

    private static final List<String> DEVICE_POOL = List.of(
            "f47ac10b-58cc-4372-a567-0e02b2c3d479",
            "9a3b7e4d-12fc-4ad9-bc4e-7d8c5f8a1234",
            "11111111-2222-3333-4444-555555555555",
            "abcde123-4567-89ab-cdef-1234567890ab",
            UUID.randomUUID().toString()
    );

    private static final List<String> EVENT_TYPES = List.of(
            "location_update", "app_open", "app_close", "settings_change", "background_sync"
    );

    private static final List<String> TAG_POOL = List.of(
            "home", "work", "frequent_location", "weekend", "morning", "commute"
    );

    private static final List<String> CARRIERS = List.of("T-Mobile", "Verizon", "AT&T", "Comcast");
    private static final List<String> OSES     = List.of("iOS", "Android");
    private static final List<String> SSIDS    = List.of("HomeNetwork", "OfficeWifi", "CafeFree", "");
    private static final List<String> SESSIONS = List.of(
            "sess_abc123xyz", "sess_def456uvw", "sess_ghi789rst");

    public DeviceEvent generate() {
        ThreadLocalRandom r = ThreadLocalRandom.current();

        DeviceEvent.Builder b = DeviceEvent.newBuilder()
                .setDeviceId(pick(DEVICE_POOL, r))
                .setUserId(100_000L + r.nextLong(900_000L))
                .setEventType(pick(EVENT_TYPES, r))
                .setLat(round6(r.nextDouble(24.0, 49.0)))
                .setLon(round6(r.nextDouble(-125.0, -67.0)))
                .setAccuracyMeters(round1(r.nextDouble(1.0, 50.0)))
                .setAltitudeMeters(round1(r.nextDouble(-10.0, 3000.0)))
                .setSpeedMps(round1(r.nextDouble(0.0, 30.0)))
                .setHeadingDegrees(round1(r.nextDouble(0.0, 360.0)))
                .setIsForeground(r.nextBoolean())
                .setBatteryPct(r.nextInt(1, 101))
                .setTimestampMs(System.currentTimeMillis())
                .setSessionId(pick(SESSIONS, r));

        int tagCount = r.nextInt(0, 4);
        for (int i = 0; i < tagCount; i++) {
            b.addTags(pick(TAG_POOL, r));
        }

        b.putMetadata("app_version", "4." + r.nextInt(0, 5) + "." + r.nextInt(0, 10));
        b.putMetadata("os", pick(OSES, r));
        b.putMetadata("carrier", pick(CARRIERS, r));
        b.putMetadata("wifi_ssid", pick(SSIDS, r));

        return b.build();
    }

    private static <T> T pick(List<T> xs, ThreadLocalRandom r) {
        return xs.get(r.nextInt(xs.size()));
    }

    private static double round6(double v) { return Math.round(v * 1_000_000d) / 1_000_000d; }
    private static double round1(double v) { return Math.round(v * 10d) / 10d; }
}
