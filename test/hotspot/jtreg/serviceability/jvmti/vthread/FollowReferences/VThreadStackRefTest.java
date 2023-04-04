/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @requires vm.jvmti
 * @requires vm.continuations
 * @enablePreview
 * @run main/othervm/native
 *      -Djava.util.concurrent.ForkJoinPool.common.parallelism=2
 *      -agentlib:VThreadStackRefTest
 *      VThreadStackRefTest
 */

import java.lang.ref.Reference;
import java.util.stream.Stream;
import java.util.concurrent.CountDownLatch;

/*
 * The test verifies JVMTI FollowReferences function reports references from
 * mounted and unmounted virtual threads and reports correct thread id.
 * To get unmounted state virtual threads wait in CountDownLatch.await()
 * and main test thread makes a delay.
 * To get mounted state virtual threads waits in a tight loop in java
 * or in native so the threads have no chance to be unmounted.
 */
public class VThreadStackRefTest {

    static volatile boolean timeToStop = false;
    static int i = -1;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch dumpedLatch = new CountDownLatch(1);

        // Unmounted virtual thread with stack local.
        Thread vthreadUnmounted = Thread.ofVirtual().start(() -> {
            Object referenced = new VThreadUnmountedReferenced();
            await(dumpedLatch);
            Reference.reachabilityFence(referenced);
        });

        // Unmounted virtual thread with JNI local.
        Thread vthreadJNIUnmounted = Thread.ofVirtual().start(() -> {
            createObjAndCallback(VThreadUnmountedJNIReferenced.class,
                new Runnable() {
                    public void run() {
                        await(dumpedLatch);
                    }
                });
        });

        // Ended virtual thread with stack local - should not be reported.
        Thread vthreadEnded = Thread.ofVirtual().start(() -> {
            Object referenced = new VThreadUnmountedEnded();
            Reference.reachabilityFence(referenced);
        });

        // Mounted virtual thread with stack local.
        Thread vthreadMounted = Thread.ofVirtual().start(() -> {
            Object referenced = new VThreadMountedReferenced();
            while (!timeToStop) {
                if (++i == 10000) {
                    i = 0;
                }
            }
            Reference.reachabilityFence(referenced);
        });

        // Unmounted virtual thread with JNI local on top frame.
        Thread vthreadJNIMounted = Thread.ofVirtual().start(() -> {
            createObjAndWait(VThreadMountedJNIReferenced.class);
        });

        // Sanity check - reference from platform thread stack.
        Thread pthread = Thread.ofPlatform().start(() -> {
            Object referenced = new PThreadReferenced();

            await(dumpedLatch);
            Reference.reachabilityFence(referenced);
        });

        // Wait until the threads have made enough progress to create the references,
        // and have had a chance to unmount due to the await() call.
        Thread.sleep(2000);

        // Make sure this vthread has exited so we can test
        // that it no longer holds any stack references.
        vthreadEnded.join();

        TestCase[] testCases = new TestCase[] {
            new TestCase(VThreadUnmountedReferenced.class, 1, vthreadUnmounted.getId()),
            new TestCase(VThreadUnmountedJNIReferenced.class, 1, vthreadJNIUnmounted.getId()),
            new TestCase(VThreadMountedReferenced.class, 1, vthreadMounted.getId()),
            new TestCase(VThreadMountedJNIReferenced.class, 1, vthreadJNIMounted.getId()),
            new TestCase(PThreadReferenced.class, 1, pthread.getId()),
            // expected to be unreported as stack local
            new TestCase(VThreadUnmountedEnded.class, 0, 0)
        };

        Class[] testClasses = Stream.of(testCases).map(c -> c.cls()).toArray(Class[]::new);
        System.out.println("test classes:");
        for (int i = 0; i < testClasses.length; i++) {
            System.out.println("  - (" + i + ") " + testClasses[i]);
        }

        test(testClasses);

        // Finish all threads
        timeToStop = true;       // signal mounted threads to stop
        dumpedLatch.countDown(); // signal unmounted threads to stop

        vthreadMounted.join();
        vthreadJNIMounted.join();
        vthreadUnmounted.join();
        vthreadJNIUnmounted.join();
        pthread.join();

        boolean failed = false;
        for (int i = 0; i < testCases.length; i++) {
            int refCount = getRefCount(i);
            long threadId = getRefThreadID(i);
            System.out.println(" (" + i + ") " + testCases[i].cls()
                               + ": ref count = " + refCount
                               + " (expected " + testCases[i].expectedCount() + ")"
                               + ", thread id = " + threadId
                               + " (expected " + testCases[i].expectedThreadId() + ")");
            if (refCount != testCases[i].expectedCount()
                    || threadId != testCases[i].expectedThreadId()) {
                failed = true;
            }
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

    private static native void test(Class<?>... classes);
    private static native int getRefCount(int index);
    private static native long getRefThreadID(int index);

    // creates object of the the specified class (local JNI)
    // and calls the provided callback.
    private static native void createObjAndCallback(Class cls, Runnable callback);
    // creates object of the the specified class (local JNI)
    // and waits until timeToStop static field is set to true.
    private static native void createObjAndWait(Class cls);

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