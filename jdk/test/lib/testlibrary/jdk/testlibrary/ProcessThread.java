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
     * @param threadName The name of thread
     * @param cmd The string array of program and its arguments to pass to {@link ProcessBuilder}
     */
    public ProcessThread(String threadName, String... cmd) {
        super(new ProcessRunnable(new ProcessBuilder(cmd)), threadName);
    }

    /**
     * Creates a new {@code ProcessThread} object.
     *
     * @param threadName The name of thread.
     * @param pb The ProcessBuilder to execute.
     */
    public ProcessThread(String threadName, ProcessBuilder pb) {
        super(new ProcessRunnable(pb), threadName);
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
     * @return The process output, or null if the process has not yet completed.
     */
    public OutputAnalyzer getOutput() {
        return ((ProcessRunnable) getRunnable()).getOutput();
    }

    /**
     * {@link Runnable} interface for starting and stopping {@link Process}.
     */
    static class ProcessRunnable extends XRun {

        private final ProcessBuilder processBuilder;
        private final CountDownLatch latch;
        private volatile Process process;
        private volatile OutputAnalyzer output;

        /**
         * Creates a new {@code ProcessRunnable} object.
         *
         * @param pb The {@link ProcessBuilder} to run.
         */
        public ProcessRunnable(ProcessBuilder pb) {
            super();
            this.processBuilder = pb;
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
            try {
                output = new OutputAnalyzer(this.process);
            } catch (Throwable t) {
                String name = Thread.currentThread().getName();
                System.out.println(String.format("ProcessThread[%s] failed: %s", name, t.toString()));
                throw t;
            } finally {
                String logMsg = ProcessTools.getProcessLog(processBuilder, output);
                System.out.println(logMsg);
            }
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
                System.out.println("ProcessThread.stopProcess() will kill process");
                this.process.destroy();
            }
        }

        /**
         * Returns the OutputAnalyzer with stdout/stderr from the process.
         * @return The process output, or null if process not completed.
         * @throws InterruptedException
         */
        public OutputAnalyzer getOutput() {
            return output;
        }
    }

}
