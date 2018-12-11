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


package org.graalvm.compiler.jtt.threads;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */

// Interrupted while running, do nothing, just set the flag and continue
// (thomaswue) This test will exercise deoptimization on HotSpot, because a volatile unloaded field is accessed.
// (thomaswue) The temporary result variable is needed, because in order to query the isInterrupted flag, the thread must be alive.
public class Thread_isInterrupted04 extends JTTTest {

    public static boolean test() throws InterruptedException {
        final Thread1 thread = new Thread1();
        thread.start();
        while (!thread.running) {
            Thread.sleep(10);
        }
        Thread.sleep(100);
        thread.interrupt();
        boolean result = thread.isInterrupted();
        thread.setStop(true);
        return result;
    }

    public static class Thread1 extends java.lang.Thread {

        private volatile boolean stop = false;
        public volatile boolean running = false;
        public long i = 0;

        @Override
        public void run() {
            running = true;
            while (!stop) {
                i++;
            }
        }

        public void setStop(boolean value) {
            stop = value;
        }

    }

    @Test
    public void run0() throws Throwable {
        runTest("test");
    }

}
