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
 *
 */

// Try to trigger concurrent dynamic-dump-at-exit processing by creating a race
// between normal VM termination by the last non-daemon thread exiting, and a
// call to Runtime.halt().

public class ExitRace {

    static volatile int terminationPhase = 0;

    public static void main(String [] args) {

        // Need to spawn a new non-daemon thread so the main thread will
        // have time to become the DestroyJavaVM thread.
        Thread runner = new Thread("Runner") {
                public void run() {
                    // This thread will be the one to trigger normal VM termination
                    // when it exits. We first create a daemon thread to call
                    // Runtime.halt() and then wait for it to tell us to exit.

                    Thread daemon = new Thread("Daemon") {
                            public void run() {
                                // Let main thread go
                                terminationPhase = 1;
                                // Spin until main thread is active again
                                while (terminationPhase == 1)
                                    ;
                                Runtime.getRuntime().halt(0); // Normal exit code
                            }
                        };
                    daemon.setDaemon(true);
                    daemon.start();

                    // Wait until daemon is ready
                    while (terminationPhase == 0) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException cantHappen) {
                        }
                    }

                    // Release daemon thread
                    terminationPhase++;
                    // Normal exit
                }
            };
        runner.start();
    }
}
