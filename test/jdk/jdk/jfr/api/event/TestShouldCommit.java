/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.event;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.SettingControl;
import jdk.jfr.SettingDefinition;
import jdk.jfr.consumer.RecordingFile;

import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Asserts.assertFalse;

/**
 * @test
 * @summary Test jdk.jfr.Event::shouldCommit()
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.event.TestShouldCommit
 */
public class TestShouldCommit {

    private static class CatEvent extends Event {
    }

    private static class DogEvent extends Event {
    }

    private static class BirdEvent extends Event {
        public boolean isFlying;

        @SettingDefinition
        public boolean fly(FlySetting control) {
            return control.shouldFly() == isFlying;
        }
    }

    private static class FlySetting extends SettingControl {
        private boolean shouldFly;

        @Override
        public String combine(Set<String> settingValues) {
            for (String s : settingValues) {
                if ("true".equals(s)) {
                    return "true";
                }
            }
            return "false";
        }

        public boolean shouldFly() {
            return shouldFly;
        }

        @Override
        public void setValue(String settingValue) {
            shouldFly = "true".equals(settingValue);
        }

        @Override
        public String getValue() {
            return String.valueOf(shouldFly);
        }
    }

    public static void main(String[] args) throws Exception {
        testEnablement();
        testThreshold();
        testCustomSetting();
        testWithoutEnd();
        testCommit();
    }

    private static void testEnablement() throws Exception {
        DogEvent b = new DogEvent();
        assertFalse(b.shouldCommit(), "Expected false before recording is started");

        try (Recording r = new Recording()) {
            r.enable(CatEvent.class);
            r.disable(DogEvent.class);
            r.start();

            CatEvent c = new CatEvent();
            assertTrue(c.shouldCommit(), "Expected true for enabled event");

            DogEvent d = new DogEvent();
            assertFalse(d.shouldCommit(), "Expected false for disabled event");
        }

        CatEvent c = new CatEvent();
        assertFalse(c.shouldCommit(), "Expected false after recording is stopped");
    }

    private static void testThreshold() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(CatEvent.class).withThreshold(Duration.ofNanos(0));
            r.enable(DogEvent.class).withThreshold(Duration.ofDays(1));
            r.start();

            CatEvent c = new CatEvent();
            c.begin();
            Thread.sleep(1);
            c.end();
            assertTrue(c.shouldCommit(), "Expected true if above threshold");

            DogEvent d = new DogEvent();
            d.begin();
            Thread.sleep(1);
            d.end();
            assertFalse(d.shouldCommit(), "Expected false if below threshold");
        }
    }

    private static void testCustomSetting() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(BirdEvent.class).with("fly", "true");
            r.start();
            BirdEvent b1 = new BirdEvent();
            b1.isFlying = false;
            b1.begin();
            b1.end();
            assertFalse(b1.shouldCommit(), "Expected false if rejected by custom setting");

            BirdEvent b2 = new BirdEvent();
            b2.isFlying = true;
            b2.begin();
            b2.end();
            assertTrue(b2.shouldCommit(), "Expected true if accepted by custom setting");
        }
    }

    private static void testWithoutEnd() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(CatEvent.class).withThreshold(Duration.ofDays(0));
            r.enable(DogEvent.class).withThreshold(Duration.ofDays(1));
            r.start();

            CatEvent c = new CatEvent();
            c.begin();
            Thread.sleep(1);
            assertTrue(c.shouldCommit(), "Expected true when above threshold and end() not invoked");

            DogEvent d = new DogEvent();
            d.begin();
            Thread.sleep(1);
            assertFalse(d.shouldCommit(), "Expected false when below threshold and end() not invoked");
        }
    }

    private static void testCommit() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(CatEvent.class);
            r.start();
            CatEvent c = new CatEvent();
            c.begin();
            Thread.sleep(1);
            c.end();
            if (c.shouldCommit()) {
                c.commit();
            }
            r.stop();
            Path file = Path.of("dump.jfr");
            r.dump(file);
            boolean hasEvent = RecordingFile.readAllEvents(file).size() > 0;
            assertTrue(hasEvent, "Expected event when using commit() after shouldCommit()");
        }
    }
}
