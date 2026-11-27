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
 * @summary Stress the Java and JVMTI stack walking APIs on virtual threads and
 *          their carriers around mount and unmount transitions
 * @requires vm.continuations
 * @library /test/lib
 * @run main/othervm/native -agentlib:StackTraceTransitionStressTest StackTraceTransitionStressTest
 */

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;

/*
 * Virtual threads keep mounting and unmounting: they yield, park, block on a
 * monitor and wait, some of them deep enough to be thawed lazily. Other threads
 * keep sampling them and their carriers. A transition cannot be stopped in, so
 * each sample is checked on its own: a live carrier always has frames down to its
 * run() method, and a started virtual thread down to VirtualThread.run() for the
 * Java APIs, and down to Continuation.enter() for JVMTI.
 */
public class StackTraceTransitionStressTest {
    private static final long MAX_MILLIS = 20_000;
    private static final long ENOUGH_SAMPLES = 2_000_000;
    private static final int VTHREADS = 64;

    // One round of JVMTI queries on all threads and on the given virtual threads.
    static native void sampleJvmti(Thread[] vthreads);
    static native long samples();     // checked stack traces, frame counts and frame locations
    static native long wrongSamples();
    static native String firstWrongSample();
    static native void reportWrong(String sample);

    static final Object LOCK = new Object();
    static volatile boolean stop;
    static volatile long sink;

    static void recurse(int depth, int action) throws InterruptedException {
        if (depth > 0) {
            recurse(depth - 1, action);
            return;
        }
        switch (action) {
            case 0 -> Thread.yield();
            case 1 -> LockSupport.parkNanos(20_000);
            case 2 -> {
                synchronized (LOCK) { // contended: may unmount while blocked
                    sink++;
                }
            }
            default -> {
                synchronized (LOCK) {
                    LOCK.wait(0, 20_000); // unmounts while waiting
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Thread[] vthreads = new Thread[VTHREADS];
        CountDownLatch running = new CountDownLatch(VTHREADS);
        for (int i = 0; i < VTHREADS; i++) {
            int depth = i % 8 == 0 ? 250 : 5 + i % 20;
            int first = i;
            vthreads[i] = Thread.ofVirtual().start(() -> {
                running.countDown();
                try {
                    for (int n = first; !stop; n++) {
                        recurse(depth, n % 4);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        running.await();

        long deadline = System.currentTimeMillis() + MAX_MILLIS;
        Thread[] samplers = new Thread[4];
        for (int i = 0; i < samplers.length; i++) {
            boolean java = i == 0;
            samplers[i] = Thread.ofPlatform().start(() -> {
                while (System.currentTimeMillis() < deadline && wrongSamples() == 0 && samples() < ENOUGH_SAMPLES) {
                    if (java) {
                        sampleJava(vthreads);
                    } else {
                        sampleJvmti(vthreads);
                    }
                }
            });
        }
        for (Thread t : samplers) {
            t.join();
        }
        stop = true;
        for (Thread t : vthreads) {
            t.join();
        }

        System.out.println("samples: " + samples() + ", wrong: " + wrongSamples());
        if (wrongSamples() != 0) {
            throw new RuntimeException(wrongSamples() + " wrong samples, first: " + firstWrongSample());
        }
        if (samples() == 0) {
            throw new RuntimeException("no samples");
        }
    }

    // Java APIs: Thread.getStackTrace() of each virtual thread, and Thread.getAllStackTraces().
    static void sampleJava(Thread[] vthreads) {
        for (Thread vthread : vthreads) {
            checkJava("Thread.getStackTrace", vthread.getStackTrace(), "java.lang.VirtualThread", "run");
        }
        for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
            if (e.getKey().getClass().getName().equals("jdk.internal.misc.CarrierThread") && e.getKey().isAlive()) {
                checkJava("Thread.getAllStackTraces, carrier", e.getValue(), null, "run");
            }
        }
    }

    static void checkJava(String what, StackTraceElement[] trace, String bottomClass, String bottomMethod) {
        StackTraceElement bottom = trace.length == 0 ? null : trace[trace.length - 1];
        if (bottom == null || !bottom.getMethodName().equals(bottomMethod)
            || (bottomClass != null && !bottom.getClassName().equals(bottomClass))) {
            reportWrong(what + ": " + trace.length + " frames, bottom " + bottom);
        }
    }
}
