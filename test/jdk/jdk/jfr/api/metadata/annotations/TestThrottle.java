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

package jdk.jfr.api.metadata.annotations;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.SettingControl;
import jdk.jfr.SettingDefinition;
import jdk.jfr.Threshold;
import jdk.jfr.Throttle;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.consumer.RecordingStream;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.metadata.annotations.TestThrottle
 */
public class TestThrottle {

    public static class UnthrottledEvent extends Event {
    }

    @Throttle("off")
    @Enabled(false)
    public static class ThrottledDisabledEvent extends Event {
    }

    @Throttle("off")
    public static class ThrottledOffEvent extends Event {
    }

    @Throttle("0/s")
    public static class ThrottledZeroRateEvent extends Event {
    }

    @Throttle("10000000/s")
    public static class ThrottledHighRateEvent extends Event {
    }

    @Throttle("off")
    @Threshold("5 h")
    public static class ThrottledThresholdedEvent extends Event {
    }

    @Throttle("50/s")
    public static class ThrottledNormalRateEvent extends Event {
        public int index;
    }

    static class TestSetting extends SettingControl {
        private boolean value;

        @Override
        public String combine(Set<String> values) {
            if (values.contains("true")) {
                return "true";
            }
            if (values.contains("false")) {
                return "false";
            }
            return "true";
        }

        @Override
        public void setValue(String text) {
            value = Boolean.parseBoolean(text);
        }

        @Override
        public String getValue() {
            return "" + value;
        }
    }

    @Throttle("10000000/s")
    public static class ThrottledUserdefinedEvent extends Event {
        @SettingDefinition
        public boolean test(TestSetting control) {
            return control.value;
        }
    }

    @Throttle("50/s")
    public static class ThrottledReuseEvent extends Event {
        public int index;
    }

    private static Instant startTime;

    public static void main(String[] args) throws Exception {
        startTime = determineMinimumTime();
        testUnthrottled(); // To ensure problem is specific to throttled events
        testThrottleDisabled();
        testThrottledOff();
        testThottleZeroRate();
        testThrottleHighRate();
        testThrottleThresholded();
        testThrottleNormalRate();
        testThrottleUserdefined();
    }

    private static void testUnthrottled() throws Exception {
        testEvent(UnthrottledEvent.class, true);
    }

    private static Instant determineMinimumTime() throws IOException {
        try (Recording r = new Recording()) {
            r.enable("jdk.JVMInformation");
            r.start();
            Path p = Path.of("start.jfr");
            r.dump(p);
            return RecordingFile.readAllEvents(p).get(0).getStartTime();
        }
    }

    private static void testThrottleDisabled() throws Exception {
        testEvent(ThrottledDisabledEvent.class, false);
    }

    private static void testThrottledOff() throws Exception {
        testEvent(ThrottledOffEvent.class, true);
    }

    private static void testThottleZeroRate() throws Exception {
        testEvent(ThrottledZeroRateEvent.class, false);
    }

    private static void testThrottleHighRate() throws Exception {
        testEvent(ThrottledHighRateEvent.class, true);
    }

    private static void testThrottleThresholded() throws Exception {
        testEvent(ThrottledThresholdedEvent.class, false);
    }

    private static void testThrottleNormalRate() throws Exception {
        try (RecordingStream rs = new RecordingStream()) {
            AtomicInteger lastIndex = new AtomicInteger();
            AtomicInteger throttled = new AtomicInteger();
            rs.onEvent(ThrottledNormalRateEvent.class.getName(), e -> {
                int index = e.getInt("index");
                if (lastIndex.get() + 1 != index) {
                    throttled.incrementAndGet();
                }
                lastIndex.set(index);
            });
            rs.startAsync();
            int index = 1;
            while (throttled.get() < 30) {
                ThrottledNormalRateEvent e = new ThrottledNormalRateEvent();
                e.index = index;
                e.commit();
                index++;
                Thread.sleep(3);
            }
        }
    }

