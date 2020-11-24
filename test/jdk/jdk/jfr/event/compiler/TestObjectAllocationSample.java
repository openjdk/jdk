/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jfr.event.compiler;

import static java.lang.Math.floor;
import static jdk.test.lib.Asserts.assertGreaterThanOrEqual;
import static jdk.test.lib.Asserts.assertLessThanOrEqual;

import java.time.Duration;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Test for the new object allocation event.
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm -XX:+UseTLAB -XX:TLABSize=2k -XX:-ResizeTLAB -XX:TLABRefillWasteFraction=256 jdk.jfr.event.compiler.TestObjectAllocationSample
 * @run main/othervm -XX:+UseTLAB -XX:TLABSize=2k -XX:-ResizeTLAB -XX:TLABRefillWasteFraction=256 -Xint jdk.jfr.event.compiler.TestObjectAllocationSample
 */

/**
 * Test that an event is triggered when an object is allocated outside a
 * Thread Local Allocation Buffer (TLAB). The test is done for default interpreted mode (-Xint).
 *
 * To force objects to be allocated outside TLAB:
 *      the size of TLAB is set to 90k (-XX:TLABSize=90k);
 *      the size of allocated objects is set to 100k.
 *      max TLAB waste at refill is set to 256 (-XX:TLABRefillWasteFraction=256),
 *          to prevent a new TLAB creation.
*/
public class TestObjectAllocationSample {
    private static final String EVENT_NAME = EventNames.ObjectAllocationSample;

    private static final int BYTE_ARRAY_OVERHEAD = 16; // Extra bytes used by a byte array
    private static final int OBJECT_SIZE = 100 * 1024;
    private static final int OBJECT_SIZE_ALT = OBJECT_SIZE + 8; // Object size in case of disabled CompressedOops
    private static final int OBJECTS_TO_ALLOCATE = 100;
    private static final String BYTE_ARRAY_CLASS_NAME = new byte[0].getClass().getName();

    public static byte[] tmp; // Used to prevent optimizer from removing code.

    public static void main(String[] args) throws Exception {
        Recording rate = new Recording();
        rate.enable(EVENT_NAME).with("throttle", "100/s");
        rate.start();
        Recording new_rate = new Recording();
        new_rate.enable(EVENT_NAME).with("throttle", "99/s");
        new_rate.start();

        for (int i = 0; i < OBJECTS_TO_ALLOCATE; ++i) {
            tmp = new byte[OBJECT_SIZE - BYTE_ARRAY_OVERHEAD];
        }
        new_rate.stop();
        rate.stop();

        int countEvents = 0;
        for (RecordedEvent event : Events.fromRecording(rate)) {
            if (!EVENT_NAME.equals(event.getEventType().getName())) {
                continue;
            }
            System.out.println("Event:" + event);

            long allocationSize = Events.assertField(event, "allocationSize").atLeast(1L).getValue();
            String className = Events.assertField(event, "objectClass.name").notEmpty().getValue();

            boolean isMyEvent = Thread.currentThread().getId() == event.getThread().getJavaThreadId()
                && className.equals(BYTE_ARRAY_CLASS_NAME)
                 && (allocationSize == OBJECT_SIZE || allocationSize == OBJECT_SIZE_ALT);
            if (isMyEvent) {
                ++countEvents;
            }
        }
        System.out.println("Events: " + countEvents);
        /*
        int minCount = (int) floor(OBJECTS_TO_ALLOCATE * 0.80);
        assertGreaterThanOrEqual(countEvents, minCount, "Too few tlab objects allocated");
        assertLessThanOrEqual(countEvents, OBJECTS_TO_ALLOCATE, "Too many tlab objects allocated");
        */
    }

}
