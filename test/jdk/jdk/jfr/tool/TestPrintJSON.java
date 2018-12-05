/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.tool;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import jdk.jfr.Timespan;
import jdk.jfr.Timestamp;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;
import jdk.nashorn.api.scripting.JSObject;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @key jfr
 * @summary Tests print --json
 * @requires vm.hasJFR
 *
 * @library /test/lib /test/jdk
 * @modules jdk.scripting.nashorn
 *          jdk.jfr
 *
 * @run main/othervm jdk.jfr.tool.TestPrintJSON
 */
public class TestPrintJSON {

    public static void main(String... args) throws Throwable {

        Path recordingFile = ExecuteHelper.createProfilingRecording().toAbsolutePath();

        OutputAnalyzer output = ProcessTools.executeProcess("jfr", "print", "--json", "--stack-depth", "999", recordingFile.toString());
        String json = output.getStdout();

        // Parse JSON using Nashorn
        String statement = "var jsonObject = " + json;
        ScriptEngineManager factory = new ScriptEngineManager();
        ScriptEngine engine = factory.getEngineByName("nashorn");
        engine.eval(statement);
        JSObject o = (JSObject) engine.get("jsonObject");
        JSObject recording = (JSObject) o.getMember("recording");
        JSObject jsonEvents = (JSObject) recording.getMember("events");

        List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
        Collections.sort(events, (e1, e2) -> e1.getEndTime().compareTo(e2.getEndTime()));
        // Verify events are equal
        Iterator<RecordedEvent> it = events.iterator();

        for (Object jsonEvent : jsonEvents.values()) {
            RecordedEvent recordedEvent = it.next();
            String typeName = recordedEvent.getEventType().getName();
            Asserts.assertEquals(typeName, ((JSObject) jsonEvent).getMember("type").toString());
            assertEquals(jsonEvent, recordedEvent);
        }
        Asserts.assertFalse(events.size() != jsonEvents.values().size(), "Incorrect number of events");
    }

    private static void assertEquals(Object jsonObject, Object jfrObject) throws Exception {
        // Check object
        if (jfrObject instanceof RecordedObject) {
            JSObject values = (JSObject) ((JSObject) jsonObject).getMember("values");
            RecordedObject recObject = (RecordedObject) jfrObject;
            Asserts.assertEquals(values.values().size(), recObject.getFields().size());
            for (ValueDescriptor v : recObject.getFields()) {
                String name = v.getName();
                Object jsonValue = values.getMember(name);
                Object expectedValue = recObject.getValue(name);
                if (v.getAnnotation(Timestamp.class) != null) {
                    // Make instant of OffsetDateTime
                    jsonValue = OffsetDateTime.parse("" + jsonValue).toInstant().toString();
                    expectedValue = recObject.getInstant(name);
                }
                if (v.getAnnotation(Timespan.class) != null) {
                    expectedValue = recObject.getDuration(name);
                }
                assertEquals(jsonValue, expectedValue);
                return;
            }
        }
        // Check array
        if (jfrObject != null && jfrObject.getClass().isArray()) {
            Object[] jfrArray = (Object[]) jfrObject;
            JSObject jsArray = (JSObject) jsonObject;
            for (int i = 0; i < jfrArray.length; i++) {
                assertEquals(jsArray.getSlot(i), jfrArray[i]);
            }
            return;
        }
        String jsonText = String.valueOf(jsonObject);
        // Double.NaN / Double.Inifinity is not supported by JSON format,
        // use null
        if (jfrObject instanceof Double) {
            double expected = ((Double) jfrObject);
            if (Double.isInfinite(expected) || Double.isNaN(expected)) {
                Asserts.assertEquals("null", jsonText);
                return;
            }
            double value = Double.parseDouble(jsonText);
            Asserts.assertEquals(expected, value);
            return;
        }
        // Float.NaN / Float.Inifinity is not supported by JSON format,
        // use null
        if (jfrObject instanceof Float) {
            float expected = ((Float) jfrObject);
            if (Float.isInfinite(expected) || Float.isNaN(expected)) {
                Asserts.assertEquals("null", jsonText);
                return;
            }
            float value = Float.parseFloat(jsonText);
            Asserts.assertEquals(expected, value);
            return;
        }
        if (jfrObject instanceof Integer) {
            Integer expected = ((Integer) jfrObject);
            double value = Double.parseDouble(jsonText);
            Asserts.assertEquals(expected.doubleValue(), value);
            return;
        }
        if (jfrObject instanceof Long) {
            Long expected = ((Long) jfrObject);
            double value = Double.parseDouble(jsonText);
            Asserts.assertEquals(expected.doubleValue(), value);
            return;
        }

        String jfrText = String.valueOf(jfrObject);
        Asserts.assertEquals(jfrText, jsonText, "Primitive values don't match. JSON = " + jsonText);
    }
}
