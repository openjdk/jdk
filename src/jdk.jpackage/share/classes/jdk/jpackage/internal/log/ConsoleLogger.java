/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.log;

import java.io.PrintWriter;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Consumer;

public record ConsoleLogger(Consumer<String> out, Consumer<String> err, Clock timestampClock) {

    public ConsoleLogger {
        Objects.requireNonNull(out);
        Objects.requireNonNull(err);
        Objects.requireNonNull(timestampClock);
    }

    public ConsoleLogger(Consumer<String> out, Consumer<String> err) {
        this(out, err, Clock.systemDefaultZone());
    }

    ConsoleLogger discardOut() {
        return new ConsoleLogger(Utils.DISCARDER, err, timestampClock);
    }

    ConsoleLogger discardErr() {
        return new ConsoleLogger(out, Utils.DISCARDER, timestampClock);
    }

    ConsoleLogger addTimestampsToOut() {
        return new ConsoleLogger(addTimestamps(out, timestampClock), err, timestampClock);
    }

    ConsoleLogger addTimestampsToErr() {
        return new ConsoleLogger(out, addTimestamps(err, timestampClock), timestampClock);
    }

    record ConsoleLoggerOutSink(PrintWriter sink) implements LoggerTrait {

        ConsoleLoggerOutSink {
            Objects.requireNonNull(sink);
        }
    }

    record ConsoleLoggerErrSink(PrintWriter sink) implements LoggerTrait {

        ConsoleLoggerErrSink {
            Objects.requireNonNull(sink);
        }
    }

    record ConsoleLoggerTimestampClock(Clock clock) implements LoggerTrait {

        ConsoleLoggerTimestampClock {
            Objects.requireNonNull(clock);
        }
    }

    private static Consumer<String> addTimestamps(Consumer<String> sink, Clock clock) {
        Objects.requireNonNull(sink);
        Objects.requireNonNull(clock);
        if (sink == Utils.DISCARDER || sink instanceof AddingTimestampsConsumer) {
            return sink;
        } else {
            return new AddingTimestampsConsumer(sink, clock);
        }
    }

    private record AddingTimestampsConsumer(Consumer<String> sink, Clock clock) implements Consumer<String> {

        AddingTimestampsConsumer {
            Objects.requireNonNull(sink);
            Objects.requireNonNull(clock);
        }

        @Override
        public void accept(String str) {
            var sb = new StringBuilder();
            var time = LocalTime.now(clock);
            sb.append('[').append(time.format(TIMESTAMP_FORMATTER)).append("] ");
            sb.append(str);
            str = sb.toString();
            sink.accept(str);
        }

        private final static DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    }

}
