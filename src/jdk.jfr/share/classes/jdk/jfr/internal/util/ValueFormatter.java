/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.jfr.internal.util;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedMethod;

public final class ValueFormatter {
    private static enum TimespanUnit {
        NANOSECONDS("ns", 1000),
        MICROSECONDS("us", 1000),
        MILLISECONDS("ms", 1000),
        SECONDS("s", 60), MINUTES("m", 60),
        HOURS("h", 24),
        DAYS("d", 7);

        private final String text;
        private final long amount;

        TimespanUnit(String unit, long amount) {
            this.text = unit;
            this.amount = amount;
        }
    }

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Duration MICRO_SECOND = Duration.ofNanos(1_000);
    private static final Duration SECOND = Duration.ofSeconds(1);
    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);
    private static final int NANO_SIGNIFICANT_FIGURES = 9;
    private static final int MILL_SIGNIFICANT_FIGURES = 3;
    private static final int DISPLAY_NANO_DIGIT = 3;
    private static final int BASE = 10;

    // -XX:FlightRecorderOptions:repository=<path> triggers an upcall
    // which will load this class. If NumberFormat.getNumberInstance()
    // is called during startup, locale settings will not take effect.
    // Workaround is to create an instance lazily. See numberFormatInstance().
    private static NumberFormat NUMBER_FORMAT;

    public static String formatTimespan(Duration dValue, String separation) {
        if (dValue == null) {
            return "0";
        }
        long value = dValue.toNanos();
        TimespanUnit result = TimespanUnit.NANOSECONDS;
        for (TimespanUnit unit : TimespanUnit.values()) {
            result = unit;
            long amount = unit.amount;
            if (result == TimespanUnit.DAYS || value < amount || value % amount != 0) {
                break;
            }
            value /= amount;
        }
        return String.format("%d%s%s", value, separation, result.text);
    }

    // This method reduces the number of loaded classes
    // compared to DateTimeFormatter
    public static String formatDateTime(LocalDateTime time) {
        StringBuilder sb = new StringBuilder(19);
        sb.append(time.getYear() / 100);
        appendPadded(sb, time.getYear() % 100, true);
        appendPadded(sb, time.getMonth().getValue(), true);
        appendPadded(sb, time.getDayOfMonth(), true);
        appendPadded(sb, time.getHour(), true);
        appendPadded(sb, time.getMinute(), true);
        appendPadded(sb, time.getSecond(), false);
        return sb.toString();
    }

    private static void appendPadded(StringBuilder text, int number, boolean separator) {
        if (number < 10) {
            text.append('0');
        }
        text.append(number);
        if (separator) {
            text.append('_');
        }
    }

    private static NumberFormat numberFormatInstance() {
        if (NUMBER_FORMAT == null) {
            NUMBER_FORMAT = NumberFormat.getNumberInstance();
        }
        return NUMBER_FORMAT;
    }

    public static String formatNumber(Number n) {
        return numberFormatInstance().format(n);
    }

    public static String formatDuration(Duration d) {
        Duration roundedDuration = roundDuration(d);
        if (roundedDuration.equals(Duration.ZERO)) {
            return "0 s";
        } else if (roundedDuration.isNegative()) {
            return "-" + formatPositiveDuration(roundedDuration.abs());
        } else {
            return formatPositiveDuration(roundedDuration);
        }
    }

    private static String formatPositiveDuration(Duration d){
        if (d.compareTo(MICRO_SECOND) < 0) {
            // 0.000001 ms - 0.000999 ms
            double outputMs = (double) d.toNanosPart() / 1_000_000;
            return String.format("%.6f ms", outputMs);
        } else if (d.compareTo(SECOND) < 0) {
            // 0.001 ms - 999 ms
            int valueLength = countLength(d.toNanosPart());
            int outputDigit = NANO_SIGNIFICANT_FIGURES - valueLength;
            double outputMs = (double) d.toNanosPart() / 1_000_000;
            return String.format("%." + outputDigit + "f ms", outputMs);
        } else if (d.compareTo(MINUTE) < 0) {
            // 1.00 s - 59.9 s
            int valueLength = countLength(d.toSecondsPart());
            int outputDigit = MILL_SIGNIFICANT_FIGURES - valueLength;
            double outputSecond = d.toSecondsPart() + (double) d.toMillisPart() / 1_000;
            return String.format("%." + outputDigit + "f s", outputSecond);
        } else if (d.compareTo(HOUR) < 0) {
            // 1 m 0 s - 59 m 59 s
            return String.format("%d m %d s", d.toMinutesPart(), d.toSecondsPart());
        } else if (d.compareTo(DAY) < 0) {
            // 1 h 0 m - 23 h 59 m
            return String.format("%d h %d m", d.toHoursPart(), d.toMinutesPart());
        } else {
            // 1 d 0 h -
            return String.format("%d d %d h", d.toDaysPart(), d.toHoursPart());
        }
    }

    private static int countLength(long value){
        return (int) Math.log10(value) + 1;
    }

    private static Duration roundDuration(Duration d) {
        if (d.equals(Duration.ZERO)) {
            return d;
        } else if(d.isNegative()) {
            Duration roundedPositiveDuration = roundPositiveDuration(d.abs());
            return roundedPositiveDuration.negated();
        } else {
            return roundPositiveDuration(d);
        }
    }

    private static Duration roundPositiveDuration(Duration d){
        if (d.compareTo(MICRO_SECOND) < 0) {
            // No round
            return d;
        } else if (d.compareTo(SECOND) < 0) {
            // Round significant figures to three digits
            int valueLength = countLength(d.toNanosPart());
            int roundValue = (int) Math.pow(BASE, valueLength - DISPLAY_NANO_DIGIT);
            long roundedNanos = Math.round((double) d.toNanosPart() / roundValue) * roundValue;
            return d.truncatedTo(ChronoUnit.SECONDS).plusNanos(roundedNanos);
        } else if (d.compareTo(MINUTE) < 0) {
            // Round significant figures to three digits
            int valueLength = countLength(d.toSecondsPart());
            int roundValue = (int) Math.pow(BASE, valueLength);
            long roundedMills = Math.round((double) d.toMillisPart() / roundValue) * roundValue;
            return d.truncatedTo(ChronoUnit.SECONDS).plusMillis(roundedMills);
        } else if (d.compareTo(HOUR) < 0) {
            // Round for more than 500 ms or less
            return d.plusMillis(SECOND.dividedBy(2).toMillisPart()).truncatedTo(ChronoUnit.SECONDS);
        } else if (d.compareTo(DAY) < 0) {
            // Round for more than 30 seconds or less
            return d.plusSeconds(MINUTE.dividedBy(2).toSecondsPart()).truncatedTo(ChronoUnit.MINUTES);
        } else {
            // Round for more than 30 minutes or less
            return d.plusMinutes(HOUR.dividedBy(2).toMinutesPart()).truncatedTo(ChronoUnit.HOURS);
        }
    }

    public static String formatClass(RecordedClass clazz) {
        String name = clazz.getName();
        if (name.startsWith("[")) {
            return decodeDescriptors(name, "").getFirst();
        }
        return name;
    }

    private static String formatDataAmount(String formatter, long amount) {
        if (amount == Long.MIN_VALUE) {
            return "N/A";
        }
        int exp = (int) (Math.log(Math.abs(amount)) / Math.log(1024));
        char unit = "kMGTPE".charAt(exp - 1);
        return String.format(formatter, amount / Math.pow(1024, exp), unit);
    }

    public static String formatBytesCompact(long bytes) {
        if (bytes < 1024) {
            return String.valueOf(bytes);
        }
        return formatDataAmount("%.1f%cB", bytes);
    }

    public static String formatBits(long bits) {
        if (bits == 1 || bits == -1) {
            return bits + " bit";
        }
        if (bits < 1024 && bits > -1024) {
            return bits + " bits";
        }
        return formatDataAmount("%.1f %cbit", bits);
    }

    public static String formatBytes(long bytes) {
        if (bytes == 1 || bytes == -1) {
            return bytes + " byte";
        }
        if (bytes < 1024 && bytes > -1024) {
            return bytes + " bytes";
        }
        return formatDataAmount("%.1f %cB", bytes);
    }

    public static String formatBytesPerSecond(long bytes) {
        if (bytes < 1024 && bytes > -1024) {
            return bytes + " byte/s";
        }
        return formatDataAmount("%.1f %cB/s", bytes);
    }

    public static String formatBitsPerSecond(long bits) {
        if (bits < 1024 && bits > -1024) {
            return bits + " bps";
        }
        return formatDataAmount("%.1f %cbps", bits);
    }

    public static String formatMethod(RecordedMethod m, boolean compact) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getType().getName());
        sb.append(".");
        sb.append(m.getName());
        sb.append("(");
        StringJoiner sj = new StringJoiner(", ");
        String md = m.getDescriptor().replace("/", ".");
        String parameter = md.substring(1, md.lastIndexOf(")"));
        List<String> parameters = decodeDescriptors(parameter, "");
        if (!compact) {
            for (String qualifiedName :parameters) {
                String typeName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
                sj.add(typeName);
            }
            sb.append(sj.toString());
        } else {
            if (!parameters.isEmpty()) {
               sb.append("...");
            }
        }
        sb.append(")");

        return sb.toString();
    }

    public static List<String> decodeDescriptors(String descriptor, String arraySize) {
        List<String> descriptors = new ArrayList<>();
        for (int index = 0; index < descriptor.length(); index++) {
            String arrayBrackets = "";
            while (descriptor.charAt(index) == '[') {
                arrayBrackets = arrayBrackets + "[" + arraySize + "]";
                arraySize = "";
                index++;
            }
            char c = descriptor.charAt(index);
            String type;
            switch (c) {
            case 'L':
                int endIndex = descriptor.indexOf(';', index);
                type = descriptor.substring(index + 1, endIndex);
                index = endIndex;
                break;
            case 'I':
                type = "int";
                break;
            case 'J':
                type = "long";
                break;
            case 'Z':
                type = "boolean";
                break;
            case 'D':
                type = "double";
                break;
            case 'F':
                type = "float";
                break;
            case 'S':
                type = "short";
                break;
            case 'C':
                type = "char";
                break;
            case 'B':
                type = "byte";
                break;
            default:
                type = "<unknown-descriptor-type>";
            }
            descriptors.add(type + arrayBrackets);
        }
        return descriptors;
    }

    public static String formatTimestamp(Instant instant) {
        if (Instant.MIN.equals(instant)) {
            return "N/A";
        }
        return LocalTime.ofInstant(instant, ZoneId.systemDefault()).format(DATE_FORMAT);
    }
}
