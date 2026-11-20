/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.TestClassLoader;

/**
 * @test id=loading
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @modules java.base/jdk.internal.misc
 * @build jdk.jfr.event.runtime.TestClasses
 * @run main/othervm jdk.jfr.event.runtime.TestClassLoadingStatisticsEvent
 */
/*
 * @test id=retransform
 * @bug 8397416
 * @summary JFR class loading statistics must not include scratch classes created by retransformation
 * @requires vm.flagless & vm.hasJFR & vm.jvmti
 * @library /test/lib /test/jdk
 * @modules java.instrument jdk.jfr
 * @build jdk.jfr.event.runtime.TestClassLoadingStatisticsEvent
 * @run driver jdk.test.lib.util.JavaAgentBuilder
 *      jdk.jfr.event.runtime.TestClassLoadingStatisticsEvent TestClassLoadingStatisticsEvent.jar
 * @run main/othervm -javaagent:TestClassLoadingStatisticsEvent.jar
 *      jdk.jfr.event.runtime.TestClassLoadingStatisticsEvent retransform
 */
/*
 * @test id=redefine
 * @bug 8397416
 * @summary JFR class loading statistics must not include scratch classes created by redefinition
 * @requires vm.flagless & vm.hasJFR & vm.jvmti
 * @library /test/lib /test/jdk
 * @modules java.instrument jdk.jfr
 * @build jdk.jfr.event.runtime.TestClassLoadingStatisticsEvent
 * @run driver jdk.test.lib.util.JavaAgentBuilder
 *      jdk.jfr.event.runtime.TestClassLoadingStatisticsEvent TestClassLoadingStatisticsEvent.jar
 * @run main/othervm -javaagent:TestClassLoadingStatisticsEvent.jar
 *      jdk.jfr.event.runtime.TestClassLoadingStatisticsEvent redefine
 */
/**
 * This test will load a number of classes. After each load step we verify that
 * the loadedClassCount and unloadedClassCount attributes are correct.
 *
 * System.gc() will trigger class unloading if -XX:+ExplicitGCInvokesConcurrent
 * is NOT set. If this flag is set G1 will never unload classes on System.gc().
 * As far as the "jfr" key guarantees no VM flags are set from the
 * outside it should be enough with System.gc().
 */
public class TestClassLoadingStatisticsEvent {

    private final static String EVENT_PATH = EventNames.ClassLoadingStatistics;
    private final static String TESTCLASS_PUBLIC_STATIC =
                "jdk.jfr.event.runtime.TestClasses$TestClassPublicStatic";
    private final static String TESTCLASS_PUBLIC_STATIC_INNER =
                "jdk.jfr.event.runtime.TestClasses$TestClassPublicStatic$TestClassPublicStaticInner";

    // Declare unloadableClassLoader as "public static"
    // to prevent the compiler to optimize away all unread writes
    public static TestClassLoader unloadableClassLoader;

    private static Instrumentation instrumentation;

    public static class Target {
        public static int value() {
            return 4711;
        }
    }

    public static void premain(String arguments, Instrumentation agent) {
        instrumentation = agent;
    }

    public static void main(String[] args) throws Throwable {
        if (args.length != 0) {
            testRedefinition(args[0].equals("retransform"));
            return;
        }

        // Load twice to get more stable result.
        RecordedEvent event = getCurrentEvent();
        event = getCurrentEvent();

        // Declare class ClassLoadingStatisticsHelper.
        TestClasses[] helpers = new TestClasses[10];
        event = verifyCountDelta(event, 1, 0);

        // Should load classes TestClassPrivate and TestClassPrivateStatic.
        for (int c = 0; c < helpers.length; c++) {
            helpers[c] = new TestClasses();
        }
        event = verifyCountDelta(event, 2, 0);

        // Load classes TestClassProtected and B2.
        helpers[0].new TestClassProtected();
        helpers[1].new TestClassProtected(); // This class is already loaded.
        new TestClasses.TestClassProtectedStatic();
        event = verifyCountDelta(event, 2, 0);

        // Load classes TestClassProtected1 and TestClassProtectedStatic1.
        for (int c = 0; c < helpers.length; c++) {
            helpers[c].loadClasses();
        }
        event = verifyCountDelta(event, 2, 0);

        // Load the classes with separate class loader. Will be unloaded later.
        unloadableClassLoader = new TestClassLoader();

        unloadableClassLoader.loadClass(TESTCLASS_PUBLIC_STATIC_INNER);
        event = verifyCountDelta(event, 1, 0);

        unloadableClassLoader.loadClass(TESTCLASS_PUBLIC_STATIC);
        event = verifyCountDelta(event, 1, 0);

        // This System.gc() should not unload classes, since the
        // unloadableClassLoader object is still active.
        System.gc();
        event = verifyCountDelta(event, 0, 0);

        // make classes are unloaded.
        unloadableClassLoader = null;
        System.gc();
        event = verifyCountDelta(event, 0, 2);
    }

    private static void testRedefinition(boolean retransform) throws Throwable {
        Class<?>[] targets = { Target.class };
        ClassDefinition[] definitions;
        try (InputStream stream = Target.class.getResourceAsStream("TestClassLoadingStatisticsEvent$Target.class")) {
            definitions = new ClassDefinition[] { new ClassDefinition(Target.class, stream.readAllBytes()) };
        }
        Asserts.assertEquals(4711, Target.value());

        for (int iteration = 0; iteration < 10; iteration++) {
            transform(retransform, targets, definitions);
            getCurrentEvent();
        }

        RecordedEvent before = getCurrentEvent();
        for (int iteration = 0; iteration < 100; iteration++) {
            transform(retransform, targets, definitions);
        }
        RecordedEvent after = getCurrentEvent();

        System.out.println("Mode: " + (retransform ? "retransform" : "redefine"));
        System.out.println(before);
        System.out.println(after);

        Asserts.assertEquals(before.getLong("loadedClassCount"), after.getLong("loadedClassCount"),
            "must not count scratch classes as loaded classes");
        Asserts.assertEquals(4711, Target.value());
    }

    private static void transform(boolean retransform, Class<?>[] targets,
                                  ClassDefinition[] definitions) throws Exception {
        if (retransform) {
            instrumentation.retransformClasses(targets);
        } else {
            instrumentation.redefineClasses(definitions);
        }
    }

    private static RecordedEvent getCurrentEvent() throws Throwable {
        try (Recording recording = new Recording()) {
            recording.enable(EVENT_PATH).with("period", "endChunk");
            recording.start();
            recording.stop();
            List<RecordedEvent> events = Events.fromRecording(recording);
            Asserts.assertFalse(events.isEmpty(), "No events in recording");
            RecordedEvent event = events.getFirst();
            Asserts.assertEquals(event.getEventType().getName(), EVENT_PATH);
            return event;
        }
    }

    private static RecordedEvent verifyCountDelta(RecordedEvent prevEvent,
                                                  int loadDelta, int unloadDelta) throws Throwable {
        RecordedEvent currEvent = null;
        try {
            long prevLoad = Events.assertField(prevEvent, "loadedClassCount").getValue();
            long prevUnload = Events.assertField(prevEvent, "unloadedClassCount").getValue();
            currEvent = getCurrentEvent();
            Events.assertField(currEvent, "loadedClassCount").atLeast(prevLoad + loadDelta);
            Events.assertField(currEvent, "unloadedClassCount").atLeast(prevUnload + unloadDelta);
            return currEvent;
        } catch (Throwable t) {
            System.out.println("verifyCountDelta failed. prevEvent=" + prevEvent + ", currEvent=" + currEvent);
            throw t;
        }
    }

}
