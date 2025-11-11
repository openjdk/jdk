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

/**
 * @test
 * @bug 8353835
 * @summary Basic test for JFR FinalFieldMutation event
 * @requires vm.hasJFR
 * @modules jdk.jfr/jdk.jfr.events
 * @run junit FinalFieldMutationEventTest
 * @run junit/othervm --illegal-final-field-mutation=allow FinalFieldMutationEventTest
 * @run junit/othervm --illegal-final-field-mutation=warn FinalFieldMutationEventTest
 * @run junit/othervm --illegal-final-field-mutation=debug FinalFieldMutationEventTest
 * @run junit/othervm --illegal-final-field-mutation=deny FinalFieldMutationEventTest
 */

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.events.FinalFieldMutationEvent;
import jdk.jfr.events.StackFilter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinalFieldMutationEventTest {
    private static final String EVENT_NAME = "jdk.FinalFieldMutation";

    /**
     * Test class with final field.
     */
    private static class C {
        final int value;
        C(int value) {
            this.value = value;
        }
    }

    /**
     * Test jdk.FinalFieldMutation is recorded when mutating a final field.
     */
    @Test
    void testFieldSet() throws Exception {
        Field field = C.class.getDeclaredField("value");
        field.setAccessible(true);

        try (Recording recording = new Recording()) {
            recording.enable(EVENT_NAME).withStackTrace();

            boolean mutated = false;
            recording.start();
            try {
                var obj = new C(100);
                try {
                    field.setInt(obj, 200);
                    mutated = true;
                } catch (IllegalAccessException e) {
                    // denied
                }
            } finally {
                recording.stop();
            }

            // FinalFieldMutation event should be recorded if field mutated
            List<RecordedEvent> events = find(recording, EVENT_NAME);
            System.err.println(events);
            if (mutated) {
                assertEquals(1, events.size(), "1 event expected");
                checkEvent(events.get(0), field, "FinalFieldMutationEventTest::testFieldSet");
            } else {
                assertEquals(0, events.size(), "No events expected");
            }
        }
    }

    /**
     * Test jdk.FinalFieldMutation is recorded when unreflecting a field field for mutation.
     */
    @Test
    void testUnreflectSetter() throws Exception {
        Field field = C.class.getDeclaredField("value");
        field.setAccessible(true);

        try (Recording recording = new Recording()) {
            recording.enable(EVENT_NAME).withStackTrace();

            boolean unreflected = false;
            recording.start();
            try {
                MethodHandles.lookup().unreflectSetter(field);
                unreflected = true;
            } catch (IllegalAccessException e) {
                // denied
            } finally {
                recording.stop();
            }

            // FinalFieldMutation event should be recorded if field unreflected for set
            List<RecordedEvent> events = find(recording, EVENT_NAME);
            System.err.println(events);
            if (unreflected) {
                assertEquals(1, events.size(), "1 event expected");
                checkEvent(events.get(0), field, "FinalFieldMutationEventTest::testUnreflectSetter");
            } else {
                assertEquals(0, events.size(), "No events expected");
            }
        }
    }

    /**
     * Test that a FinalFieldMutationEvent event has the declaringClass and fieldName of
     * the given Field, and the expected top frame.
     */
    private void checkEvent(RecordedEvent e, Field f, String expectedTopFrame) {
        RecordedClass clazz = e.getClass("declaringClass");
        assertNotNull(clazz);
        assertEquals(f.getDeclaringClass().getName(), clazz.getName());
        assertEquals(f.getName(), e.getString("fieldName"));

        // check the top-frame of the stack trace
        RecordedMethod m = e.getStackTrace().getFrames().getFirst().getMethod();
        assertEquals(expectedTopFrame, m.getType().getName() + "::" + m.getName());
    }

    /**
     * Tests that FinalFieldMutationEvent's stack filter value names classes/methods that
     * exist. This will help detect stale values when the implementation is refactored.
     */
    @Test
    void testFinalFieldMutationEventStackFilter() throws Exception {
        String[] filters = FinalFieldMutationEvent.class.getAnnotation(StackFilter.class).value();
        for (String filter : filters) {
            String[] classAndMethod = filter.split("::");
            String cn = classAndMethod[0];

            // throws if class not found
            Class<?> clazz = Class.forName(cn);

            // if the filter has a method name then check a method of that name exists
            if (classAndMethod.length > 1) {
                String mn = classAndMethod[1];
                Method method = Stream.of(clazz.getDeclaredMethods())
                        .filter(m -> m.getName().equals(mn))
                        .findFirst()
                        .orElse(null);
                assertNotNull(method, cn + "::" + mn + " not found");
            }
        }
    }

    /**
     * Returns the list of events in the given recording with the given name.
     */
    private List<RecordedEvent> find(Recording recording, String name) throws Exception {
        Path recordingFile = recordingFile(recording);
        return RecordingFile.readAllEvents(recordingFile)
                .stream()
                .filter(e -> e.getEventType().getName().equals(name))
                .toList();
    }

    /**
     * Return the file path to the recording file.
     */
    private Path recordingFile(Recording recording) throws Exception {
        Path recordingFile = recording.getDestination();
        if (recordingFile == null) {
            ProcessHandle h = ProcessHandle.current();
            recordingFile = Path.of("recording-" + recording.getId() + "-pid" + h.pid() + ".jfr");
            recording.dump(recordingFile);
        }
        return recordingFile;
    }
}
