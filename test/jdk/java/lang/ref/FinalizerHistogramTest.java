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

import java.lang.ref.PhantomReference;
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
    static final AtomicInteger wasInitialized = new AtomicInteger(0);
    static final AtomicInteger wasTrapped = new AtomicInteger(0);
    static final int OBJECTS_COUNT = 1000;

    static class MyObject {
        public MyObject() {
            // Make sure object allocation/deallocation is not optimized out
            wasInitialized.incrementAndGet();
        }

        protected void finalize() {
            // Trap the object in a finalization queue
            wasTrapped.incrementAndGet();
            lock.lock();
        }
    }

    public static void main(String[] argvs) {
        try {
            lock.lock();
            final PhantomReference<MyObject> ref1 = new PhantomReference<>(new MyObject(), null);
            final PhantomReference<MyObject> ref2 = new PhantomReference<>(new MyObject(), null);
            for(int i = 2; i < OBJECTS_COUNT; ++i) {
                new MyObject();
            }
            System.out.println("Objects intialized: " + wasInitialized.get());
            // GC and wait for at least 2 MyObjects to be ready for finalization,
            // and one MyObject to be stuck in finalize().
            ForceGC.wait(() -> { return ref1.refersTo(null) &&
                                        ref2.refersTo(null) &&
                                        wasTrapped.intValue() > 0;
            });

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
}
