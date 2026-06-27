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

package jdk.jfr.jvm;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;

import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm --enable-final-field-mutation=ALL-UNNAMED jdk.jfr.jvm.TestJdkEpochThrottle
 */
public class TestJdkEpochThrottle {

    /**
     * Test class with final fields.
     */
    static class TestClass {
        final int value0;
        final int value1;
        final int value2;
        TestClass(int value0, int value1, int value2) {
            this.value0 = value0;
            this.value1 = value1;
            this.value2 = value2;
        }
    }

    private static String EVENT_NAME = EventNames.FinalFieldMutation;

    public static void main(String... args) throws Exception {
        testFieldSet();
    }

    /**
     * Normally, a FinalFieldMutation event is sent on every mutation of a final field.
     * With JFR epoch throttling in the JDK applied, we control event emission to
     * only happen once per mutated final field, per epoch.
     */
    private static void testFieldSet() throws Exception {
        try (Recording firstRecording = new Recording()) {
            firstRecording.enable(EVENT_NAME).withStackTrace();
            firstRecording.start();
            // Epoch 1.
            var obj = new TestClass(100, 100, 100);
            mutateFinalFields(obj);
            Recording secondRecording = new Recording();
            secondRecording.start();
            // Epoch 2.
            mutateFinalFields(obj);
            secondRecording.stop();
            // Epoch 3.
            mutateFinalFields(obj);
            firstRecording.stop();
            // Epoch 4.
            validate(secondRecording, 3);
            secondRecording.close();
            validate(firstRecording, 9);
        }
    }

    /**
     * Mutate each final field twice using distinct Field instances.
     * Because epoch throttling is applied to final field mutation events,
     * only the initial mutation of each final field per epoch should result in a FinalFieldMutation event.
     * I.e, out of 6 final field mutations, we only see 3 FinalFieldMutation events per epoch,
     * one event per field.
     */
    private static void mutateFinalFields(TestClass obj) throws Exception {
        for (int i = 0; i < 6; ++i) {
            getTestClassField("value" + i % 3).setInt(obj, 200);
        }
    }

    private static Field getTestClassField(String fieldName) throws Exception {
        Field field = TestClass.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    private static void validate(Recording recording, int expectedEvents) throws Exception {
        List<RecordedEvent> events = Events.fromRecording(recording);
        Asserts.assertEquals(expectedEvents, events.size(), "Unexpected number of events.");
        for (RecordedEvent event : events) {
            Asserts.assertTrue(event.getEventType().getName().equals(EVENT_NAME), "invalid event type");
            Asserts.assertTrue(event.getClass("declaringClass").getName().equals(TestClass.class.getName()), "invalid declaring class");
            Asserts.assertTrue(event.getString("fieldName").startsWith("value"), "invalid field name");
        }
    }
}
