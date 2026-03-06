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

import java.util.LinkedList;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.internal.test.WhiteBox;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Tests that DFS works with a small stack
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @modules jdk.jfr/jdk.jfr.internal.test
 * @run main/othervm jdk.jfr.event.oldobject.TestDFSWithSmallStack
 */
public class TestDFSWithSmallStack {

    // Tests depth first search with a small stack.

    // An non-zero exit code, together with a missing hs-err file or possibly a missing jfr file,
    // indicates a native stack overflow happened and is a fail condition for this test.

    // We build up an array of linked lists, each containing enough entries for DFS search to
    // max out max_dfs_depth (but not greatly surpass it).
    // The old recursive implementation, started with such a small stack, will fail to reach
    // max_dfs_depth, instead crashing with stack overflow.
    // The new non-recursive implementation should work; the majority of the linked list items
    // should be scanned (all those at the start of the lists, below max_dfs_depth) and contribute
    // old object samples to the result.

    // Note: VMThreadStackSize defines thread stack size for *all* VM-internal threads, not just the
    // VM thread. Make sure that whatever small size we use in this test is still fine for the rest of
    // the VM.

    private static final int TOTAL_OBJECTS = 10_000_000;
    private static final int OBJECTS_PER_LIST = 5_000;
    public static LinkedList<Object>[] leak;

    public static void main(String... args) throws Exception {
         WhiteBox.setWriteAllObjectSamples(true);
         WhiteBox.setSkipBFS(true);
         int count = 10;
         while (count > 0) {
             try (Recording r = new Recording()) {
                 r.enable(EventNames.OldObjectSample).with("cutoff", "infinity");
                 r.start();
                 leak = new LinkedList[TOTAL_OBJECTS/OBJECTS_PER_LIST];
                 for (int i = 0; i < leak.length; i++) {
                     leak[i] = new LinkedList<Object>();
                     for (int j = 0; j < OBJECTS_PER_LIST; j++) {
                         leak[i].add(new Object());
                     }
                 }
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
         }
     }
}
