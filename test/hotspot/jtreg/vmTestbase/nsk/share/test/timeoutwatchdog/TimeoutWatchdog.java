/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package nsk.share.test.timeoutwatchdog;

import nsk.share.test.ExecutionController;

/**
 * This class watches for ExecutionControler and notifies TimeoutHander in case of timeout.
 */
public class TimeoutWatchdog implements Runnable {

        private ExecutionController executionController;

        private TimeoutHandler handler;

        private static long CHECK_PERIOD = 1000; // In milliseconds

        private TimeoutWatchdog(ExecutionController executionController, TimeoutHandler handler) {
                this.executionController = executionController;
                this.handler = handler;
        }

        /**
         * Start watching for timeout.
         * This method runs a new daemon thread that checks periodically if the observable test is still running.
         * If timeout is detected <code>handler.handleTimeout()</code> will be called. If the test finishes normally the daemon
         * thread will silently die.
         * @param executionController - executionController used to monitor time left
         * @param handler - handler on which handleTimeout() will be called
         */
        public static void watch(ExecutionController executionController, TimeoutHandler handler) {
                Thread thread = new Thread(new TimeoutWatchdog(executionController, handler));
                thread.setName("TimeoutWatchdog_thread");
                thread.setDaemon(true);
                thread.start();
        }

        @Override
        public void run() {
                try {
                        while (true) {
                                Thread.sleep(CHECK_PERIOD);
                                if (!executionController.continueExecution()) {
                                        System.out.println("Time expired. TimeoutWatchdog is calling TimeoutHandler.handleTimeout.");
                                        handler.handleTimeout();
                                }
                        }
                } catch (InterruptedException e) {
                        throw new RuntimeException("Somebody dared to interrupt TimeoutWatchdog thread.");
                }
        }

}
