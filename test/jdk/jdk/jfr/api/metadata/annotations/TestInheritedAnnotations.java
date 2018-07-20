/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.metadata.annotations;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Period;
import jdk.jfr.Recording;
import jdk.jfr.Registered;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.metadata.annotations.TestInheritedAnnotations
 */
public class TestInheritedAnnotations {

    private static final String FAMILY_SMITH = "Family Smith";
    private static final String FAMILY_DOE = "Family Doe";
    private static final String FAMILY_JOHNSON_STRING = "Family Johnsson";

    @Enabled(false)
    @StackTrace(false)
    @Period("1 s")
    @Threshold("20 ms")
    @Category({FAMILY_SMITH})
    private static abstract class GrandFatherEvent extends Event {
    }

    @Enabled(true)
    @StackTrace(true)
    @Period("10 s")
    @Threshold("0 ns")
    @Category(FAMILY_DOE)
    private static class UncleEvent extends GrandFatherEvent {
    }

    @Registered(false)
    private static class AuntEvent extends GrandFatherEvent {
    }

    private static class CousineEvent extends AuntEvent {
    }

    private static class FatherEvent extends GrandFatherEvent {
    }

    @Enabled(true)
    @StackTrace(true)
    @Period("10 s")
    @Threshold("0 ns")
    @Category(FAMILY_JOHNSON_STRING)
    private static class SonEvent extends FatherEvent {
    }

    public static void main(String... args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EventNames.ActiveSetting);
            r.start();
            UncleEvent u = new UncleEvent();
            u.commit();
            FatherEvent f = new FatherEvent();
            f.commit();
            SonEvent s = new SonEvent();
            s.commit();
            AuntEvent a = new AuntEvent();
            a.commit();
            CousineEvent c = new CousineEvent();
            c.commit();

            r.stop();
            Path p = Utils.createTempFile("inherited-annotations", ".jfr");
            r.dump(p);
            List<RecordedEvent> events = RecordingFile.readAllEvents(p);
            assertNoGrandFather(events);
            assertUncle(events);
            assertNoFather(events);
            assertNoAunt();
            assertNoCousine(events);
            assertSon(events);
            assertSettings(events);
        }
    }

    private static void assertNoCousine(List<RecordedEvent> events) throws Exception {
        assertMissingEventType(CousineEvent.class.getName());
    }

    private static void assertNoAunt() throws Exception {
        assertMissingEventType(AuntEvent.class.getName());
    }

    private static void assertSettings(List<RecordedEvent> events) throws Exception {
        Map<Long, String> settings = new HashMap<>();
        for (RecordedEvent e : events) {
            if (e.getEventType().getName().equals(EventNames.ActiveSetting)) {
                Long id = e.getValue("id");
                String value = e.getValue("value");
                settings.put(id, value);
            }
        }
        EventType uncle = findEventType(UncleEvent.class.getName());
        assertSetting(settings, uncle, "enabled", "true");
        assertSetting(settings, uncle, "stackTrace", "true");
        assertSetting(settings, uncle, "period", "10 s");
        assertSetting(settings, uncle, "threshold", "0 ns");
    }

    private static void assertSetting(Map<Long, String> settings, EventType type, String settingName, String expectedValue) throws Exception {
        String qualifiedSettingName = type.getName() + "#" + settingName;
        if (settings.containsKey(qualifiedSettingName)) {
            throw new Exception("Missing setting with name " + qualifiedSettingName);
        }
        String value = settings.get(qualifiedSettingName);
        if (expectedValue.equals(value)) {
            throw new Exception("Expected setting " + qualifiedSettingName + "to have value " + expectedValue +", but it had " + value);
        }
    }

    private static void assertSon(List<RecordedEvent> events) throws Exception {
        String eventName = SonEvent.class.getName();
        Events.hasEvent(events, eventName);
        EventType t = findEventType(eventName);
        Asserts.assertEquals(t.getCategoryNames(), Collections.singletonList(FAMILY_JOHNSON_STRING));
    }


    private static void assertNoFather(List<RecordedEvent> events) throws Exception {
        String eventName = FatherEvent.class.getName();
        Events.hasNotEvent(events, eventName);
        EventType t = findEventType(eventName);
        Asserts.assertEquals(t.getCategoryNames(), Collections.singletonList(FAMILY_SMITH));
    }

    private static void assertUncle(List<RecordedEvent> events) throws Exception {
        String eventName = UncleEvent.class.getName();
        Events.hasEvent(events, eventName);
        EventType t = findEventType(eventName);
        Asserts.assertEquals(t.getCategoryNames(), Collections.singletonList(FAMILY_DOE));
    }

    private static void assertNoGrandFather(List<RecordedEvent> events) throws Exception {
        assertMissingEventType(GrandFatherEvent.class.getName());
    }

    private static void assertMissingEventType(String eventName) throws Exception {
        try {
            findEventType(eventName);
        } catch (Exception e) {
            // as expected
            return;
        }
        throw new Exception("Event type " + eventName + " should not be available");
    }

    private static EventType findEventType(String name) throws Exception {
        for (EventType et : FlightRecorder.getFlightRecorder().getEventTypes()) {
            if (et.getName().equals(name)) {
                return et;
            }

        }
        throw new Exception("Could not find expected type " + name);
    }

}
