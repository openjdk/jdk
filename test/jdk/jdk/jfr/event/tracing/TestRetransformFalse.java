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

import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Tests that tracing doesn't work retransformation disabled.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm
 *     -Xlog:jfr+methodtrace=info
 *     -XX:FlightRecorderOptions:retransform=false
 *     jdk.jfr.event.tracing.TestRetransformFalse false
 * @run main/othervm -Xlog:jfr+methodtrace=info
 *      -Xlog:jfr+methodtrace=info
 *      -XX:FlightRecorderOptions:retransform=true
 *      jdk.jfr.event.tracing.TestRetransformFalse true
 **/
public class TestRetransformFalse {
    private static final String FILTER = "jdk.jfr.event.tracing.TestRetransformFalse::foo";
    public static void main(String... args) throws Exception {
        boolean retransform = switch (args[0]) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new Exception("Test error, expected 'true' or 'false' argument to test.");
        };
        System.out.println("Testing -XX:FlightRecorderOptions:retransform=" + retransform);
        try (Recording r = new Recording()) {
            r.enable("jdk.MethodTrace")
             .with("filter", FILTER);
            r.enable("jdk.MethodTiming")
             .with("filter", FILTER)
             .with("period", "endChunk");
            r.start();
            foo();
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            System.out.println(events);
            if (retransform) {
                Events.assertEventCount(events, 2);
            } else {
                Events.assertEventCount(events, 0);
            }
        }
    }

    private static void foo() {
        System.out.println("Running Foo");
    }
}
