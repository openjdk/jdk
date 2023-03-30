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
import java.util.concurrent.CountDownLatch;

public class VThreadStackRefTest {
    private static native void test(Class<?>... classes);
    private static native int getRefCount(int index);
	private static native long getRefThreadID(int index);

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

		Class[] testClasses = new Class[] {
			VThreadUnmountedEnded.class,	// expected to be unreported as stack local
			VThreadMountedReferenced.class,
			VThreadUnmountedReferenced.class,
			PThreadReferenced.class
		};
		System.out.println("test classes:");
		for (int i = 0; i < testClasses.length; i++) {
			System.out.println("  - (" + i + ") " + testClasses[i]);
		}
        test(testClasses);
		timeToStop = true;
        dumpedLatch.countDown();
        vthreadMounted.join();
        vthreadUnmounted.join();
        pthread.join();
		//verify(testClasses)
		
		for (int i = 0; i < testClasses.length; i++) {
			System.out.println(" (" + i + ") " + testClasses[i]
			                   + ": ref count = " + getRefCount(i)
							   + ", thread id = " + getRefThreadID(i));
		}
		
/*		
        if (count[0] != 1 || count[1] != 1 || count[2] != 1) {
            System.err.println("did not find expected references: VThreadMountedReferenced: " + count[0] + ", VThreadUnmountedReferenced: " + count[1] + ", PThreadReferenced: " + count[2]);
            System.exit(1);
        }
*/
    }

    private static void await(CountDownLatch dumpedLatch) {
        try {
            dumpedLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static class VThreadMountedReferenced {
    }
    public static class VThreadUnmountedReferenced {
    }
    public static class VThreadUnmountedEnded {
    }
    public static class PThreadReferenced {
    }
}