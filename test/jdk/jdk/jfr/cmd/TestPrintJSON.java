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

package jdk.jfr.cmd;

import java.nio.file.Path;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;
import jdk.nashorn.api.scripting.JSObject;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;

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
 * @run main/othervm jdk.jfr.cmd.TestPrintJSON
 */
public class TestPrintJSON {

    public static void main(String... args) throws Exception {

        Path recordingFile = ExecuteHelper.createProfilingRecording().toAbsolutePath();

        OutputAnalyzer output = ExecuteHelper.run("print", "--json", recordingFile.toString());
        String json = output.getStdout();

        // Parse JSON using Nashorn
        String statement = "var jsonObject = " + json;
        ScriptEngineManager factory = new ScriptEngineManager();
        ScriptEngine engine = factory.getEngineByName("nashorn");
        engine.eval(statement);
        JSObject o = (JSObject) engine.get("jsonObject");
        JSObject recording = (JSObject) o.getMember("recording");
        JSObject events = (JSObject) recording.getMember("events");

        // Verify events are equal
        try (RecordingFile rf = new RecordingFile(recordingFile)) {
            for (Object jsonEvent : events.values()) {
                RecordedEvent recordedEvent = rf.readEvent();
                double typeId = recordedEvent.getEventType().getId();
                String startTime = recordedEvent.getStartTime().toString();
                String duration = recordedEvent.getDuration().toString();
                Asserts.assertEquals(typeId, ((Number) ((JSObject) jsonEvent).getMember("typeId")).doubleValue());
                Asserts.assertEquals(startTime, ((JSObject) jsonEvent).getMember("startTime"));
                Asserts.assertEquals(duration, ((JSObject) jsonEvent).getMember("duration"));
                assertEquals(jsonEvent, recordedEvent);
            }
            Asserts.assertFalse(rf.hasMoreEvents(), "Incorrect number of events");
        }
    }

    private static void assertEquals(Object jsonObject, Object jfrObject) throws Exception {
        // Check object
        if (jfrObject instanceof RecordedObject) {
            JSObject values = (JSObject) ((JSObject) jsonObject).getMember("values");
            RecordedObject recObject = (RecordedObject) jfrObject;
            Asserts.assertEquals(values.values().size(), recObject.getFields().size());
            for (ValueDescriptor v : recObject.getFields()) {
                String name = v.getName();
                assertEquals(values.getMember(name), recObject.getValue(name));
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
