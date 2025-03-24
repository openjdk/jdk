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

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jfr.EventType;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

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

    public static void main(String[] args) throws Throwable {
        try (Recording recording = new Recording()) {
            recording.enable(EventNames.VirtualThreadStart).withoutStackTrace();
            recording.enable(EventNames.VirtualThreadEnd).withoutStackTrace();
            recording.start();
            // execute 10_000 tasks, each in their own virtual thread
            ThreadFactory factory = Thread.ofVirtual().factory();
            try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
                for (int i = 0; i < 10_000; i++) {
                    executor.submit(() -> { });
                }
            } finally {
                recording.stop();
            }

            Map<String, Integer> events = sumEvents(recording);
            System.err.println(events);

            int startCount = events.getOrDefault(EventNames.VirtualThreadStart, 0);
            int endCount = events.getOrDefault(EventNames.VirtualThreadEnd, 0);
            Asserts.assertEquals(10_000, startCount, "Expected 10000, got " + startCount);
            Asserts.assertEquals(10_000, endCount, "Expected 10000, got " + endCount);
        }
    }

    private static Map<String, Integer> sumEvents(Recording recording) throws Exception {
        List<RecordedEvent> events = Events.fromRecording(recording);
        return events.stream().map(RecordedEvent::getEventType)
                               .collect(Collectors.groupingBy(EventType::getName, Collectors.summingInt(x -> 1)));
    }
}
