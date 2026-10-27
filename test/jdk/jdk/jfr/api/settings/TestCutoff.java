/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.settings;

import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.FlightRecorder;
import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Tests cutoff setting
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.api.settings.TestCutoff
 */
public class TestCutoff {
    private static final String OLD_OBJECT_SAMPLE = "jdk.OldObjectSample";
    private static final String ACTIVE_SETTING = "jdk.ActiveSetting";

    public static void main(String[] args) throws Exception {
        testMultipleZeroCutoff();
    }

    public static void testMultipleZeroCutoff() throws Exception {
        try (Recording r1 = new Recording()) {
            r1.enable(OLD_OBJECT_SAMPLE).with("cutoff", "0 ns");
            r1.start();
            try (Recording r2 = new Recording()) {
                // Need to use "0 ms" instead of "0 ns" so SettingControl::combine is invoked
                r2.enable(OLD_OBJECT_SAMPLE).with("cutoff", "0 ms");
                r2.enable(ACTIVE_SETTING);
                r2.start();
                r2.stop();
                RecordedEvent event = findActiveSettingEvent(r2);
                String value = event.getString("value");
                if (!(value.equals("0 ms") || value.equals("0 ns"))) {
                    throw new Exception("Unexpected combined cutoff setting's value '" + value + "'");
                }
            }
        }
    }

    private static RecordedEvent findActiveSettingEvent(Recording recording) throws Exception {
        long activeSetting = findEventType(ACTIVE_SETTING).getId();
        long oldObjectSample = findEventType(OLD_OBJECT_SAMPLE).getId();
        List<RecordedEvent> events = Events.fromRecording(recording);
        for (RecordedEvent e : events) {
            if (e.getEventType().getId() == activeSetting) {
                String name = e.getString("name");
                long id = e.getLong("id");
                if (name.equals("cutoff") && id == oldObjectSample) {
                    return e;
                }
            }
        }
        throw new Exception("Could not find active settings event for cutoff");
    }

    private static EventType findEventType(String name) throws Exception {
        for (EventType eventType : FlightRecorder.getFlightRecorder().getEventTypes()) {
            if (eventType.getName().equals(name)) {
                return eventType;
            }
        }
        throw new Exception("Could not find event type named " + name);
    }
}
