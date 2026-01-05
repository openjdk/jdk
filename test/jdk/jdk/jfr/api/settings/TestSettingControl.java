/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.SettingDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/**
 * @test
 * @summary Tests that methods on all SettingControls have expected behavior.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.api.settings.TestSettingControl
 */
public class TestSettingControl {
    record SettingTest(String setting, String defaultValue, String event, List<String> exampleValues) {
        public String eventSettingName() {
            return event + "#" + setting;
        }
    }

    // Example values should be listed in precedence order with the lowest precedence first.
    static List<SettingTest> SETTING_TESTS = List.of(
        new SettingTest("enabled", "false", "jdk.JavaMonitorWait",List.of("false", "true")),
        new SettingTest("stackTrace", "true", "jdk.JavaMonitorWait", List.of("false", "true")),
        new SettingTest("threshold", "0 ns", "jdk.JavaMonitorWait", List.of("infinity", "10 ms", "0 ns")),
        new SettingTest("level", "forRemoval", "jdk.DeprecatedInvocation", List.of("off", "forRemoval")),
        new SettingTest("period", "everyChunk", "jdk.ExceptionStatistics", List.of("everyChunk", "60 s", "1 s")),
        new SettingTest("cutoff", "infinity", "jdk.OldObjectSample", List.of("0 ms", "1 s", "infinity")),
        new SettingTest("throttle", "off", "jdk.ObjectAllocationSample", List.of("off", "100/s", "10/ms")),
        new SettingTest("filter", "", "jdk.MethodTrace", List.of("", "foo.bar::Baz", "com.example.Test;foo.bar::Baz"))
    );

    public static void main(String... args) throws Exception {
        testTesting();
        testDefault();
        testDefaultWithInvalid();
        testPrecedence();
        testPrecedenceWithInvalid();
    }

    // Ensure that all known SettingControl/types are tested at least once
    private static void testTesting() throws Exception {
        Set<String> foundSettings = new HashSet<>();
        for (EventType eventType : allEventTypes()) {
            for (SettingDescriptor s : eventType.getSettingDescriptors()) {
                foundSettings.add(s.getName());
            }
        }
        for (SettingTest st : SETTING_TESTS) {
            foundSettings.remove(st.setting());
        }
        if (!foundSettings.isEmpty()) {
            throw new Exception("All event SettingControls should be tested. Missing test for " + foundSettings);
        }
    }

    // Ensure that the default values for all SettingControls are correct
    private static void testDefault() throws Exception {
        for (SettingTest settingTest : SETTING_TESTS) {
            SettingDescriptor s = findSettingDescriptor(settingTest);
            if (!settingTest.defaultValue().equals(s.getDefaultValue())) {
                String message = "Incorrect default value " + quote(s.getDefaultValue());
                message += " for setting " + settingTest.eventSettingName() + ". ";
                message += "Expected " + quote(settingTest.defaultValue());
                throw new Exception(message);
            }
        }
    }

    // Ensure that default settings are used if an invalid setting is specified.
    private static void testDefaultWithInvalid() throws Exception {
        Map<String, String> settings = createEnabledMap();
        for (SettingTest settingTest : SETTING_TESTS) {
            settings.put(settingTest.eventSettingName(), "%#&2672g");
        }
        Map<String, String> result = check("testDefaultWithInvalid", List.of(settings));
        for (var entry : new ArrayList<>(result.entrySet())) {
            String key = entry.getKey();
            String removed = result.remove(key);
            if (removed == null) {
                throw new Exception("Expected setting " + quote(key) + " to exist");
            }
            String setting = key.substring(key.indexOf("#") + 1);
            SettingTest st = findSettingTest(setting);
            if (st == null) {
                throw new Exception("Found unexpected setting " + quote(key));
            }
            if (!removed.equals(st.defaultValue())) {
                String message = "Expected default value " + quote(st.defaultValue());
                message += " for setting " + quote(setting) + " when";
                message += " an invalid settings value was specified";
                throw new Exception(message);
            }
        }
        if (!result.isEmpty()) {
            throw new Exception("Found unexpected setting when testing preserved default");
        }
    }

    // Only enabled events will use settings
    private static Map<String, String> createEnabledMap() {
        Map<String, String> settings = new TreeMap<>();
        for (SettingTest settingTest : SETTING_TESTS) {
            settings.put(settingTest.event + "#enabled", "true");
        }
        return settings;
    }

    // Ensure that precedence are respected when multiple settings are specified
    private static void testPrecedence() throws Exception {
        testPrecedence("testPrecedence");
    }

    // Ensure that precedence are respected when an incorrect setting is used
    private static void testPrecedenceWithInvalid() throws Exception {
        testPrecedence("testPrecedenceWithInvalid");
    }

    // * * * HELPER METHODS * * *

