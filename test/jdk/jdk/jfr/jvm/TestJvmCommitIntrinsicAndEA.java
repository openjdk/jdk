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

package jdk.jfr.jvm;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.CountDownLatch;
import jdk.jfr.consumer.RecordingStream;
import jdk.test.lib.jfr.EventNames;

/**
 *
 * A successful execution is when the JTREG test executes successfully,
 * i.e., with no exceptions or JVM asserts.
 *
 * In JVM debug builds, C2 will assert as part of EscapeAnalysis and dump an hs_err<pid>.log:
 *
 * #
 * # A fatal error has been detected by the Java Runtime Environment:
 * #
 * # Internal Error (..\open\src\hotspot\share\opto\escape.cpp:4788)
 * # assert(false) failed: EA: missing memory path
 *
 * Current thread JavaThread "C2 CompilerThread2"
 * Current CompileTask:
 * C2:27036 1966 ! 4   java.lang.VirtualThread::run (173 bytes)
 *
 * ConnectionGraph::split_unique_types+0x2409 (escape.cpp:4788)
 * ConnectionGraph::compute_escape+0x11e6 (escape.cpp:397)
 * ConnectionGraph::do_analysis+0xf2 (escape.cpp:118)
 * Compile::Optimize+0x85d (compile.cpp:2381)
 * ...
 *
 * The error is because C2's Escape Analysis does not recognize a pattern
 * where one input of memory Phi node is MergeMem node, and another is RAW store.
 * This pattern is created by the jdk.jfr.internal.JVM.commit() intrinsic,
 * which is inlined because of inlining the JFR event jdk.VirtualThreadStart.
 *
 * As a result, EA complains about a strange memory graph.
 */

/**
 * @test
 * @bug 8352696
 * @requires vm.flagless & vm.hasJFR & vm.debug
 * @library /test/lib /test/jdk
 * @run main/othervm -Xbatch jdk.jfr.jvm.TestJvmCommitIntrinsicAndEA
 */
public final class TestJvmCommitIntrinsicAndEA {
    private static final int NUM_TASKS = 10_000;

    public static void main(String[] args) throws Throwable {
        CountDownLatch latch = new CountDownLatch(NUM_TASKS);

        try (RecordingStream rs = new RecordingStream()) {
            rs.enable(EventNames.VirtualThreadStart).withoutStackTrace();
            rs.enable(EventNames.VirtualThreadEnd).withoutStackTrace();
            rs.onEvent(EventNames.VirtualThreadEnd, e -> latch.countDown());
            rs.startAsync();
            // Execute NUM_TASKS, each in their own virtual thread.
            ThreadFactory factory = Thread.ofVirtual().factory();
            try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
                for (int i = 0; i < NUM_TASKS; i++) {
                    executor.submit(() -> { });
                }
            }
            latch.await();
        }
    }
}
