/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.testlibrary;

import static jdk.testlibrary.Asserts.assertNotEquals;
import static jdk.testlibrary.Asserts.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * The helper class for starting and stopping {@link Process} in a separate thread.
 */
public class ProcessThread extends TestThread {

    /**
     * Creates a new {@code ProcessThread} object.
     *
     * @param cmd The list of program and its arguments to pass to {@link ProcessBuilder}
     */
    public ProcessThread(List<String> cmd) {
        super(new ProcessRunnable(cmd));
    }

    /**
     * Creates a new {@code ProcessThread} object.
     *
     * @param cmd The string array of program and its arguments to pass to {@link ProcessBuilder}
     */
    public ProcessThread(String... cmd) {
        super(new ProcessRunnable(cmd));
    }

    /**
     * Creates a new {@code ProcessThread} object.
     *
     * @param threadName The name of thread
     * @param cmd The list of program and its arguments to pass to {@link ProcessBuilder}
     */
    public ProcessThread(String threadName, List<String> cmd) {
        super(new ProcessRunnable(cmd), threadName);
    }

    /**
     * Creates a new {@code ProcessThread} object.
     *
     * @param threadName The name of thread
     * @param cmd The string array of program and its arguments to pass to {@link ProcessBuilder}
     */
    public ProcessThread(String threadName, String... cmd) {
        super(new ProcessRunnable(cmd), threadName);
    }

    /**
     * Stops {@link Process} started by {@code ProcessRunnable}.
     *
     * @throws InterruptedException
     */
    public void stopProcess() throws InterruptedException {
        ((ProcessRunnable) getRunnable()).stopProcess();
    }

    /**
     * {@link Runnable} interface for starting and stopping {@link Process}.
     */
    static class ProcessRunnable extends XRun {

        private final ProcessBuilder processBuilder;
        private final CountDownLatch latch;
        private volatile Process process;

        /**
         * Creates a new {@code ProcessRunnable} object.
         *
         * @param cmd The list of program and its arguments to to pass to {@link ProcessBuilder}
         */
        public ProcessRunnable(List<String> cmd) {
            super();
            this.processBuilder = new ProcessBuilder(cmd);
            this.latch = new CountDownLatch(1);
        }

        /**
         * Creates a new {@code ProcessRunnable} object.
         *
         * @param cmd The string array of program and its arguments to to pass to {@link ProcessBuilder}
         */
        public ProcessRunnable(String... cmd) {
            super();
            this.processBuilder = new ProcessBuilder(cmd);
            this.latch = new CountDownLatch(1);
        }

        /**
         * Starts the process in {@code ProcessThread}.
         * All exceptions which occurs here will be caught and stored in {@code ProcessThread}.
         *
         * see {@link XRun}
         */
        @Override
        public void xrun() throws Throwable {
            this.process = processBuilder.start();
            // Release when process is started
            latch.countDown();

            // Will block...
            OutputAnalyzer output = new OutputAnalyzer(this.process);

            assertTrue(output.getOutput().isEmpty(), "Should get an empty output, got: "
                        + Utils.NEW_LINE + output.getOutput());
            assertNotEquals(output.getExitValue(), 0,
                    "Process exited with unexpected exit code");
        }

        /**
         * Stops the process.
         *
         * @throws InterruptedException
         */
        public void stopProcess() throws InterruptedException {
            // Wait until process is started
            latch.await();
            if (this.process != null) {
                this.process.destroy();
            }
        }

    }

}
