/*
 * Copyright (c) 2026, Datadog, Inc. All rights reserved.
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

/*
 * @test
 * @bug 8398335
 * @summary JVMTI stack and monitor queries report the frames of a resumed
 *          continuation that are still in its stack chunk
 * @requires vm.continuations
 * @modules java.base/jdk.internal.vm
 * @library /test/lib
 * @run main/othervm/native -agentlib:GetStackTraceStackChunkTest GetStackTraceStackChunkTest
 */

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

/*
 * A continuation that yields from deep in its stack is thawed lazily when it
 * resumes: if its stack chunk is large enough, only its top frames are copied
 * back to the thread stack, and the frames below stay in the chunk until
 * they are returned to. Stack and monitor queries must still report all of
 * them, both from the thread itself and from another thread.
 */
public class GetStackTraceStackChunkTest {
    private static final ContinuationScope SCOPE = new ContinuationScope("GetStackTraceStackChunkTest") {};

    // Deep enough for a stack chunk well above the size that is thawed in full.
    private static final int DEPTH = 200;
    // recurse(INNER_DEPTH) holds INNER across the yield; runWorker() holds OUTER.
    private static final int INNER_DEPTH = 100;
    private static final Object INNER = new Object();
    private static final Object OUTER = new Object();

    // A null thread means the current thread. Frames are named "<class>.<method>".
    static native String[] stackTrace(Thread thread);
    static native int frameCount(Thread thread);
    static native String frameAt(Thread thread, int depth); // via GetFrameLocation
    static native int monitorDepth(Thread thread, Object monitor); // via GetOwnedMonitorStackDepthInfo, -1 if absent
    static native boolean ownsMonitor(Thread thread, Object monitor); // via GetOwnedMonitorInfo
    static native int entryCount(Object monitor); // via GetObjectMonitorUsage
    static native void suspend(Thread thread);
    static native void resume(Thread thread);

    static String[] before;
    static String[] after;
    static final CountDownLatch resumed = new CountDownLatch(1);
    static final CountDownLatch inspected = new CountDownLatch(1);

    static void recurse(int depth) throws InterruptedException {
        if (depth == INNER_DEPTH) {
            synchronized (INNER) {
                recurse(depth - 1);
            }
            return;
        }
        if (depth > 0) {
            recurse(depth - 1);
            return;
        }
        before = sample("before the yield", null);
        Continuation.yield(SCOPE);
        // Resumed: the frames below this one are still in the stack chunk.
        after = sample("after resuming", null);
        resumed.countDown();
        inspected.await(); // the main thread inspects this thread while it is here
    }

    static void runWorker() {
        Continuation cont = new Continuation(SCOPE, () -> {
            try {
                recurse(DEPTH);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        synchronized (OUTER) {
            cont.run(); // runs until the yield
            cont.run(); // resumes and completes
        }
        if (!cont.isDone()) {
            throw new RuntimeException("continuation did not complete");
        }
    }

    public static void main(String[] args) throws Exception {
        Throwable[] failure = new Throwable[1];
        Thread worker = new Thread(() -> {
            try {
                runWorker();
            } catch (Throwable t) {
                failure[0] = t;
            } finally {
                resumed.countDown(); // do not leave the main thread waiting if the worker fails early
            }
        }, "worker");
        worker.start();

        resumed.await();
        String[] remote;
        try {
            if (failure[0] != null) {
                throw new RuntimeException("worker failed", failure[0]);
            }
            // Suspended, so that its frames above recurse(0) cannot change between the queries.
            suspend(worker);
            try {
                remote = sample("from another thread", worker);
            } finally {
                resume(worker);
            }
        } finally {
            inspected.countDown();
        }
        worker.join();
        if (failure[0] != null) {
            throw new RuntimeException("worker failed", failure[0]);
        }

        if (before.length < DEPTH) {
            throw new RuntimeException("expected at least " + DEPTH + " frames before the yield, got " + before.length);
        }
        checkSameFrames("after resuming", before, after);
        // The worker is parked above recurse(0); compare the frames from recurse(0) down.
        checkSameFrames("from another thread", fromRecurse(before), fromRecurse(remote));
        System.out.println("frames before yield: " + before.length + ", after: " + after.length
                           + ", from another thread: " + remote.length);
    }

    // Returns the GetStackTrace frames and checks that the other queries agree with them.
    // All are called from this frame, so their depths line up; for the current thread,
    // depth 0 is the native method making the call and is not compared.
    static String[] sample(String when, Thread thread) {
        String[] trace = stackTrace(thread);
        int count = frameCount(thread);
        if (count != trace.length) {
            throw new RuntimeException(when + ": GetFrameCount " + count + " != GetStackTrace depth " + trace.length);
        }
        for (int depth = thread == null ? 1 : 0; depth < trace.length; depth++) {
            String frame = frameAt(thread, depth);
            if (!trace[depth].equals(frame)) {
                throw new RuntimeException(when + ": GetFrameLocation at depth " + depth + " is " + frame
                                           + ", GetStackTrace has " + trace[depth]);
            }
        }
        int innerDepth = monitorDepth(thread, INNER);
        int outerDepth = monitorDepth(thread, OUTER);
        int recurse0 = Arrays.asList(trace).indexOf("GetStackTraceStackChunkTest.recurse");
        checkMonitor(when, thread, "INNER", INNER, innerDepth, recurse0 < 0 ? -1 : recurse0 + INNER_DEPTH);
        checkMonitor(when, thread, "OUTER", OUTER, outerDepth,
                     Arrays.asList(trace).indexOf("GetStackTraceStackChunkTest.runWorker"));
        return trace;
    }

    static void checkMonitor(String when, Thread thread, String name, Object monitor, int depth, int expectedDepth) {
        if (expectedDepth < 0 || depth != expectedDepth) {
            throw new RuntimeException(when + ": GetOwnedMonitorStackDepthInfo reports " + name + " at depth " + depth
                                       + ", the frame holding it is at depth " + expectedDepth);
        }
        if (!ownsMonitor(thread, monitor)) {
            throw new RuntimeException(when + ": GetOwnedMonitorInfo does not report " + name);
        }
        int count = entryCount(monitor);
        if (count != 1) {
            throw new RuntimeException(when + ": GetObjectMonitorUsage reports entry_count " + count + " for " + name);
        }
    }

    static String[] fromRecurse(String[] trace) {
        int top = Arrays.asList(trace).indexOf("GetStackTraceStackChunkTest.recurse");
        if (top < 0) {
            throw new RuntimeException("no recurse frame in " + Arrays.toString(trace));
        }
        return Arrays.copyOfRange(trace, top, trace.length);
    }

    static void checkSameFrames(String when, String[] expected, String[] actual) {
        int n = Math.min(expected.length, actual.length);
        int i = 0;
        while (i < n && expected[i].equals(actual[i])) {
            i++;
        }
        if (i < n || expected.length != actual.length) {
            throw new RuntimeException(when + ": " + actual.length + " frames instead of " + expected.length
                                       + ", first difference at depth " + i + ": "
                                       + (i < actual.length ? actual[i] : "<none>") + " instead of "
                                       + (i < expected.length ? expected[i] : "<none>"));
        }
    }
}
