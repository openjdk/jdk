/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.event.runtime;

import java.util.List;
import java.util.ArrayList;

import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.internal.test.DeprecatedMethods;
import jdk.jfr.internal.test.DeprecatedThing;
import jdk.jfr.Recording;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Asserts.assertNull;
import static jdk.test.lib.Asserts.assertNotNull;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @library /test/lib
 *
 * @run main/othervm/timeout=300 -XX:StartFlightRecording:settings=none,+jdk.DeprecatedInvocation#enabled=true
 *      jdk.jfr.event.runtime.TestDeprecatedEvent Default

 * @run main/othervm/timeout=300 -Xint -XX:+UseInterpreter -XX:StartFlightRecording:settings=none,+jdk.DeprecatedInvocation#enabled=true
 *      jdk.jfr.event.runtime.TestDeprecatedEvent Interpreter
 *
 * @run main/othervm/timeout=300 -Xcomp -XX:-UseInterpreter -XX:StartFlightRecording:settings=none,+jdk.DeprecatedInvocation#enabled=true
 *      jdk.jfr.event.runtime.TestDeprecatedEvent Compiler
 *
 * @run main/othervm/timeout=300 -Xcomp -XX:TieredStopAtLevel=1 -XX:-UseInterpreter -XX:StartFlightRecording:settings=none,+jdk.DeprecatedInvocation#enabled=true
 *      jdk.jfr.event.runtime.TestDeprecatedEvent C1
 *
 * @run main/othervm/timeout=300 -Xcomp -XX:TieredStopAtLevel=4 -XX:-TieredCompilation -XX:-UseInterpreter -XX:StartFlightRecording:settings=none,+jdk.DeprecatedInvocation#enabled=true
 *      jdk.jfr.event.runtime.TestDeprecatedEvent C2
 *
 */
public class TestDeprecatedEvent {
/*
 *
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *      jdk.jfr.event.runtime.TestDeprecatedEvent JVMCI
 *
 */
    public static String EVENT_NAME = EventNames.DeprecatedInvocation;
    private static String mode;
    public static int counter;

    public static void main(String... args) throws Exception {
        mode = args[0];
        testDeprecatedLevelAll();
        testDeprecatedLevelAllRetained();
        testReflectionAll();
        testDeprecatedLevelForRemovalRetained();
    }

