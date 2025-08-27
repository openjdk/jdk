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
import jdk.jfr.consumer.RecordedMethod;

import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Tests that class filters work as expected.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm -Xlog:jfr+methodtrace=trace
 *      jdk.jfr.event.tracing.TestFilterClass
 **/
public class TestFilterClass {
    private static final String THIS_CLASS = TestFilterClass.class.getName();
    private static final String EVENT_NAME = "jdk.MethodTrace";

    interface Interface {
        void foo();

        void bar();

        public static void baz() {
            System.out.println("Executing Interface::baz");
        }
    }

    static class Implementation implements Interface {
        public void foo() {
            System.out.println("Executing Implementation::foo");
        }

        @Override
        public void bar() {
            throw new Error("Should not happen");
        }
    }

    enum Enum {
        VALUE;

        public void bar() {
            System.out.println("Executing Enum::bar");
        }
    }

    record Record(int value) {
    }

    public static void main(String... args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME)
             .with("filter", THIS_CLASS + "$Implementation;" +
                             THIS_CLASS + "$Interface;" +
                             THIS_CLASS + "$Enum;" +
                             THIS_CLASS + "$Record");
            r.start();
            Interface.baz();
            new Implementation().foo();
            Enum.VALUE.bar();
            new Record(4711).value();
            r.stop();
            var list = new ArrayList<>(List.of(THIS_CLASS + "$Interface::baz", THIS_CLASS + "$Implementation::<init>", THIS_CLASS + "$Implementation::foo", THIS_CLASS + "$Enum::<clinit>",
                    THIS_CLASS + "$Enum::<init>", THIS_CLASS + "$Enum::bar", THIS_CLASS + "$Record::<init>", THIS_CLASS + "$Record::value"));
            var events = Events.fromRecording(r);
            System.out.println(list);
            Events.hasEvents(events);
            for (RecordedEvent event : events) {
                System.out.println(event);
                RecordedMethod method = event.getValue("method");
                String name = method.getType().getName() + "::" + method.getName();
                if (!list.remove(name)) {
                    throw new Exception("Unexpected method '" + name + "' in event");
                }
            }
            if (!list.isEmpty()) {
                throw new Exception("Expected events for the methods " + list);
            }
        }
    }
}
