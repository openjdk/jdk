/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.TestClassLoader;

/**
 * @test
 * @bug 8266936 8276422
 * @summary The test verifies that classes overriding finalize() are represented as events.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm -Xlog:class+unload,finalizer -Xmx16m jdk.jfr.event.runtime.TestFinalizerStatisticsEvent
 * @run main/othervm -Xlog:class+unload,finalizer -Xmx16m --finalization=disabled jdk.jfr.event.runtime.TestFinalizerStatisticsEvent disabled
 */

public final class TestFinalizerStatisticsEvent {
    private final static String TEST_CLASS_NAME = "jdk.jfr.event.runtime.TestFinalizerStatisticsEvent$TestClassOverridingFinalize";
    private final static String TEST_CLASS_UNLOAD_NAME = "jdk.jfr.event.runtime.TestFinalizerStatisticsEvent$TestClassUnloadOverridingFinalize";
    private final static String EVENT_PATH = EventNames.FinalizerStatistics;
    private static boolean disabled = false;

    // Declare as public static to prevent the compiler from optimizing away all unread writes
    public static TestClassLoader unloadableClassLoader;
    public static Class<?> unloadOverrideClass;
    public static Object overridingInstance;

    public static void main(String[] args) throws Throwable {
        if (args.length > 0 && "disabled".equals(args[0])) {
            disabled = true;
            System.out.println("Testing with finalization disabled");
        }
        Recording recording1 = new Recording();
        recording1.enable(EVENT_PATH);
        Recording recording2 = new Recording();
        recording2.enable(EVENT_PATH);
        recording1.start();
        allocateAndGC();
        recording2.start(); // rotation writes an event for TEST_CLASS_NAME into recording1
        unloadableClassLoader = new TestClassLoader();
        unloadOverrideClass = unloadableClassLoader.loadClass(TEST_CLASS_UNLOAD_NAME);
        unloadOverrideClass = null;
        unloadableClassLoader = null;
        allocateAndGC(); // the unloading of class TEST_CLASS_UNLOAD_NAME is intercepted and an event is written into both recording1 and recording2
        recording2.stop(); // rotation writes an event for TEST_CLASS_NAME into both recording1 and recording2
        allocateAndGC();
        recording1.stop(); // rotation writes an event for TEST_CLASS_NAME into recording1 which now has 4 events reflecting this test case (3 chunks + 1 unload)

        try {
            if (disabled) {
                verifyDisabled(recording1);
            } else {
                verifyEnabled(recording2);
                verifyEnabled(recording1);
            }
        }
        finally {
            recording2.close();
            recording1.close();
        }
    }

    private static void allocateAndGC() {
        overridingInstance = new TestClassOverridingFinalize();
        overridingInstance = null;
        System.gc();
    }

    /* Verify correct operation with finalization enabled */
    private static void verifyEnabled(Recording recording) throws Throwable {
        boolean foundTestClassName = false;
        boolean foundTestClassUnloadName = false;
        List<RecordedEvent> events = Events.fromRecording(recording);
        Events.hasEvents(events);
        for (RecordedEvent event : events) {
          System.out.println("Event:" + event);
          RecordedClass overridingClass = event.getValue("finalizableClass");
          switch (overridingClass.getName()) {
              case TEST_CLASS_NAME: {
                  Asserts.assertTrue(event.getString("codeSource").startsWith("file://"));
                  foundTestClassName = true;
                  break;
              }
              case TEST_CLASS_UNLOAD_NAME: {
                  foundTestClassUnloadName = true;
                  break;
              }
          }
        }
        Asserts.assertTrue(foundTestClassName, "The class: " + TEST_CLASS_NAME + " overriding finalize() is not found");
        Asserts.assertTrue(foundTestClassUnloadName, "The class: " + TEST_CLASS_UNLOAD_NAME + " overriding finalize() is not found");
    }

    /* Verify no jdk.FinalizerStatistics events with finalization disabled */
    private static void verifyDisabled(Recording recording) throws Throwable {
        int f10nEvents = 0;
        List<RecordedEvent> events = Events.fromRecording(recording);
        for (RecordedEvent event : events) {
            System.out.println("Event:" + event);
            if ("jdk.FinalizerStatistics".equals(event.getEventType().getName())) {
                f10nEvents++;
            }
        }
        Asserts.assertEquals(f10nEvents, 0, "Finalization disabled, but recorded " + f10nEvents + " jdk.FinalizerStatistics events");
    }

    static public class TestClassOverridingFinalize {
        public boolean finalized = false;

        @Override
        protected void finalize() {
            finalized = true;
        }
    }

    static public class TestClassUnloadOverridingFinalize {
        public boolean finalized = false;

        @Override
        protected void finalize() {
            finalized = true;
        }
    }
}
