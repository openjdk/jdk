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
 * @bug 8307365
 * @summary Exercise JVMTI state creation concurrently with terminating vthreads
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @run main/othervm/native -agentlib:JVMTIStateSanityTest JVMTIStateSanityTest
 */

import java.util.concurrent.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class JVMTIStateSanityTest {
    private static final String agentLib = "JVMTIStateSanityTest";
    static final int VTHREAD_COUNT = 64;

    private static native void SetSingleSteppingMode(boolean enable);
    private static native void SetMonitorContendedMode(boolean enable);

    final Runnable FOO = () -> {
        Thread.yield();
    };

    private void runTest() throws Exception {
        int tryCount = 150;

        // Force creation of JVMTI state on vthread start.
        SetMonitorContendedMode(true);

        while (tryCount-- > 0) {
            ExecutorService scheduler = Executors.newFixedThreadPool(8);
            ThreadFactory factory = virtualThreadBuilder(scheduler).factory();

            List<Thread> virtualThreads = new ArrayList<>();
            for (int i = 0; i < VTHREAD_COUNT; i++) {
                virtualThreads.add(factory.newThread(FOO));
            }

            for (Thread t : virtualThreads) {
                t.start();
            }

            // Give some time for vthreads to finish.
            Thread.sleep(10);

            // Trigger race of JVMTI state creation with terminating vthreads.
            SetMonitorContendedMode(false);
            SetMonitorContendedMode(true);

            for (Thread t : virtualThreads) {
                t.join();
            }
            // Let all carriers go away.
            scheduler.shutdown();
            Thread.sleep(20);

            // Check that looping over all JVMTI states works fine.
            SetSingleSteppingMode(true);

            // Reset for next iteration
            SetSingleSteppingMode(false);
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            System.loadLibrary(agentLib);
        } catch (UnsatisfiedLinkError ex) {
            System.err.println("Failed to load " + agentLib + " lib");
            System.err.println("java.library.path: " + System.getProperty("java.library.path"));
            throw ex;
        }
        JVMTIStateSanityTest obj = new JVMTIStateSanityTest();
        obj.runTest();
    }

    private static Thread.Builder.OfVirtual virtualThreadBuilder(Executor scheduler) {
        Thread.Builder.OfVirtual builder = Thread.ofVirtual();
        try {
            Class<?> clazz = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder");
            Constructor<?> ctor = clazz.getDeclaredConstructor(Executor.class);
            ctor.setAccessible(true);
            return (Thread.Builder.OfVirtual) ctor.newInstance(scheduler);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
