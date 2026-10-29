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
 * @test id=hybrid
 * @summary Stack traces requested with the JVMTI RequestStackTrace extension are
 *          written into agent buffers, without any JFR recording: small agent
 *          frame storage, with VM-allocated overflow for deep stacks. Two agents
 *          (JVMTI environments) use the facility independently.
 * @requires vm.hasJFR & vm.jvmti & os.family != "windows"
 * @library /test/lib
 * @run main/othervm/native -agentlib:RequestStackTraceBuffers=hybrid TestRequestStackTraceBuffers hybrid
 */

/*
 * @test id=vm
 * @summary The VM allocates all frames.
 * @requires vm.hasJFR & vm.jvmti & os.family != "windows"
 * @library /test/lib
 * @run main/othervm/native -agentlib:RequestStackTraceBuffers=vm TestRequestStackTraceBuffers vm
 */

/*
 * @test id=inline
 * @summary The agent provides all frame storage; deep stacks are truncated.
 * @requires vm.hasJFR & vm.jvmti & os.family != "windows"
 * @library /test/lib
 * @run main/othervm/native -agentlib:RequestStackTraceBuffers=inline TestRequestStackTraceBuffers inline
 */

/*
 * @test id=poll
 * @summary The agent polls buffer status instead of using a completion list.
 * @requires vm.hasJFR & vm.jvmti & os.family != "windows"
 * @library /test/lib
 * @run main/othervm/native -agentlib:RequestStackTraceBuffers=poll TestRequestStackTraceBuffers poll
 */

/*
 * @test id=with-cpu-time-sampling
 * @summary While JFR CPU-time sampling uses the same stack walker.
 * @requires vm.hasJFR & vm.jvmti & os.family == "linux"
 * @library /test/lib
 * @run main/othervm/native -agentlib:RequestStackTraceBuffers=hybrid
 *      -XX:StartFlightRecording=settings=none,jdk.CPUTimeSample#enabled=true,jdk.CPUTimeSample#throttle=1ms
 *      TestRequestStackTraceBuffers hybrid
 */

/*
 * @test id=interpreter
 * @summary With the interpreter only, so requests are processed in its leaf hooks.
 * @requires vm.hasJFR & vm.jvmti & os.family != "windows"
 * @library /test/lib
 * @run main/othervm/native -agentlib:RequestStackTraceBuffers=hybrid -Xint TestRequestStackTraceBuffers hybrid
 */

/*
 * @test id=zgc
 * @requires vm.hasJFR & vm.jvmti & os.family != "windows" & vm.gc.Z
 * @library /test/lib
 * @run main/othervm/native -agentlib:RequestStackTraceBuffers=hybrid -XX:+UseZGC TestRequestStackTraceBuffers hybrid
 */

/*
 * @test id=shenandoah
 * @requires vm.hasJFR & vm.jvmti & os.family != "windows" & vm.gc.Shenandoah
 * @library /test/lib
 * @run main/othervm/native -agentlib:RequestStackTraceBuffers=hybrid -XX:+UseShenandoahGC TestRequestStackTraceBuffers hybrid
 */

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import jdk.test.lib.Asserts;

public class TestRequestStackTraceBuffers {
    static final int KIND_JNI = 0, KIND_SIGNAL = 1, KIND_FOREIGN = 2, KIND_DETACH = 3, KIND_SLEEPER = 4, KIND_DEPTH = 5, KINDS = 4;
    static final int ACCEPTED = 0, DROPPED_AT_SUBMIT = 1, NO_BUFFER = 2, DONE = 3, FAILED = 4, DROPPED = 5,
                     MATCHED = 6, TRUNCATED = 7, OVERFLOWED = 8, WRONG_THREAD = 9, MISROUTED = 10, BAD_DEPTH = 11;
    static final int JVMTI_ERROR_NONE = 0;
    static final String[] KIND_NAMES = { "jni", "signal", "foreign", "detach", "sleeper" };
    static final int JVMTI_ERROR_NOT_AVAILABLE = 98;
    static final int REQUESTS = 200;
    // Deeper than the inline buffers of the "inline" and "hybrid" modes.
    static final int DEPTH = 100;

