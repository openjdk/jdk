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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.Set;

import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.event.tracing.Apple;
import jdk.jfr.event.tracing.Banana;
import jdk.jfr.event.tracing.TestFilterMethodAnnotation.Foo;

/**
 * @test
 * @summary Tests method-annotation-based filtering.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm -Xlog:jfr+methodtrace=trace
 *      jdk.jfr.event.tracing.TestFilterMethodAnnotation
 */
public class TestFilterMethodAnnotation {

    static String EVENT_NAME = "jdk.MethodTrace";

    // Tests that abstract method is ignored
    static abstract class Foo {
        @Apple
        abstract void baz();
    }

    // Tests tracing of an inner class
    static class Bar extends Foo {
        @Override
        // Tests method override
        @Apple
        void baz() {
            System.out.println("Executing Bar::baz()");
        }

        @Apple
        void qux() {
            System.out.println("Executing Bar::qux()");
        }
    }

    // Tests tracing of method with multiple annotations and the target not being
    // first
    @Banana
    @Apple
    private static void ant() {
        System.out.println("Executing method: ant()");
    }

    // Tests that overloaded method with the same name is not traced
    private static void ant(int i) {
        System.out.println("Executing method: apple(" + i + ")");
    }

    // Tests that non-annotated method is not traced
    private static void bear() {
        System.out.println("Executing method: bear()");
    }

    public static void main(String... args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME)
             .with("filter", "@" + Apple.class.getName());
            r.start();
            ant();
            ant(4711);
            bear();
            Bar bar = new Bar();
            bar.baz();
            bar.qux();
            r.stop();
            var set = new HashSet<>(Set.of("ant", "baz", "qux"));
            var events = Events.fromRecording(r);
            Events.hasEvents(events);
            for (RecordedEvent event : events) {
                System.out.println(event);
                RecordedMethod method = event.getValue("method");
                String methodName = method.getName();
                if (!set.remove(methodName)) {
                    throw new Exception("Unexpected method " + methodName + "() in event");
                }
                RecordedFrame topFrame = event.getStackTrace().getFrames().get(0);
                if (!topFrame.getMethod().getName().equals("main")) {
                    throw new Exception("Expected method to be called from main");
                }
            }
            if (!set.isEmpty()) {
                throw new Exception("Expected events for the methods " + set);
            }
        }
    }
}
