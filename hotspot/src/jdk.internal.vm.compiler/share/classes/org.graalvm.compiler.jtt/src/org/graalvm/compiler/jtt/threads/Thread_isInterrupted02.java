/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

//Test all, mainly monitors
public class Thread_isInterrupted02 extends JTTTest {

    private static final Object start = new Object();
    private static final Object end = new Object();
    private static int waitTime;

    @SuppressWarnings("unused")
    public static boolean test(int i, int time) throws InterruptedException {
        waitTime = time;
        final Thread thread = new Thread();
        synchronized (thread) {
            // start the thread and wait for it
            thread.setDaemon(true); // in case the thread gets stuck
            thread.start();
            while (!thread.wait1Condition) {
                thread.wait(10000);
            }
        }
        synchronized (start) {
            thread.interrupt();
            thread.sentInterrupt = true;
        }
        synchronized (end) {
            while (!thread.wait2Condition) {
                end.wait(10000);
            }
        }
        return thread.interrupted;
    }

    private static class Thread extends java.lang.Thread {

        private boolean interrupted;
        private boolean sentInterrupt;
        private boolean wait1Condition;
        private boolean wait2Condition;

        @Override
        public void run() {
            try {
                synchronized (start) {
                    synchronized (this) {
                        // signal test thread that we are running
                        wait1Condition = true;
                        notify();
                    }
                    // wait for the condition, which should be interrupted
                    while (!sentInterrupt) {
                        if (waitTime == 0) {
                            start.wait();
                        } else {
                            start.wait(waitTime);
                        }
                        if (Thread.interrupted()) {
                            throw new InterruptedException();
                        }
                    }
                    Assert.fail("should not reach here - was not interrupted");
                }
            } catch (InterruptedException e) {
                // interrupted successfully.
                interrupted = true;
                synchronized (end) {
                    // notify the other thread we are done
                    wait2Condition = true;
                    end.notify();
                }
            }
        }
    }

    @Test(timeout = 20000)
    public void run0() throws Throwable {
        runTest("test", 0, 0);
    }

    @Test(timeout = 20000)
    public void run1() throws Throwable {
        runTest("test", 1, 500);
    }

}
