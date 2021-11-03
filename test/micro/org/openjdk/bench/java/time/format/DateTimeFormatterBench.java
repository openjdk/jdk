package org.openjdk.bench.java.time.format;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Benchmark)
public class DateTimeFormatterBench {

    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("UTC");

    private static final Instant[] INSTANTS = createInstants();

    private static final ZonedDateTime[] ZONED_DATE_TIMES = createZonedDateTimes();

    @Param({
            "HH:mm:ss",
            "HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS"
    })
    public String pattern;

    private static Instant[] createInstants() {
        // Various instants during the same day
        final Instant loInstant = Instant.EPOCH.plus(Duration.ofDays(365*50)); // 2020-01-01
        final Instant hiInstant = loInstant.plus(Duration.ofDays(1));
        final long maxOffsetNanos = Duration.between(loInstant, hiInstant).toNanos();
        final Random random = new Random(0);
        return IntStream
                .range(0, 1_000)
                .mapToObj(ignored -> {
                    final long offsetNanos = (long) Math.floor(random.nextDouble() * maxOffsetNanos);
                    return loInstant.plus(offsetNanos, ChronoUnit.NANOS);
                })
                .toArray(Instant[]::new);
    }

    private static ZonedDateTime[] createZonedDateTimes() {
        return Stream.of(INSTANTS)
                .map(instant -> ZonedDateTime.ofInstant(instant, TIME_ZONE.toZoneId()))
                .toArray(ZonedDateTime[]::new);
    }

    private StringBuilder stringBuilder = new StringBuilder(100);
    private DateTimeFormatter dateTimeFormatter;

    @Setup
    public void setup() {
        dateTimeFormatter = DateTimeFormatter
                .ofPattern(pattern, Locale.US)
                .withZone(TIME_ZONE.toZoneId());
    }

    @Benchmark
    public void formatInstants(final Blackhole blackhole) {
        for (final Instant instant : INSTANTS) {
            stringBuilder.setLength(0);
            dateTimeFormatter.formatTo(instant, stringBuilder);
            blackhole.consume(stringBuilder);
        }
    }

    @Benchmark
    public void formatZonedDateTime(final Blackhole blackhole) {
        for (final ZonedDateTime zonedDateTime : ZONED_DATE_TIMES) {
            stringBuilder.setLength(0);
            dateTimeFormatter.formatTo(zonedDateTime, stringBuilder);
            blackhole.consume(stringBuilder);
        }
    }
}
