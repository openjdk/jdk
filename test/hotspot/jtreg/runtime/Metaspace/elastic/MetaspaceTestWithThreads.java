/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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
 *
 */

import java.util.Set;

public class MetaspaceTestWithThreads {

    // The context to use.
    final MetaspaceTestContext context;

    // Total *word* size we allow for the test to allocation. The test may overshoot this a bit, but should not by much.
    final long testAllocationCeiling;

    // Number of parallel allocators
    final int numThreads;

    // Number of seconds for each test
    final int seconds;

    RandomAllocatorThread threads[];

    public MetaspaceTestWithThreads(MetaspaceTestContext context, long testAllocationCeiling, int numThreads, int seconds) {
        this.context = context;
        this.testAllocationCeiling = testAllocationCeiling;
        this.numThreads = numThreads;
        this.seconds = seconds;
        this.threads = new RandomAllocatorThread[numThreads];
    }

    protected void stopAllThreads() throws InterruptedException {
        // Stop all threads.
        for (Thread t: threads) {
            t.interrupt();
            t.join();
        }
    }

    void destroyArenasAndPurgeSpace() {

        // This deletes the arenas, which will cause them to return all their accumulated
        // metaspace chunks into the context' chunk manager (freelist) before vanishing.
        // It then purges the context.
        // We may return memory to the operating system:
        // - with -XX:MetaspaceReclaimPolicy=balanced|aggressive (balanced is the default),
        //   we will scourge the freelist for chunks larger than a commit granule, and uncommit
        //   their backing memory. Note that since we deleted all arenas, all their chunks are
        //   in the freelist, should have been maximally folded by the buddy allocator, and
        //   therefore should all be eligible for uncommitting. Meaning the context should
        //   retain no memory at all, its committed counter should be zero.
        // - with -XX:MetaspaceReclaimPolicy=none, we omit the purging and retain memory in the
        //   metaspace allocator, so the context should retain its memory.

        for (RandomAllocatorThread t: threads) {
            if (t.allocator.arena.isLive()) {
                context.destroyArena(t.allocator.arena);
            }
        }
        context.purge();

        context.checkStatistics();

        if (Settings.settings().doesReclaim()) {
            if (context.committedWords() > 0) {
                throw new RuntimeException("Expected no committed words after purging empty metaspace context (was: " + context.committedWords() + ")");
            }
        }
    }

    @Override
    public String toString() {
        return "commitLimit=" + context.commitLimit +
                ", reserveLimit=" + context.reserveLimit +
                ", testAllocationCeiling=" + testAllocationCeiling +
                ", num_allocators=" + numThreads +
                ", seconds=" + seconds;
    }

}
