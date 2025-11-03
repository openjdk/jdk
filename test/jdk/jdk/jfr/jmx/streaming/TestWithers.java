/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.jfr.jmx.streaming;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.management.MBeanServerConnection;

import jdk.jfr.Event;
import jdk.jfr.EventSettings;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Name;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.management.jfr.RemoteRecordingStream;

/**
 * @test
 * @requires vm.flagless
 * @summary Tests that event settings for a RemoteRecordingStream can be changed
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.jmx.streaming.TestWithers
 */
public class TestWithers {
    private static final Set<String> RESULT = Collections.synchronizedSet(new HashSet<>());

    @Name("AA")
    @StackTrace(false)
    static class A extends Event {
    }

    @Name("BB")
    @StackTrace(true)
    static class B extends Event {
    }

    @Name("CC")
    @Threshold("10 h")
    static class C extends Event {
    }

    @Name("DD")
    @Threshold("10 h")
    static class D extends Event {
    }

    @Name("EE")
    @StackTrace(false)
    static class E extends Event {
    }

    @Name("FF")
    @Period("10 h")
    static class F extends Event {
    }

    public static void main(String... args) throws Exception {
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        try (RemoteRecordingStream stream = new RemoteRecordingStream(conn)) {
            addCheck(stream, es -> es.withStackTrace(), "AA", TestWithers::hasStackTrace);
            addCheck(stream, es -> es.withoutStackTrace(), "BB", e -> !hasStackTrace(e));
            addCheck(stream, es -> es.withThreshold(Duration.ofMillis(0)), "CC", e -> true);
            addCheck(stream, es -> es.withoutThreshold(), "DD", e -> true);
            addCheck(stream, es -> es.with("stackTrace", "true"), "EE", TestWithers::hasStackTrace);
            addCheck(stream, es -> es.withPeriod(Duration.ofMillis(700)), "FF", e -> true);
            FlightRecorder.addPeriodicEvent(F.class, () -> {
                F f = new F();
                f.commit();
            });
            stream.onFlush(() -> {
                System.out.println(RESULT);
                if (RESULT.size() == 6) {
                    stream.close();
                }
            });

            stream.startAsync();
            A a = new A();
            a.commit();

            B b = new B();
            b.commit();

            C c = new C();
            c.commit();

            D d = new D();
            d.commit();

            E e = new E();
            e.commit();

            stream.awaitTermination();
        }
    }

    private static void addCheck(RemoteRecordingStream stream, Consumer<EventSettings> es, String eventName, Predicate<RecordedEvent> validator) {
        es.accept(stream.enable(eventName));
        stream.onEvent(eventName, e -> {
            System.out.println(e);
            if (validator.test(e)) {
                RESULT.add(eventName);
            }
        });
    }

    private static boolean hasStackTrace(RecordedEvent e) {
        RecordedStackTrace rs = e.getStackTrace();
        return rs != null && !rs.getFrames().isEmpty();
    }
}