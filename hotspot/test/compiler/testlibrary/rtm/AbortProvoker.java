/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package rtm;

import java.util.Objects;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import com.oracle.java.testlibrary.Asserts;
import com.oracle.java.testlibrary.Utils;
import sun.misc.Unsafe;

/**
 * Base class for different transactional execution abortion
 * provokers aimed to force abort due to specified reason.
 */
public abstract class AbortProvoker implements CompilableTest {
    public static final long DEFAULT_ITERATIONS = 10000L;
    /**
     * Inflates monitor associated with object {@code monitor}.
     * Inflation is forced by entering the same monitor from
     * two different threads.
     *
     * @param monitor monitor to be inflated.
     * @return inflated monitor.
     * @throws Exception if something went wrong.
     */
    public static Object inflateMonitor(Object monitor) throws Exception {
        Unsafe unsafe = Utils.getUnsafe();
        CyclicBarrier barrier = new CyclicBarrier(2);

        Runnable inflatingRunnable = () -> {
            unsafe.monitorEnter(monitor);
            try {
                barrier.await();
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new RuntimeException(
                        "Synchronization issue occurred.", e);
            } finally {
                unsafe.monitorExit(monitor);
            }
        };

        Thread t = new Thread(inflatingRunnable);
        t.start();
        // Wait until thread t enters the monitor.
        barrier.await();
        // At this point monitor will be owned by thread t,
        // so our attempt to enter the same monitor will force
        // monitor inflation.
        Asserts.assertFalse(unsafe.tryMonitorEnter(monitor),
                            "Not supposed to enter the monitor first");
        barrier.await();
        t.join();
        return monitor;
    }


    /**
     * Get instance of specified AbortProvoker, inflate associated monitor
     * if needed and then invoke forceAbort method in a loop.
     *
     * Usage:
     * AbortProvoker &lt;AbortType name&gt; [&lt;inflate monitor&gt
     * [&lt;iterations&gt; [ &lt;delay&gt;]]]
     *
     *  Default parameters are:
     *  <ul>
     *  <li>inflate monitor = <b>true</b></li>
     *  <li>iterations = {@code AbortProvoker.DEFAULT_ITERATIONS}</li>
     *  <li>delay = <b>0</b></li>
     *  </ul>
     */
    public static void main(String args[]) throws Throwable {
        Asserts.assertGT(args.length, 0, "At least one argument is required.");

        AbortType abortType = AbortType.lookup(Integer.valueOf(args[0]));
        boolean monitorShouldBeInflated = true;
        long iterations = AbortProvoker.DEFAULT_ITERATIONS;

        if (args.length > 1) {
            monitorShouldBeInflated = Boolean.valueOf(args[1]);

            if (args.length > 2) {
                iterations = Long.valueOf(args[2]);

                if (args.length > 3) {
                    Thread.sleep(Integer.valueOf(args[3]));
                }
            }
        }

        AbortProvoker provoker = abortType.provoker();

        if (monitorShouldBeInflated) {
            provoker.inflateMonitor();
        }

        for (long i = 0; i < iterations; i++) {
            provoker.forceAbort();
        }
    }

    protected final Object monitor;

    protected AbortProvoker() {
        this(new Object());
    }

    protected AbortProvoker(Object monitor) {
        this.monitor = Objects.requireNonNull(monitor);
    }

    /**
     * Inflates monitor used by this AbortProvoker instance.
     * @throws Exception
     */
    public void inflateMonitor() throws Exception {
        AbortProvoker.inflateMonitor(monitor);
    }

    /**
     * Forces transactional execution abortion.
     */
    public abstract void forceAbort();

    /**
     * Returns names of all methods that have to be compiled
     * in order to successfully force transactional execution
     * abortion.
     *
     * @return array with methods' names that have to be compiled.
     */
    @Override
    public String[] getMethodsToCompileNames() {
        return new String[] { getMethodWithLockName() };
    }

    /**
     * Returns name of the method that will contain monitor whose locking
     * will be elided using transactional execution.
     *
     * @return name of the method that will contain elided lock.
     */
    @Override
    public String getMethodWithLockName() {
        return this.getClass().getName() + "::forceAbort";
    }
}