    private static void testThrottleUserdefined() throws Exception {
        testThrottleUserdefined("false", "1000000/s", false);
        testThrottleUserdefined("true", "10000000/s", true);
        testThrottleUserdefined("true", "0/s", false);
        testThrottleUserdefined("true", "off", true);
        testThrottleUserdefined("false", "off", false);
    }

    private static void testThrottleUserdefined(String test, String throttle, boolean emit) throws Exception {
        String eventName = ThrottledUserdefinedEvent.class.getName();
        try (Recording r = new Recording()) {
            r.enable(eventName).with("test", test).with("throttle", throttle);
            r.start();

            ThrottledUserdefinedEvent e1 = new ThrottledUserdefinedEvent();
            e1.commit();

            ThrottledUserdefinedEvent e2 = new ThrottledUserdefinedEvent();
            e2.begin();
            e2.commit();

            ThrottledUserdefinedEvent e3 = new ThrottledUserdefinedEvent();
            e3.begin();
            e3.end();
            e3.commit();

            ThrottledUserdefinedEvent e4 = new ThrottledUserdefinedEvent();
            if (e4.shouldCommit()) {
                e4.commit();
            }
            assertShouldCommit(e4, emit);

            ThrottledUserdefinedEvent e5 = new ThrottledUserdefinedEvent();
            assertShouldCommit(e5, emit);
            if (e5.shouldCommit()) {
                e5.commit();
            }
            assertEvents(r, eventName, emit ? 5 : 0);
        }
    }

    @SuppressWarnings("unchecked")
    private static void testEvent(Class<? extends Event> eventClass, boolean shouldCommit) throws Exception {
        try (Recording r = new Recording()) {
            r.start();
            Constructor<Event> c = (Constructor<Event>) eventClass.getConstructor();
            for (int i = 0; i < 17; i++) {
                Event e = c.newInstance();
                if (i % 5 == 0) {
                    assertShouldCommit(e, shouldCommit);
                }
                e.commit();
                if (i % 3 == 0) {
                    assertShouldCommit(e, shouldCommit);
                }
            }
            for (int i = 0; i < 50; i++) {
                Event e = c.newInstance();
                e.begin();
                if (i % 5 == 0) {
                    assertShouldCommit(e, shouldCommit);
                }
                e.end();
                if (i % 3 == 0) {
                    assertShouldCommit(e, shouldCommit);
                }
                e.commit();
                if (i % 7 == 0) {
                    assertShouldCommit(e, shouldCommit);
                }
            }
            for (int i = 0; i < 11; i++) {
                Event e = c.newInstance();
                e.begin();
                e.commit();
                if (i % 7 == 0) {
                    assertShouldCommit(e, shouldCommit);
                }
            }
            if (shouldCommit) {
                assertEvents(r, eventClass.getName(), 17 + 50 + 11);
            }
        }
    }

    private static void assertEvents(Recording r, String name, int expected) throws Exception {
        int count = 0;
        r.stop();
        Duration d = Duration.between(r.getStartTime(), r.getStopTime());
        Path file = Path.of("dump.jfr");
        r.dump(file);
        for (RecordedEvent event : RecordingFile.readAllEvents(file)) {
            if (event.getEventType().getName().equals(name)) {
                count++;
            }
            if (event.getDuration().isNegative()) {
                System.out.println(event);
                throw new Exception("Unexpected negative duration");
            }
            if (event.getStartTime().isBefore(startTime)) {
                System.out.println(event);
                throw new Exception("Unexpected early start time");
            }
            if (event.getDuration().toMillis() > 2 * d.toMillis()) {
                System.out.println(event);
                throw new Exception("Duration exceed twice the length of the recording");
            }
        }
        if (count != expected) {
            throw new Exception("Expected " + expected + " " + name + " events, but found " + count);
        }
        Files.delete(file);
    }

    private static void assertShouldCommit(Event e, boolean expected) throws Exception {
        if (e.shouldCommit() != expected) {
            throw new Exception("Expected " + e.getClass() + "::shouldCommit() to return " + expected);
        }
    }
}
