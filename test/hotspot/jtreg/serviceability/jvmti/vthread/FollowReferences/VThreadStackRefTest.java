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
 *      -Djava.util.concurrent.ForkJoinPool.common.parallelism=1
 *      -agentlib:VThreadStackRefTest
 *      VThreadStackRefTest
 */
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;
import java.util.concurrent.CountDownLatch;

public class VThreadStackRefTest {

	static volatile boolean timeToStop = false;
	static int i = -1;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch dumpedLatch = new CountDownLatch(1);
        Thread vthreadMounted = Thread.ofVirtual().start(() -> {
            Object referenced = new VThreadMountedReferenced();
            System.out.println(referenced.getClass());
			while (!timeToStop) {
				if (++i == 10000) {
					i = 0;
				}
			}
            await(dumpedLatch);
            System.out.println(referenced.getClass());
        });
        Thread vthreadUnmounted = Thread.ofVirtual().start(() -> {
            Object referenced = new VThreadUnmountedReferenced();
            System.out.println(referenced.getClass());
            await(dumpedLatch);
            System.out.println(referenced.getClass());
        });
        Thread vthreadJNIUnmounted = Thread.ofVirtual().start(() -> {
			createObjAndCallback(VThreadUnmountedJNIReferenced.class,
			    new Runnable() {
					public void run() {
						await(dumpedLatch);
					}
				});
        });

        Thread vthreadEnded = Thread.ofVirtual().start(() -> {
            Object referenced = new VThreadUnmountedEnded();
            System.out.println(referenced.getClass());
        });
        Thread pthread = Thread.ofPlatform().start(() -> {
            Object referenced = new PThreadReferenced();
            System.out.println(referenced.getClass());

            await(dumpedLatch);
            System.out.println(referenced.getClass());
        });
        vthreadEnded.join();

        Thread.sleep(2000); // wait for reference and unmount

		TestCase[] testCases = new TestCase[] {
			new TestCase(VThreadMountedReferenced.class, 1, vthreadMounted.getId()),
			new TestCase(VThreadUnmountedReferenced.class, 1, vthreadUnmounted.getId()),
			new TestCase(VThreadUnmountedJNIReferenced.class, 1, vthreadJNIUnmounted.getId()),
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
		timeToStop = true;
        dumpedLatch.countDown();
        vthreadMounted.join();
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

    // creates object of the the specified class (local JNI) and calls the provided callback.
    private static native void createObjAndCallback(Class cls, Runnable callback);


    private record TestCase(Class cls, int expectedCount, long expectedThreadId) {
    }

    public static class VThreadMountedReferenced {
    }
    public static class VThreadUnmountedReferenced {
    }
    public static class VThreadUnmountedJNIReferenced {
    }
    public static class VThreadUnmountedEnded {
    }
    public static class PThreadReferenced {
    }
}