    static native void startDrainer();
    static native void stopDrainer();
    static native void requestCurrent(int n, long tid);
    static native void registerWorker(long tid);
    static native void signalWorker(int n);
    static native void requestOther(int profiler, Thread thread, int n, int kind);
    static native void requestFromDetachingThread(int n);
    static native void requestWithDepth(Thread thread, int depth, int n);
    static native int disable(int profiler);
    static native int dispose(int profiler);
    static native int requestOnce(int profiler);
    static native int outstanding(int profiler);
    static native int count(int profiler, int kind, int counter);

    static int count(int kind, int counter) {
        return count(0, kind, counter);
    }

    static final AtomicBoolean stop = new AtomicBoolean();
    static volatile double sink;

    static void recurse(int depth, Runnable action) {
        if (depth > 0) {
            recurse(depth - 1, action);
        } else {
            action.run();
        }
    }

    static void spin() {
        double d = 1;
        while (!stop.get()) {
            for (int i = 0; i < 10_000; i++) {
                d = Math.sqrt(d * Math.pow(Math.sqrt(Math.random()), Math.random()) + 1);
            }
        }
        sink = d;
    }

    static void nativeCaller() {
        requestCurrent(REQUESTS, Thread.currentThread().threadId());
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        startDrainer();

        CountDownLatch registered = new CountDownLatch(1);
        Thread worker = new Thread(() -> recurse(DEPTH, () -> {
            registerWorker(Thread.currentThread().threadId());
            registered.countDown();
            spin();
        }), "RequestStackTrace-Worker");
        worker.start();
        registered.await();
        Thread.sleep(100);

        recurse(DEPTH, TestRequestStackTraceBuffers::nativeCaller);
        signalWorker(REQUESTS);
        requestOther(0, worker, REQUESTS, KIND_FOREIGN);
        requestFromDetachingThread(20);

        awaitCompletion();
        int truncated = 0, overflowed = 0, done = 0;
        for (int kind = 0; kind < KINDS; kind++) {
            String name = KIND_NAMES[kind];
            int accepted = count(kind, ACCEPTED);
            int completed = count(kind, DONE) + count(kind, FAILED) + count(kind, DROPPED);
            System.out.println(name + ": accepted=" + accepted + " dropped-at-submit=" + count(kind, DROPPED_AT_SUBMIT)
                    + " no-buffer=" + count(kind, NO_BUFFER) + " done=" + count(kind, DONE)
                    + " failed=" + count(kind, FAILED) + " dropped=" + count(kind, DROPPED)
                    + " matched=" + count(kind, MATCHED) + " truncated=" + count(kind, TRUNCATED)
                    + " overflowed=" + count(kind, OVERFLOWED) + " wrong-thread=" + count(kind, WRONG_THREAD));
            Asserts.assertGreaterThan(accepted, 0, name + ": no request was accepted");
            Asserts.assertEquals(accepted, completed, name + ": every accepted request must complete exactly once");
            Asserts.assertEquals(count(kind, WRONG_THREAD), 0, name + ": wrong thread id");
            if (kind != KIND_DETACH) {
                Asserts.assertGreaterThan(count(kind, DONE), 0, name + ": no stack trace");
                Asserts.assertGreaterThanOrEqual(count(kind, MATCHED), count(kind, DONE) / 2,
                        name + ": expected frame missing in most stack traces");
                truncated += count(kind, TRUNCATED);
                overflowed += count(kind, OVERFLOWED);
                done += count(kind, DONE);
            }
        }
        switch (mode) {
            case "vm"     -> Asserts.assertEquals(overflowed, done, "all frames should be VM-allocated");
            case "inline" -> {
                Asserts.assertEquals(overflowed, 0, "no VM allocation expected");
                Asserts.assertGreaterThan(truncated, 0, "deep stacks should be truncated");
            }
            default       -> {
                Asserts.assertGreaterThan(overflowed, 0, "deep stacks should overflow");
                Asserts.assertEquals(truncated, 0, "nothing should be truncated");
            }
        }

        // max_depth is a limit independent of frame_capacity: 0 and a value below
        // the agent's storage report that many frames, truncated, without overflow.
        for (int depth : new int[] { 0, 4 }) {
            requestWithDepth(worker, depth, 20);
            awaitCompletion();
        }
        System.out.println("depth: accepted=" + count(KIND_DEPTH, ACCEPTED) + " done=" + count(KIND_DEPTH, DONE)
                + " truncated=" + count(KIND_DEPTH, TRUNCATED) + " bad=" + count(KIND_DEPTH, BAD_DEPTH));
        Asserts.assertGreaterThan(count(KIND_DEPTH, ACCEPTED), 0, "depth: no request was accepted");
        Asserts.assertEquals(count(KIND_DEPTH, DONE), count(KIND_DEPTH, ACCEPTED), "depth: all should be done");
        Asserts.assertEquals(count(KIND_DEPTH, TRUNCATED), count(KIND_DEPTH, DONE), "depth: all should be truncated");
        Asserts.assertEquals(count(KIND_DEPTH, BAD_DEPTH), 0, "depth: limit not respected");

        // The second agent works independently of the first one.
        requestOther(1, worker, REQUESTS, KIND_FOREIGN);
        awaitCompletion();
        Asserts.assertGreaterThan(count(1, KIND_FOREIGN, DONE), 0, "second agent: no stack trace");
        Asserts.assertEquals(count(1, KIND_FOREIGN, ACCEPTED), count(1, KIND_FOREIGN, DONE)
                + count(1, KIND_FOREIGN, FAILED) + count(1, KIND_FOREIGN, DROPPED), "second agent: incomplete");

        // Requests for a sleeping thread stay queued: it does not poll, and is not in native.
        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(600_000);
            } catch (InterruptedException e) {
            }
        }, "RequestStackTrace-Sleeper");
        sleeper.start();
        while (sleeper.getState() != Thread.State.TIMED_WAITING) {
            Thread.sleep(10);
        }
        // The agents share the thread's request queue (initially 20 entries), so stay below it.
        requestOther(0, sleeper, 8, KIND_SLEEPER);
        requestOther(1, sleeper, 8, KIND_SLEEPER);
        Asserts.assertEquals(count(0, KIND_SLEEPER, ACCEPTED), 8, "first agent: sleeper requests not accepted");
        Asserts.assertEquals(count(1, KIND_SLEEPER, ACCEPTED), 8, "second agent: sleeper requests not accepted");
        Asserts.assertEquals(outstanding(0), count(0, KIND_SLEEPER, ACCEPTED), "first agent: sleeper requests outstanding");
        Asserts.assertEquals(outstanding(1), count(1, KIND_SLEEPER, ACCEPTED), "second agent: sleeper requests outstanding");

        // Disabling the first agent completes only its requests, and only it gets NOT_AVAILABLE.
        Asserts.assertEquals(disable(0), 0, "disable failed");
        Asserts.assertEquals(requestOnce(0), JVMTI_ERROR_NOT_AVAILABLE, "first agent: request after disable");
        Asserts.assertEquals(requestOnce(1), JVMTI_ERROR_NONE, "second agent: request after the first disabled");
        awaitOutstanding(0, 0);
        Asserts.assertEquals(count(0, KIND_SLEEPER, DROPPED), count(0, KIND_SLEEPER, ACCEPTED),
                "first agent: queued requests must be dropped by disable");
        Asserts.assertEquals(count(1, KIND_SLEEPER, DROPPED), 0, "second agent: requests dropped by the first's disable");
        Asserts.assertGreaterThanOrEqual(outstanding(1), count(1, KIND_SLEEPER, ACCEPTED),
                "second agent: sleeper requests must still be outstanding");

        // Disposing the second agent's environment disables it implicitly.
        Asserts.assertEquals(dispose(1), 0, "dispose failed");
        stopDrainer();
        Asserts.assertEquals(outstanding(0), 0, "first agent: requests outstanding");
        Asserts.assertEquals(outstanding(1), 0, "second agent: requests outstanding after dispose");
        Asserts.assertEquals(count(1, KIND_SLEEPER, DROPPED), count(1, KIND_SLEEPER, ACCEPTED),
                "second agent: queued requests must be dropped by dispose");
        for (int profiler = 0; profiler < 2; profiler++) {
            Asserts.assertEquals(count(profiler, 0, MISROUTED), 0, "buffer returned to the wrong agent");
        }
        System.out.println("isolation: first agent dropped " + count(0, KIND_SLEEPER, DROPPED)
                + " sleeper requests at disable; second agent dropped " + count(1, KIND_SLEEPER, DROPPED) + " at dispose");
        sleeper.interrupt();
        sleeper.join();

        stop.set(true);
        worker.join();
    }

    static void awaitCompletion() throws InterruptedException {
        awaitOutstanding(0, 0);
        awaitOutstanding(1, 0);
    }

    static void awaitOutstanding(int profiler, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (outstanding(profiler) > expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }
}
