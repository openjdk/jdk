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


import jdk.jfr.event.tracing.Apple;
import jdk.jfr.event.tracing.Banana;


import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.Set;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;

/**
 * @test
 * @summary Tests class-annotation-based filtering.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm
 *    -Xlog:jfr+methodtrace=trace
 *    jdk.jfr.event.tracing.TestFilterClassAnnotation
 */

// @Banana and Apple tests multiple annotations and that the target is not the first annotation
@Banana
@Apple
public class TestFilterClassAnnotation {
    private static final String EVENT_NAME = "jdk.MethodTrace";

    // Class Foo tests inner and interface classes
    @Apple
    interface Foo {
        // Method duck() tests that static methods in interfaces can be instrumented
        private static void duck() {
            System.out.println("Executing method: duck()");
        }

        // Method eggplant() tests that abstract method doesn't interfere in the
        // instrumentation
        void eggplant();
    }

    // Method ant() tests that the same method annotation as the class doesn't
    // interfere
    @Apple
    private static void ant() {
        System.out.println("Executing method: ant()");
    }

    // Method bear() tests that other method annotation doesn't interfere
    @Banana
    private static void bear() {
        System.out.println("Executing method: bear()");
    }

    // Method cat() tests that a method in an annotated class is instrumented
    private static void cat() {
        System.out.println("Executing method: cat()");
    }

    public static void main(String... args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME)
             .with("filter", "@" + Apple.class.getName());
            r.start();
            ant();
            bear();
            cat();
            Foo.duck();
            r.stop();

            var set = new HashSet<>(Set.of("ant", "bear", "cat", "duck"));
            var events = Events.fromRecording(r);
            Events.hasEvents(events);
            for (RecordedEvent e : events) {
                System.out.println(e);
                RecordedMethod method = e.getValue("method");
                if (!set.remove(method.getName())) {
                    throw new Exception("Unexpected method '" + method.getName() + "' in event");
                }
                RecordedFrame topFrame = e.getStackTrace().getFrames().get(0);
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
