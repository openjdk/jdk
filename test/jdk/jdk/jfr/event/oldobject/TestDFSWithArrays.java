/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, IBM Corp.
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

package jdk.jfr.event.oldobject;

import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.internal.test.WhiteBox;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Tests that DFS works with arrays
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @run main/othervm jdk.jfr.event.oldobject.TestDFSWithArrays
 */
public class TestDFSWithArrays {
    // Tests that array chunking works correctly. We create an object array; link a second object array
    // into it at its middle; link a third object array into the second at its end; fill the third array with
    // many objects. GC root search should walk successfully through the middle of the first and the end of
    // the second to the third array and sample a good portion of its objects.

    private static final int TOTAL_OBJECTS = 10_000_000;
    private static final int ARRAY_CHUNK_SIZE = 64; // keep in sync with dfsClosure.cpp
    public static Object[] leak;

    public static void main(String... args) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);
        WhiteBox.setSkipBFS(true);
        int arraySize = (ARRAY_CHUNK_SIZE * 10) + ARRAY_CHUNK_SIZE / 2;
        int count = 10;
        while (count > 0) {
            try (Recording r = new Recording()) {
                r.enable(EventNames.OldObjectSample).with("cutoff", "infinity");
                r.start();
                Object[] first = new Object[arraySize];
                Object[] second = new Object[arraySize];
                Object[] third = new Object[TOTAL_OBJECTS];
                for (int i = 0; i < third.length; i++) {
                    third[i] = new Object();
                }
                second[second.length - 1] = third;
                first[first.length / 2] = second;
                leak = first;
                System.gc();
                r.stop();
                List<RecordedEvent> events = Events.fromRecording(r);
                Events.hasEvents(events);
                if (OldObjects.countChains(events) >= 30) {
                    return;
                }
                System.out.println("Not enough chains found, retrying.");
            }
            count++;
            leak = null;
            System.gc();
        }
    }
}