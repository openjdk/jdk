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
package jdk.jfr.event.tracing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Tests that a method filter (e.g., class::method) works as expected.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm -Xlog:jfr+methodtrace=trace
 *      jdk.jfr.event.tracing.TestFilterMethod
 */
public class TestFilterMethod {
    private static final String THIS_CLASS = TestFilterMethod.class.getName();
    private static final String EVENT_NAME = "jdk.MethodTrace";

    // Tests implicit constructor
    static public class SomeClass {
        public void override() {
            throw new Error("Should not happen");
        }
    }

    // Tests explicit constructor
    static public class OtherClass extends SomeClass {
        public OtherClass() {
            System.out.println("Executing Otherclass::Otherclass()");
        }
    }

    // Tests method override
    static public class SomeSubclass extends SomeClass {
        public void override() {
            System.out.println("Executing SomeSubclass::override()");
        }
    }

    // Tests method in enum
    enum Enum {
        VALUE;

        static void enumMethod() {
            System.out.println("Executing Enum:enumMethod");
        }
    }

    // Tests method in interface
    interface Interface {
        public static void staticMethod() {
            System.out.println("Executing Interface::staticMethod");
        }

        public void instanceMethod();
    }

    static class Implementation implements Interface {
        @Override
        public void instanceMethod() {
        }
    }

    // Tests normal method
    public static void overload() {
        System.out.println("Executing overload()");
    }

    // Tests overloaded method
    public static void overload(int value) {
        System.out.println("Executing overload(" + value + ")");
    }

    public static void main(String... args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME)
             .with("filter", THIS_CLASS + "$SomeSubclass::override;" +
                             THIS_CLASS + "$OtherClass::<init>;" +
                             THIS_CLASS + "::overload;" +
                             THIS_CLASS + "$Enum::enumMethod;" +
                             THIS_CLASS + "$Interface::staticMethod;" +
                             THIS_CLASS + "$Implementation::instanceMethod");
            r.start();
            new SomeSubclass().override();
            new OtherClass();
            overload();
            overload(1);
            Enum.enumMethod();
            Interface.staticMethod();
            new Implementation().instanceMethod();
            r.stop();

            var set = new ArrayList<>(List.of(
                "<init>", // OtherClass:<init>
                "override",
                "overload", // overload()
                "overload", // overload(int)
                "enumMethod",
                "staticMethod",
                "instanceMethod"));
            var events = Events.fromRecording(r);
            Events.hasEvents(events);
            for (RecordedEvent event : events) {
                System.out.println(event);
                RecordedMethod m = event.getValue("method");
                if (!set.remove(m.getName())) {
                    throw new Exception("Unexpected method '" + m.getName() + "' in event");
                }
                RecordedFrame topFrame = event.getStackTrace().getFrames().get(0);
                String topMethod = topFrame.getMethod().getName();
                if (!topMethod.equals("main")) {
                    throw new Exception("Expected method to be called from main");
                }
            }
            if (!set.isEmpty()) {
                throw new Exception("Expected events for the methods " + set);
            }
        }
    }
}
