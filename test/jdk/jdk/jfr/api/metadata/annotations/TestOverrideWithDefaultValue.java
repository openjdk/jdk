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

package jdk.jfr.api.metadata.annotations;

import java.io.IOException;
import java.util.List;

import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.Registered;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Tests that annotations can be overridden with the default value.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.metadata.annotations.TestOverrideWithDefaultValue
 */
public class TestOverrideWithDefaultValue {

    @Enabled(false)
    static class Mammal extends Event {
    }

    @Enabled
    static class Cat extends Mammal {
    }

    @Registered(false)
    static class Animal extends Event {
    }

    @Registered
    static class Dog extends Animal {
    }

    public static void main(String[] args) throws IOException {
        testEnabled();
        testRegistered();
    }

    private static void testEnabled() throws IOException {
        try (Recording r = new Recording()) {
            r.start();
            Cat cat = new Cat();
            cat.commit();
            List<RecordedEvent> events = Events.fromRecording(r);
            Events.hasEvents(events);
        }
    }

    private static void testRegistered() throws IOException {
        try (Recording r = new Recording()) {
            r.start();
            Dog dog = new Dog();
            dog.commit();
            List<RecordedEvent> events = Events.fromRecording(r);
            Events.hasEvents(events);
        }
    }
}
