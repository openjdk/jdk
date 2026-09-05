/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.consumer.streaming;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;

import jdk.jfr.Event;
import jdk.jfr.consumer.RecordingStream;

/**
 * @test
 * @summary Test that it is possible to register new metadata in a new segment while retaining the string pool.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.api.consumer.streaming.TestMetadataReconstructionWithRetainedStringPool
 */
public class TestMetadataReconstructionWithRetainedStringPool {
    /// Minimum string length required to trigger StringPool usage.
    /// Mirrors `jdk.jfr.internal.StringPool.MIN_LIMIT`.
    private static final int STRING_POOL_MIN_LIMIT = 16;
    private static final int EXPECTED_EVENTS = 3;

    // Condition 1: String length > STRING_POOL_MIN_LIMIT triggers CONSTANT_POOL encoding.
    private static final String TEXT = "a".repeat(STRING_POOL_MIN_LIMIT + 1);;

    static final class EventA extends Event {
        String text = TEXT;
    }

    static final class EventB extends Event {
        String text = TEXT;
    }

    public static void main(String... args) throws InterruptedException {
        var aEventsPosted = new CountDownLatch(1);
        var readyToPostEventB = new CountDownLatch(1);
        var remaining = new CountDownLatch(EXPECTED_EVENTS);

        try (var rs = new RecordingStream()) {
            rs.onEvent(e -> {
                String textValue = e.getValue("text");
                if (textValue == null) {
                    throw new RuntimeException("e.getValue(\"text\") returned null");
                }
                remaining.countDown();
                System.out.printf("Event #%d [%s]: text=%s%n",
                        EXPECTED_EVENTS - remaining.getCount(),
                        e.getEventType().getName(),
                        textValue);
            });

            rs.onFlush(() -> {
                if (aEventsPosted.getCount() == 0) {
                    readyToPostEventB.countDown();
                }
            });

            rs.startAsync();

            // Condition 2: Two distinct event types are required.
            //              First, load EventA as the initial event type and emit its first event.
            //              This first event looks into the StringPool pre-cache. Although the
            //              string length qualifies for pooling, because it isn't pre-cached,
            //              the first event encodes the string inline.
            //              The second event finds the string in the pre-cache and adds it to the
            //              pool. A constant pool ID to the pooled string is encoded in the event.
            //
            new EventA().commit();
            new EventA().commit();
            aEventsPosted.countDown();

            // Condition 3: Wait for JFR flush.
            //              The default flush period is ~1 second.
            readyToPostEventB.await();

            // Load the second event type, EventB, AFTER the flush segment containing the two events of type EventA.
            // A new metadata description will be constructed, and we verify that the StringPool added in the previous
            // segment is still available for the EventB string pool reference to be resolved correctly.
            new EventB().commit();
            remaining.await();
        }
    }
}
