/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import static jdk.test.lib.Asserts.*;

/**
 * @test
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @modules java.base/jdk.internal.misc
 * @build jdk.jfr.event.runtime.TestClasses
 * @run main/othervm -Xlog:gc -Xmx16m jdk.jfr.event.runtime.TestUnsafeAllocateMemoryEvent
 */

public final class TestUnsafeAllocateMemoryEvent {
    private final static String EVENT_PATH = EventNames.UnsafeAllocateMemory;

    private static final Unsafe UNSAFE;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void main(String[] args) throws Throwable {
        Recording recording = new Recording();
        recording.enable(EVENT_PATH);

        recording.start();

        long address = UNSAFE.allocateMemory(100);

        recording.stop();

        if (address == 0) {
            throw new RuntimeException("failed to allocate");
        }

        var events = Events.fromRecording(recording);
        var filteredEvents = events.stream().filter(e -> e.getEventType().getName().equals(EVENT_PATH)).toList();
        assertGreaterThan(filteredEvents.size(), 0, "Should exist events of type: " + EVENT_PATH);
        for (RecordedEvent event : filteredEvents) {
            System.out.println(event);
            event.getStackTrace().toString().contains("TestUnsafeAllocateMemoryEvent");
            Events.assertField(event, "size").isEqual(100);
            Events.assertField(event, "success").isEqual(true);
        }

    }
}