    private static void testDeprecatedLevelAll() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME).with("level", "all");
            r.start();
            testLevelAll();
            r.stop();
            validateLevelAll(r);
        }
    }

    @Deprecated(forRemoval = true)
    public static void userDeprecatedForRemoval() {
        counter++;
    }

    private static void testLevelAll() throws Exception {
        // Methods individually decorated.
        DeprecatedMethods.deprecated();
        DeprecatedMethods.deprecatedSince();
        DeprecatedMethods.deprecatedForRemoval();
        DeprecatedMethods.deprecatedSinceForRemoval();
        // Class level @deprecated annotation
        // @Deprecated(since = "0")
        DeprecatedThing t = new DeprecatedThing();
        t.instanceDeprecatedForRemoval();
        t.instanceDeprecatedSinceForRemoval();
        t.foo();
        t.zoo();
        // Invoke a deprecated method in the users code
        // to verify the negative case, i.e. that this
        // invocation is not reported.
        userDeprecatedForRemoval();
    }

    private static void validateLevelAll(Recording r) throws Exception {
        List<RecordedEvent> events = Events.fromRecording(r);
        printInvocations(events, "all");
        assertMethod(events, "testLevelAll", "deprecated");
        assertMethod(events, "testLevelAll", "deprecatedSince");
        assertMethod(events, "testLevelAll", "deprecatedForRemoval");
        assertMethod(events, "testLevelAll", "deprecatedSinceForRemoval");
        assertMethod(events, "testLevelAll", "instanceDeprecatedForRemoval");
        assertMethod(events, "testLevelAll", "instanceDeprecatedSinceForRemoval");
        assertMethod(events, "testLevelAll", "foo");
        assertMethod(events, "testLevelAll", "zoo");
        // Negative case
        try {
            assertMethod(events, "testLevelAll", "userDeprecatedForRemoval");
            throw new RuntimeException("Invocation of a deprecated method in user code should not be reported");
        } catch (Exception e) {
            // Expected
        }
    }

    // Does not invoke any deprecated methods. We only verify
    // that all previously invoked methods are still retained
    // when starting and stopping a subsequent recording.
    private static void testDeprecatedLevelAllRetained() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME).with("level", "all");
            r.start();
            r.stop();
            validateLevelAll(r);
        }
    }

    private static void testReflectionAll() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME).with("level", "all");
            r.start();
            DeprecatedMethods.class.getMethod("reflectionDeprecated").invoke(null);
            DeprecatedMethods.class.getMethod("reflectionDeprecatedSince").invoke(null);
            DeprecatedMethods.class.getMethod("reflectionDeprecatedForRemoval").invoke(null);
            DeprecatedMethods.class.getMethod("reflectionDeprecatedSinceForRemoval").invoke(null);
            r.stop();
            validateReflectionLevelAll(r);
        }
    }

    private static void validateReflectionLevelAll(Recording r) throws Exception {
        List<RecordedEvent> events = Events.fromRecording(r);
        printInvocations(events, "reflectionAll");
        assertMethod(events, "testReflectionAll", "reflectionDeprecated");
        assertMethod(events, "testReflectionAll", "reflectionDeprecatedSince");
        assertMethod(events, "testReflectionAll", "reflectionDeprecatedForRemoval");
        assertMethod(events, "testReflectionAll", "reflectionDeprecatedSinceForRemoval");
    }

    // Does not invoke any deprecated methods. We only verify
    // that all previously invoked methods are still retained
    // when starting and stopping a subsequent recording.
    private static void testDeprecatedLevelForRemovalRetained() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME).with("level", "forRemoval");
            r.start();
            r.stop();
            validateLevelForRemoval(r);
        }
    }

    private static void validateLevelForRemoval(Recording r) throws Exception {
        List<RecordedEvent> events = Events.fromRecording(r);
        printInvocations(events, "forRemoval");
        assertMethod(events, "testLevelAll", "deprecatedForRemoval");
        assertMethod(events, "testLevelAll", "deprecatedSinceForRemoval");
        assertMethod(events, "testLevelAll", "instanceDeprecatedForRemoval");
        assertMethod(events, "testLevelAll", "instanceDeprecatedSinceForRemoval");
        assertMethod(events, "testReflectionAll", "reflectionDeprecatedForRemoval");
        assertMethod(events, "testReflectionAll", "reflectionDeprecatedSinceForRemoval");
    }

    private static void assertMethod(List<RecordedEvent> events, String caller, String method) throws Exception {
        for (RecordedEvent e : events) {
            RecordedMethod deprecatedMethod = e.getValue("method");
            boolean forRemoval = e.getValue("forRemoval");
            RecordedStackTrace stacktrace = e.getStackTrace();
            assertNotNull(stacktrace, "should have a stacktrace");
            assertTrue(stacktrace.isTruncated(), "invariant");
            List<RecordedFrame> frames = stacktrace.getFrames();
            assertTrue(frames.size() == 1, "invariant");
            assertTrue(frames.getFirst().isJavaFrame(), "invariant");
            RecordedFrame frame = frames.getFirst();
            assertTrue(frame.isJavaFrame(), "invariant");
            RecordedMethod callerMethod = frame.getMethod();
            assertNull(e.getThread(), "should not have a thread");
            if (forRemoval) {
                assertTrue(deprecatedMethod.getName().endsWith("ForRemoval"), "wrong filtering?");
            }
            if (deprecatedMethod.getName().equals(method) && callerMethod.getName().equals(caller)){
                return;
            }
        }
        throw new Exception("Could not find invocation: " + caller + " -> " + method);
    }


    private static void printInvocations(List<RecordedEvent> events, String all) {
        System.out.println("*** METHOD INVOCATION *** (" + mode + ") level = " + all + " count: " + events.size() + " ***\n");
        for (RecordedEvent e : events) {
            RecordedMethod deprecatedMethod = e.getValue("method");
            boolean forRemoval = e.getValue("forRemoval");
            RecordedStackTrace stacktrace = e.getStackTrace();
            assertNotNull(stacktrace, "should have a stacktrace");
            assertTrue(stacktrace.isTruncated(), "invariant");
            List<RecordedFrame> frames = stacktrace.getFrames();
            assertTrue(frames.size() == 1, "invariant");
            RecordedFrame frame = frames.getFirst();
            assertTrue(frame.isJavaFrame(), "invariant");
            RecordedMethod callerMethod = frame.getMethod();
            int bci = frame.getBytecodeIndex();
            int lineNumber = frame.getLineNumber();
            assertNull(e.getThread(), "should not have a thread");
            System.out.println(callerMethod.getName() + " at bci: " + bci + " line: " + lineNumber + " -> " + deprecatedMethod.getName());
            System.out.println(e);
        }
        System.out.println();
    }
}
