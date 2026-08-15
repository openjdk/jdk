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
 *
 */

/*
 * @test
 * @summary Invokes eight threads that concurrently have to resolve the same
            set of classes, thereby putting stress on the classloader and
            deadlocks will be noticed. This execution is iterated many times.
 * @library /test/lib
 * @enablePreview
 * @compile BigClassTreeClassLoader.java
 * @run junit/othervm/timeout=480 -XX:ReservedCodeCacheSize=1G runtime.valhalla.inlinetypes.classloading.ConcurrentClassLoadingTest
 */

package runtime.valhalla.inlinetypes.classloading;

import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.junit.jupiter.api.Test;

// This test makes use of BigClassTreeClassLoader. Please refer to its documentation.
class ConcurrentClassLoadingTest {
    private static final boolean DEBUG = false;
    private static final int N_ITER = 125;
    private static final int DEPTH = 100;

    @Test
    void test() throws InterruptedException {
        for (int i = 1; i <= N_ITER; i++) {
            if (DEBUG) System.out.println("Iteration " + i);
            doIteration(8);
        }
    }

    // Should crash the VM if it fails/deadlocks.
    private void doIteration(int n) throws InterruptedException {
        // Use a barrier to ensure all threads reach a certain point before calling
        // the method that defines the class (which internally calls native code).
        final CyclicBarrier barrier = new CyclicBarrier(n);
        // Every iteration has a new instance of a class loader, to make sure we
        // create unique (Class, ClassLoader) pairs to force loading.
        // We generate DEPTH fields, and they are defined in childmost class.
        var fields = new BigClassTreeClassLoader.FieldGeneration(DEPTH - 1, Optional.empty(), Optional.empty());
        // Instantiate the class generating classloader.
        final var cl = new BigClassTreeClassLoader(DEPTH, fields);
        Thread[] threads = new Thread[n];
        // Spawn all the threads with their respective worker classes.
        for (int i = 0; i < n; i++) {
            Thread thread = new Thread(() -> {
                try {
                    // Wait for all threads to reach this point.
                    barrier.await();
                    // This will trigger the generation and loading of the childmost class.
                    // That itself will trigger loading of many field value classes.
                    Class<?> workerClass = Class.forName("Gen" + (DEPTH - 1), false, cl);
                    Object worker = workerClass.getDeclaredConstructor().newInstance();
                } catch (InterruptedException | BrokenBarrierException e) {
                    throw new IllegalStateException("test setup: waiting for barrier saw error", e);
                } catch (ReflectiveOperationException e) {
                    // A ReflectiveOperationException could get thrown if
                    // something goes wrong internally. This should make the test
                    // case fail as it represents a real problem.
                    throw new IllegalStateException("reflective exception, could be an underlying bug", e);
                }
            });
            threads[i] = thread;
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
    }
}
