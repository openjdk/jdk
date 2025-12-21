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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Tests that constructors are instrumented correctly.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm -Xlog:jfr+methodtrace=debug
 *      jdk.jfr.event.tracing.TestConstructors
 **/
public class TestConstructors {
    static private void methodThatThrows() {
        throw new RuntimeException();
    }

    public static class Cat {
        Cat() {
            new String();
            methodThatThrows();
            super();
            methodThatThrows();
        }
    }

    public static class Dog {
        Dog() {
            super();
            methodThatThrows();
        }
    }

    public static class Tiger {
        Tiger() {
            methodThatThrows();
            super();
        }
    }

    public static class Zebra {
        Zebra(boolean shouldThrow) {
            this(shouldThrow ? 1 : 0);
        }

        Zebra(int shouldThrow) {
            if (shouldThrow == 1) {
                throw new RuntimeException();
            }
        }
    }

    public static class Snake {
        Snake() {
            try {
                throw new RuntimeException();
            } catch (Exception e) {
                // Ignore
            }
            super();
        }
    }

    public static void main(String... args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable("jdk.MethodTrace").with("filter", Dog.class.getName() + ";" + Cat.class.getName() + ";" + Tiger.class.getName() + ";" + Zebra.class.getName() + ";" + Snake.class.getName());
            r.start();
            try {
                new Cat();
            } catch (Exception e) {
                // ignore
            }
            try {
                new Dog();
            } catch (Exception e) {
                // ignore
            }
            try {
                new Tiger();
            } catch (Exception e) {
                // ignore
            }
            try {
                new Zebra(true);
            } catch (Exception e) {
                // ignore
            }
            try {
                new Zebra(false);
            } catch (Exception e) {
                // ignore
            }
            try {
                new Snake();
            } catch (Exception e) {
                // ignore
            }
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            var methods = buildMethodMap(events);
            if (methods.size() != 5) {
                throw new Exception("Expected 5 different methods");
            }
            assertMethodCount(methods, "Cat", 1);
            assertMethodCount(methods, "Dog", 1);
            assertMethodCount(methods, "Snake", 1);
            assertMethodCount(methods, "Tiger", 1);
            assertMethodCount(methods, "Zebra", 3);
        }
    }

    private static void assertMethodCount(Map<String, Long> methods, String className, int expectedCount) throws Exception {
        String name = TestConstructors.class.getName() + "$" + className + "::<init>";
        Long count = methods.get(name);
        if (count == null) {
            throw new Exception("Could not find traced method " + name);
        }
        if (count != expectedCount) {
            throw new Exception("Expected " + expectedCount + " trace event for " + name);
        }
    }

    private static Map<String, Long> buildMethodMap(List<RecordedEvent> events) {
        Map<String, Long> map = new TreeMap<>();
        for (RecordedEvent e : events) {
            RecordedMethod m = e.getValue("method");
            String name = m.getType().getName() + "::" + m.getName();
            map.compute(name, (_, value) -> (value == null) ? 1 : value + 1);
        }
        for (var e : map.entrySet()) {
            System.out.println(e.getKey() + " " + e.getValue());
        }
        return map;
    }
}