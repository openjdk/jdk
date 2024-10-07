/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test id=default
 * @requires vm.jvmti
 * @requires vm.continuations
 * @run main/othervm/native
 *      -Djdk.virtualThreadScheduler.maxPoolSize=1
 *      -agentlib:VThreadStackRefTest
 *      VThreadStackRefTest
 */

/**
 * @test id=no-vmcontinuations
 * @requires vm.jvmti
 * @run main/othervm/native
 *      -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations
 *      -agentlib:VThreadStackRefTest
 *      VThreadStackRefTest NoMountCheck
 */

import java.lang.ref.Reference;
import java.util.stream.Stream;
import java.util.concurrent.CountDownLatch;

/*
 * The test verifies JVMTI FollowReferences function reports references from
 * mounted and unmounted virtual threads and reports correct thread id
 * (for mounted vthread it should be vthread id, and not carrier thread id).
 * Additionally tests that references from platform threads are reported correctly
 * and that references from terminated vthread are not reported.
 * To get both mounted and unmounted vthreads the test:
 * - limits the number of carrier threads to 1;
 * - starts vthread that creates a stack local and JNI local
 *   and then waits in CountDownLatch.await();
 * - starts another vthread that create stack local and JNI local (on top frame)
 *   and waits in native to avoid unmounting.
 */
public class VThreadStackRefTest {

    // Currently we cannot test JNI locals for unmounted threads
    // as native calls pin virtual thread.
    // TODO: revise if this changes.
    static final boolean testUnmountedJNILocals = false;

    // The flag is set by createObjAndWait method.
    static volatile boolean mountedVthreadReady = false;

