/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
 */


package org.graalvm.compiler.jtt.threads;

import org.graalvm.compiler.core.common.CancellationBailoutException;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.nodes.Cancellable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class Object_wait03 extends JTTTest {

    /**
     * Timeout for compilation.
     */
    static final long COMPILATION_TIMEOUT_MS = 15_000;

    /**
     * Total timeout for compilation and execution of compiled code.
     */
    static final long TIMEOUT_MS = COMPILATION_TIMEOUT_MS * 2;

    @Rule public TestRule timeout = createTimeoutMillis(TIMEOUT_MS);

    private static class TestClass implements Runnable {
        @Override
        public void run() {
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException ex) {

            }
            synchronized (object) {
                done = true;
                object.notifyAll();
            }
        }
    }

    static volatile boolean done;
    static final Object object = new Object();
    static int sleep;

    public static boolean test(int i) throws InterruptedException {
        done = false;
        sleep = i * 200;
        synchronized (object) {
            new Thread(new TestClass()).start();
            dowait();
        }
        return done;
    }

    private static void dowait() throws InterruptedException {
        synchronized (object) {
            while (!done) {
                object.wait(200);
            }
        }
    }

    static class CompilationTimeout extends Thread implements Cancellable {
        boolean timedOut;
        final long durationMS;

        CompilationTimeout(long durationMS) {
            super("CompilationTimeout-" + durationMS + "ms");
            this.durationMS = durationMS;
            setDaemon(true);
            start();
        }

        @Override
        public void run() {
            try {
                Thread.sleep(durationMS);
            } catch (InterruptedException e) {
            }
            timedOut = true;
        }

        @Override
        public boolean isCancelled() {
            return timedOut;
        }
    }

    @Override
    protected Cancellable getCancellable(ResolvedJavaMethod method) {
        return new CompilationTimeout(COMPILATION_TIMEOUT_MS);
    }

    private void run(int i) throws Throwable {
        try {
            runTest("test", i);
        } catch (CancellationBailoutException e) {
            String message = String.format("Compilation cancelled after " + COMPILATION_TIMEOUT_MS + " ms");
            // For diagnosing expectedly long compilations (GR-3853)
            DebugContext debug = getDebugContext();
            debug.forceDump(lastCompiledGraph, message);
            throw new AssertionError(message, e);
        }
    }

    @Test
    public void run0() throws Throwable {
        run(0);
    }

    @Test
    public void run1() throws Throwable {
        run(1);
    }

    @Test
    public void run2() throws Throwable {
        run(2);
    }
}
