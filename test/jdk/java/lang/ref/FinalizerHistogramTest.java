/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.util.ForceGC;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import java.lang.reflect.Method;
import java.lang.reflect.Field;

/*
 * @test
 * @summary Unit test for FinalizerHistogram
 * @modules java.base/java.lang.ref:open
 * @library /test/lib
 * @build jdk.test.lib.util.ForceGC
 * @run main/othervm FinalizerHistogramTest
 */

public class FinalizerHistogramTest {
    static ReentrantLock lock = new ReentrantLock();
    static final AtomicInteger initializedCount = new AtomicInteger(0);
    static final AtomicInteger trappedCount = new AtomicInteger(0);
    static final int OBJECTS_COUNT = 1000;

    static RefQForTwo refQForTwo;

    static class MyObject {
        public MyObject() {
            // Make sure object allocation/deallocation is not optimized out
            initializedCount.incrementAndGet();
        }

        protected void finalize() {
            // Trap the object in a finalization queue
            trappedCount.incrementAndGet();
            lock.lock();
        }
    }

    public static void main(String[] argvs) {
        try {
            lock.lock();
            refQForTwo = new RefQForTwo(new MyObject(), new MyObject());
            for(int i = 2; i < OBJECTS_COUNT; ++i) {
                new MyObject();
            }
            System.out.println("Objects intialized: " + initializedCount.get());
            // GC and wait for at least 2 MyObjects to be ready for finalization,
            // and one MyObject to be trapped in finalize().
            boolean forceGCResult = ForceGC.waitFor(() -> refQForTwo.bothRefsCleared() &&
                                                          trappedCount.intValue() > 0,
                    Duration.ofSeconds(60).toMillis());
            if (!forceGCResult) {
                System.out.println("*** ForceGC condition not met! ***");
            }
            System.out.println("ref1Cleared: " + refQForTwo.ref1Cleared);
            System.out.println("ref2Cleared: " + refQForTwo.ref2Cleared);
            System.out.println("trappedCount.intValue(): " + trappedCount.intValue());

            Class<?> klass = Class.forName("java.lang.ref.FinalizerHistogram");

            Method m = klass.getDeclaredMethod("getFinalizerHistogram");
            m.setAccessible(true);
            Object entries[] = (Object[]) m.invoke(null);

            Class<?> entryKlass = Class.forName("java.lang.ref.FinalizerHistogram$Entry");
            Field name = entryKlass.getDeclaredField("className");
            name.setAccessible(true);
            Field count = entryKlass.getDeclaredField("instanceCount");
            count.setAccessible(true);

            System.out.println("Unreachable instances waiting for finalization");
            System.out.println("#instances  class name");
            System.out.println("-----------------------");

            boolean found = false;
            for (Object entry : entries) {
                Object e = entryKlass.cast(entry);
                System.out.printf("%10d %s\n", count.get(e), name.get(e));
                if (((String) name.get(e)).indexOf("MyObject") != -1 ) {
                    found = true;
                }
            }

            if (!found) {
                throw new RuntimeException("MyObject is not found in test output");
            }

            System.out.println("Test PASSED");
        } catch(Exception e) {
           System.err.println("Test failed with " + e);
           e.printStackTrace(System.err);
           throw new RuntimeException("Test failed");
        } finally {
            lock.unlock();
        }
    }

    private static class RefQForTwo implements Runnable {
        final ReferenceQueue queue;
        final WeakReference ref1;
        final WeakReference ref2;
        volatile boolean ref1Cleared = false;
        volatile boolean ref2Cleared = false;
        Thread thread;

        private RefQForTwo(Object obj1, Object obj2) {
            queue = new ReferenceQueue();
            ref1 = new WeakReference(obj1, queue);
            ref2 = new WeakReference(obj2, queue);

            thread = new Thread(this, "RefQForTwo thread");
            thread.start();
        }

        @Override
        public void run() {
            try {
                while (!bothRefsCleared()) {
                    Reference ref = queue.remove();
                    if (ref == ref1) { ref1Cleared = true; }
                    else if (ref == ref2) { ref2Cleared = true; }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean bothRefsCleared() { return ref1Cleared && ref2Cleared; }
    }
}
