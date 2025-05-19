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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordingStream;

/**
 * @test
 * @summary Tests that methods are instrumented correctly.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm
 *           -Xlog:jfr+methodtrace=debug
 *           jdk.jfr.event.tracing.TestInstrumentation
 **/
public class TestInstrumentation {
    private static Object nullObject;

    public static void main(String... args) throws Exception {
        List<RecordedEvent> traceEvents = new CopyOnWriteArrayList<>();
        List<RecordedEvent> timingEvents = new CopyOnWriteArrayList<>();
        try (RecordingStream r = new RecordingStream()) {
            r.setReuse(false);
            String filter = TestInstrumentation.class.getName();
            r.enable("jdk.MethodTrace")
             .with("filter", filter);
            r.enable("jdk.MethodTiming")
             .with("filter", filter)
             .with("period", "endChunk");
            r.onEvent("jdk.MethodTrace", traceEvents::add);
            r.onEvent("jdk.MethodTiming", timingEvents::add);
            r.startAsync();
            try {
                whileTrue();
            } catch (NullPointerException npe) {
                // As expected
            }
            recursive(3);
            switchExpression(0);
            switchExpression(1);
            switchExpression(2);
            multipleReturns();
            multipleReturns();
            multipleReturns();
            multipleReturns();
            multipleReturns();
            try {
                exception();
            } catch (Exception e) {
            }
            try {
                deepException();
            } catch (Exception e) {
            }
            r.stop();
        }
        verifyTracing(traceEvents);
        verifyTiming(timingEvents);
    }

    private static void verifyTracing(List<RecordedEvent> events) throws Exception {
        Map<String, Long> map = buildMethodMap(events, false);
        printMap("Tracing:", map);
        assertMethod(map, "exception", 2);
        assertMethod(map, "switchExpression", 3);
        assertMethod(map, "recursive", 4);
        assertMethod(map, "multipleReturns", 5);
        if (!map.isEmpty()) {
            throw new Exception("Found unexpected methods " + map.keySet());
        }
    }

    private static void verifyTiming(List<RecordedEvent> events) throws Exception {
        Map<String, Long> map = buildMethodMap(events, true);
        printMap("Timing:", map);
        assertMethod(map, "exception", 2);
        assertMethod(map, "switchExpression", 3);
        assertMethod(map, "recursive", 4);
        assertMethod(map, "multipleReturns", 5);
        for (var entry : map.entrySet()) {
            long invocations = entry.getValue();
            if (invocations != 0L) {
                throw new Exception("Unexpected " + invocations + " invocations for method " + entry.getKey());
            }
        }
    }

    private static void printMap(String caption, Map<String, Long> map) {
        System.out.println(caption);
        for (var entry : map.entrySet()) {
            System.out.println(entry.getKey() + " = " + entry.getValue());
        }
        System.out.println();
    }

    private static void assertMethod(Map<String, Long> map, String method, long value) throws Exception {
        if (!map.containsKey(method)) {
            throw new Exception("Missing method " + method);
        }
        if (!map.get(method).equals(value)) {
            throw new Exception("Expected value " + value + " for method " + method);
        }
        map.remove(method);
    }

    private static Map<String, Long> buildMethodMap(List<RecordedEvent> events, boolean invocations) {
        Map<String, Long> map = new HashMap<>();
        for (RecordedEvent e : events) {
            RecordedMethod m = e.getValue("method");
            String name = m.getName();
            long add = invocations ? e.getLong("invocations") : 1;
            map.compute(name, (key, value) -> (value == null) ? add : value + add);
        }
        return map;
    }

    public static void whileTrue() {
        while (true) {
            nullObject.toString();
        }
    }

    public static void recursive(int depth) {
        if (depth > 0) {
            recursive(depth - 1);
        } else {
            return;
        }
    }

    public static String switchExpression(int value) {
        return switch (value) {
        case 0 -> "zero";
        case 1 -> "one";
        default -> "number";
        };
    }

    public static void multipleReturns() {
        Random r = new Random();
        int v = r.nextInt(5);
        if (v == 0) {
            return;
        }
        switch (v) {
        case 1:
            return;
        case 2:
            return;
        }
        return;
    }

    public static void exception() throws Exception {
        throw new Exception("");
    }

    public static void deepException() throws Exception {
        exception();
    }
}
