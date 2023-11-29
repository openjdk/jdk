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
 * @library /test/lib
 * @build jdk.jfr.event.runtime.DeprecatedThing
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
    public static int counter;
    private static String mode;

    public static void main(String... args) throws Exception {
        mode = args[0];
        testDeprecatedLevelAllPreJFR();
        testDeprecatedLevelAll();
        testDeprecatedLevelAllRetained();
        testDeprecatedLevelForRemoval();
        testDeprecatedLevelForRemovalRetained();
    }

    // Does not invoke any deprecated methods. We only verify
    // that deprecated methods registered during VM bootstrap
    // are retained when starting JFR and the first recording.
    private static void testDeprecatedLevelAllPreJFR() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME).with("level", "all");
            r.start();
            r.stop();
            validateLevelAllPreJFR(r);
        }
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

    private static void testLevelAll() throws Exception {
        deprecated1();
        deprecatedForRemovalFast();
        deprecatedForRemovalSlow();
        DeprecatedThing t = new DeprecatedThing();
        t.foo();
        t.zoo();
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

    // Validates invocations that happened before JFR was started.
    private static void validateLevelAllPreJFR(Recording r) throws Exception {
        List<RecordedEvent> events = Events.fromRecording(r);
        printInvocations(events, "all");
        assertMethod(events, "getProperties", "getSecurityManager");
    }

    private static void validateLevelAll(Recording r) throws Exception {
        List<RecordedEvent> events = Events.fromRecording(r);
        printInvocations(events, "all");
        assertMethod(events, "testLevelAll", "deprecated1");
        assertMethod(events, "testLevelAll", "deprecatedForRemovalFast");
        assertMethod(events, "testLevelAll", "deprecatedForRemovalSlow");
        assertMethod(events, "deprecatedForRemovalSlow", "deprecatedForRemovalSlow2");
        assertMethod(events, "testLevelAll", "foo");
		assertMethod(events, "testLevelAll", "zoo");
        assertMethod(events, "foo", "bar");
        assertMethod(events, "bar", "baz");
        // This is a problematic case only with the interpreter.
        // Because bar was already resolved when foo linked against it,
        // it is considered already linked also by zoo (through the cpCache).
        // Therefore the hook will not be issued when zoo -> bar.
        // assertMethod(events, "zoo", "bar");
    }


    private static void testDeprecatedLevelForRemoval() throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME).with("level", "forRemoval");
            r.start();
            testLevelForRemoval();
            r.stop();
            validateLevelForRemoval(r);
        }
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

    private static void testLevelForRemoval() throws Exception {
        deprecated2();
        deprecated3();
    }

    private static void validateLevelForRemoval(Recording r) throws Exception {
        List<RecordedEvent> events = Events.fromRecording(r);
        printInvocations(events, "forRemoval");
        for (RecordedEvent e : events) {
            RecordedMethod deprecatedMethod = e.getValue("method");
            if (deprecatedMethod.getName().equals("deprecated3")) {
                assertTrue(e.getBoolean("forRemoval"), "invalid level");
                RecordedStackTrace stacktrace = e.getStackTrace();
                assertNotNull(stacktrace, "should have a stacktrace");
                assertTrue(stacktrace.isTruncated(), "invariant");
                List<RecordedFrame> frames = stacktrace.getFrames();
                assertTrue(frames.size() == 1, "invariant");
                RecordedFrame frame = frames.getFirst();
                assertTrue(frame.isJavaFrame(), "invariant");
                RecordedMethod callerMethod = frame.getMethod();
                assertNotNull(callerMethod, "invariant");
                if (callerMethod.getName().equals("testLevelForRemoval")) {
                    return;
                }
            }
        }
        throw new Exception("Could not find invocation forRemvoval");
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
            int bci = frame.getBytecodeIndex();
            int lineNumber = frame.getLineNumber();
            assertNull(e.getThread(), "should not have a thread");
            if (deprecatedMethod.getName().equals(method) && callerMethod.getName().equals(caller)){
                return;
            }
        }
        throw new Exception("Could not find invocation: " + caller + " -> " + method);
    }


    private static void printInvocations(List<RecordedEvent> events, String all) {
        System.out.println("*** METHOD INVOCATION *** (" + mode + ") level = " + all + "count: " + events.size());
        for (RecordedEvent e : events) {
            System.out.println(e);
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
        }
        System.out.println();
    }


    @Deprecated(forRemoval = true)
    public static void deprecatedForRemovalSlow() {
        for (int i = 0; i < 1_000_000; i++) {
            deprecatedForRemovalSlow2();
        }
    }

    @Deprecated(forRemoval = true)
    private static void deprecatedForRemovalSlow2() {
        counter++;
    }

    @Deprecated(forRemoval = true)
    private static void deprecatedForRemovalFast() {
        counter++;
    }

    @Deprecated(forRemoval = false)
    private static void deprecated1() {
        counter++;
    }

    @Deprecated
    private static void deprecated2() {
        counter++;
    }

    @Deprecated(forRemoval = true)
    private static void deprecated3() {
        counter++;
    }
}
