/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Test jdk.jfr.Event::shouldCommit()
 * @key jfr
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
        testShouldCommitWithoutEnd();
        testCommitAfterShouldCommit();
    }

    private static void testEnablement() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(CatEvent.class);
            r.disable(DogEvent.class);
            r.start();
            // Test enabled event
            CatEvent c = new CatEvent();
            if (!c.shouldCommit()) {
                throw new Exception("Expected shouldCommit to be true");
            }
            // Test disabled event
            DogEvent d = new DogEvent();
            if (d.shouldCommit()) {
                throw new Exception("Expected shouldCommit to be false");
            }
        }
    }

    private static void testThreshold() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(CatEvent.class).withThreshold(Duration.ofNanos(0));
            r.enable(DogEvent.class).withThreshold(Duration.ofDays(1));
            r.start();
            // Test event above threshold
            CatEvent c = new CatEvent();
            c.begin();
            Thread.sleep(1);
            c.end();
            if (!c.shouldCommit()) {
                throw new Exception("Expected shouldCommit to be true");
            }
            // Test event below threshold
            DogEvent d = new DogEvent();
            d.begin();
            Thread.sleep(1);
            d.end();
            if (d.shouldCommit()) {
                throw new Exception("Expected shouldCommit to be false");
            }
        }
    }

    private static void testCustomSetting() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(BirdEvent.class).with("fly", "true");
            r.start();
            // Test rejection by custom setting
            BirdEvent b1 = new BirdEvent();
            b1.isFlying = false;
            b1.begin();
            b1.end();
            if (b1.shouldCommit()) {
                throw new Exception("Expected shouldCommit to be false");
            }
            // Test acceptance by custom setting
            BirdEvent b2 = new BirdEvent();
            b2.isFlying = true;
            b2.begin();
            b2.end();
            if (!b2.shouldCommit()) {
                throw new Exception("Expected shouldCommit to be true");
            }
        }
    }

    private static void testShouldCommitWithoutEnd() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(CatEvent.class).withThreshold(Duration.ofDays(0));
            r.enable(DogEvent.class).withThreshold(Duration.ofDays(1));
            r.start();
            // Test above threshold
            CatEvent c = new CatEvent();
            c.begin();
            Thread.sleep(1);
            if (!c.shouldCommit()) {
                throw new Exception("Expected shouldCommit to be true");
            }
            // Test below threshold
            DogEvent d = new DogEvent();
            d.begin();
            Thread.sleep(1);
            if (d.shouldCommit()) {
                throw new Exception("Expected shouldCommit to be false");
            }
        }
    }

    private static void testCommitAfterShouldCommit() throws Exception {
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
            if (RecordingFile.readAllEvents(file).isEmpty()) {
                throw new Exception("Expected event");
            }
        }
    }
}