    public static void main(String[] args) throws InterruptedException {
        boolean noMountCheck = args.length > 0
                               && args[0].equalsIgnoreCase("NoMountCheck");
        CountDownLatch dumpedLatch = new CountDownLatch(1);

        CountDownLatch unmountedThreadReady = new CountDownLatch(1);
        // Unmounted virtual thread with stack local.
        Thread vthreadUnmounted = Thread.ofVirtual().start(() -> {
            Object referenced = new VThreadUnmountedReferenced();
            System.out.println("created " + referenced.getClass());
            if (testUnmountedJNILocals) {
                createObjAndCallback(VThreadUnmountedJNIReferenced.class,
                    new Runnable() {
                        public void run() {
                            unmountedThreadReady.countDown();
                            await(dumpedLatch);
                        }
                    });
            } else {
                unmountedThreadReady.countDown();
                await(dumpedLatch);
            }
            Reference.reachabilityFence(referenced);
        });
        // Wait until unmounted thread is ready.
        unmountedThreadReady.await();

        // Ended virtual thread with stack local - should not be reported.
        Thread vthreadEnded = Thread.ofVirtual().start(() -> {
            Object referenced = new VThreadUnmountedEnded();
            System.out.println("created " + referenced.getClass());
            Reference.reachabilityFence(referenced);
        });
        // Make sure this vthread has exited so we can test
        // that it no longer holds any stack references.
        vthreadEnded.join();

        // Mounted virtual thread with stack local and JNI local on top frame.
        Thread vthreadMounted = Thread.ofVirtual().start(() -> {
            Object referenced = new VThreadMountedReferenced();
            System.out.println("created " + referenced.getClass());
            createObjAndWait(VThreadMountedJNIReferenced.class);
            Reference.reachabilityFence(referenced);
        });
        // Wait until mounted vthread is ready.
        while (!mountedVthreadReady) {
            Thread.sleep(10);
        }

        CountDownLatch pThreadReady = new CountDownLatch(1);
        // Sanity check - reference from platform thread stack.
        Thread pthread = Thread.ofPlatform().start(() -> {
            Object referenced = new PThreadReferenced();
            System.out.println("created " + referenced.getClass());
            pThreadReady.countDown();
            await(dumpedLatch);
            Reference.reachabilityFence(referenced);
        });
        // Wait until platform thread is ready.
        pThreadReady.await();

        System.out.println("threads:");
        System.out.println("  - vthreadUnmounted: " + vthreadUnmounted);
        System.out.println("  - vthreadEnded: " + vthreadEnded);
        System.out.println("  - vthreadMounted: " + vthreadMounted);
        System.out.println("  - pthread: " + pthread);

        TestCase[] testCases = new TestCase[] {
            new TestCase(VThreadUnmountedReferenced.class, 1, vthreadUnmounted.getId()),
            new TestCase(VThreadUnmountedJNIReferenced.class,
                         testUnmountedJNILocals ? 1 : 0,
                         testUnmountedJNILocals ? vthreadUnmounted.getId() : 0),
            new TestCase(VThreadMountedReferenced.class, 1, vthreadMounted.getId()),
            new TestCase(VThreadMountedJNIReferenced.class, 1, vthreadMounted.getId()),
            new TestCase(PThreadReferenced.class, 1, pthread.getId()),
            // expected to be unreported as stack local
            new TestCase(VThreadUnmountedEnded.class, 0, 0)
        };

        Class[] testClasses = Stream.of(testCases).map(c -> c.cls()).toArray(Class[]::new);
        System.out.println("test classes:");
        for (int i = 0; i < testClasses.length; i++) {
            System.out.println("  (" + i + ") " + testClasses[i]);
        }

        try {
            if (noMountCheck) {
                System.out.println("INFO: No mount/unmount checks");
            } else {
                verifyVthreadMounted(vthreadUnmounted, false);
                verifyVthreadMounted(vthreadMounted, true);
            }

            test(testClasses);
        } finally {
            // Finish all threads
            endWait();               // signal mounted vthread to exit
            dumpedLatch.countDown(); // signal unmounted vthread and platform thread to exit
        }

        vthreadMounted.join();
        vthreadUnmounted.join();
        pthread.join();

        boolean failed = false;
        for (int i = 0; i < testCases.length; i++) {
            int refCount = getRefCount(i);
            long threadId = getRefThreadID(i);
            String status = "OK";
            if (refCount != testCases[i].expectedCount()
                    || threadId != testCases[i].expectedThreadId()) {
                failed = true;
                status = "ERROR";
            }
            System.out.println("  (" + i + ") " + status
                               + " " + testCases[i].cls()
                               + ": ref count = " + refCount
                               + " (expected " + testCases[i].expectedCount() + ")"
                               + ", thread id = " + threadId
                               + " (expected " + testCases[i].expectedThreadId() + ")");
        }
        if (failed) {
            throw new RuntimeException("Test failed");
        }
    }

    private static void await(CountDownLatch dumpedLatch) {
        try {
            dumpedLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void verifyVthreadMounted(Thread t, boolean expectedMounted) {
        // Hacky, but simple.
        // If virtual thread is mounted, its toString() contains
        // info about carrier thread, something like
        // VirtualThread[#27]/runnable@ForkJoinPool-1-worker-1
        String s = t.toString();
        boolean mounted = t.isVirtual() && s.contains("/runnable@");
        System.out.println("Thread " + t + ": " + (mounted ? "mounted" : "unmounted"));
        if (mounted != expectedMounted) {
            throw new RuntimeException("Thread " + t + " has unexpected mount state");
        }
    }

    private static native void test(Class<?>... classes);
    private static native int getRefCount(int index);
    private static native long getRefThreadID(int index);

    // Creates object of the the specified class (local JNI)
    // and calls the provided callback.
    private static native void createObjAndCallback(Class cls, Runnable callback);
    // Creates object of the the specified class (local JNI),
    // sets mountedVthreadReady static field,
    // and then waits until endWait() method is called.
    private static native void createObjAndWait(Class cls);
    // Signals createObjAndWait() to exit.
    private static native void endWait();

    private record TestCase(Class cls, int expectedCount, long expectedThreadId) {
    }

    public static class VThreadUnmountedReferenced {
    }
    public static class VThreadUnmountedJNIReferenced {
    }
    public static class VThreadUnmountedEnded {
    }
    public static class VThreadMountedReferenced {
    }
    public static class VThreadMountedJNIReferenced {
    }
    public static class PThreadReferenced {
    }
}