    private static void testPrecedence(String testName) throws Exception {
        List<Map<String, String>> settingsList = new ArrayList<>();
        int maxExamples = 0;
        for (SettingTest t : SETTING_TESTS) {
            maxExamples = Math.max(t.exampleValues().size(), maxExamples);
        }
        for (int i = 0; i < maxExamples; i++) {
            Map<String, String> settings = createEnabledMap();
            for (SettingTest settingTest : SETTING_TESTS) {
                List<String> examples = settingTest.exampleValues();
                String name = settingTest.eventSettingName();
                if (i < examples.size()) {
                    settings.put(name, examples.get(i));
                }
                // Insert the invalid setting first
                if (testName.contains("Invalid") && i == 0) {
                    settings.put(name, "%#&2672g");
                }
            }
            settingsList.add(settings);
        }
        Map<String, String> results = check(testName, settingsList);
        Map<String, String> reversed = check(testName + "-reversed", settingsList.reversed());
        if (!reversed.equals(results)) {
            throw new Exception("Active settings should not depend on the order event settings are applied");
        }
        for (SettingTest t : SETTING_TESTS) {
            String expected = t.exampleValues().get(t.exampleValues().size() - 1);
            String found = results.get(t.eventSettingName());
            if (!expected.equals(found)) {
                throw new Exception("Expected " + expected + " to be used with setting " + quote(t.setting()) + ", not " + quote(found));
            }
        }
    }

    private static Map<String, String> check(String testName, List<Map<String, String>> settingsList) throws Exception {
        System.out.println("*** Check for " + testName + " ****");
        System.out.println("Input:");
        int index = 0;
        for (var settings : settingsList) {
            System.out.println("Setting[" + index + "] = {");
            for (var e : settings.entrySet()) {
                System.out.println("  " + e.getKey() + "=" + e.getValue());
            }
            System.out.println("}");
            index++;
        }
        int settingsCount = settingsList.size();

        // Start a recording for each settings
        List<Recording> recordings = new ArrayList<>();
        for (int i = 0; i < settingsCount; i++) {
            Recording r = new Recording();
            Map<String, String> settings = settingsList.get(i);
            settings.put("jdk.ActiveSetting#enabled", "true");
            r.setSettings(settings);
            r.start();
            recordings.add(r);
        }

        // Stop all recordings
        for (Recording r : recordings) {
            r.stop();
        }

        // Dump the innermost recording
        Path p = Path.of("recording.jfr");
        Recording inner = recordings.get(settingsCount - 1);
        inner.dump(p);

        // Close all recordings
        for (Recording r : recordings) {
            r.close();
        }
        System.out.println("Result:");
        Map<String, String> r = lastSettings(p);
        for (var e : r.entrySet()) {
            System.out.println(e.getKey() + "=" + e.getValue());
        }
        System.out.println("*************");
        System.out.println();
        Files.delete(p);
        return r;
    }

    private static SettingTest findSettingTest(String name) throws Exception {
        for (SettingTest settingTest : SETTING_TESTS) {
            if (name.equals(settingTest.setting())) {
                return settingTest;
            }
        }
        return null;
    }

    private static Map<String, String> lastSettings(Path p) throws Exception {
        List<RecordedEvent> events = RecordingFile.readAllEvents(p);
        Instant timestamp = findLastActiveSetting(events);
        Map<String, String> lastInnerMostSettings = new HashMap<>();
        for (SettingTest t : SETTING_TESTS) {
            long id = eventTypeNameToId(t.event());
            for (RecordedEvent event : events) {
                if (event.getEventType().getName().equals("jdk.ActiveSetting")) {
                    if (event.getStartTime().equals(timestamp) && id == event.getLong("id")) {
                        String name = event.getString("name");
                        String value = event.getString("value");
                        if (t.setting.equals(name)) {
                            String fullName = t.event() + "#" + name;
                            String previous = lastInnerMostSettings.put(fullName, value);
                            if (previous != null) {
                                throw new Exception("Expected only one ActiveSetting event per event setting");
                            }
                        }
                    }
                }
            }
        }
        return lastInnerMostSettings;
    }

    private static Instant findLastActiveSetting(List<RecordedEvent> events) {
        Instant lastTimestamp = null;
        for (RecordedEvent event : events) {
            if (event.getEventType().getName().equals("jdk.ActiveSetting")) {
                Instant t = event.getStartTime();
                if (lastTimestamp == null || t.isBefore(lastTimestamp)) {
                    lastTimestamp = t;
                }
            }
        }
        return lastTimestamp;
    }

    private static long eventTypeNameToId(String name) throws Exception {
        for (EventType eventType : allEventTypes()) {
            if (eventType.getName().equals(name)) {
                return eventType.getId();
            }
        }
        throw new Exception("Could not find event type with name " + name);
    }

    private static SettingDescriptor findSettingDescriptor(SettingTest settingTest) throws Exception {
        for (EventType eventType : allEventTypes()) {
            if (eventType.getName().equals(settingTest.event())) {
                for (SettingDescriptor s : eventType.getSettingDescriptors()) {
                    if (settingTest.setting().equals(s.getName())) {
                        return s;
                    }
                }
            }
        }
        throw new Exception("Could not find setting with name " + settingTest.event() + "#" + settingTest.setting());
    }

    private static List<EventType> allEventTypes() {
        return FlightRecorder.getFlightRecorder().getEventTypes();
    }

    private static String quote(String text) {
        return "'" + text + "'";
    }
}
