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

import org.graalvm.compiler.jtt.JTTTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

/*
 */

// Interrupted while sleeping, throws an interrupted exception
public class Thread_isInterrupted03 extends JTTTest {

    @Rule public TestRule timeout = createTimeoutSeconds(20);

    public static boolean test() throws InterruptedException {
        final Thread1 thread = new Thread1();
        thread.start();
        Thread.sleep(1000);
        thread.interrupt();
        Thread.sleep(1000);
        // Did thread get interrupted?
        final boolean result = thread.getInterrupted();
        // This stops the thread even if the interrupt didn't!
        thread.setInterrupted(true);
        return result;
    }

    private static class Thread1 extends java.lang.Thread {

        private boolean interrupted = false;

        @Override
        public void run() {
            while (!interrupted) {
                try {
                    sleep(10000);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }

        public void setInterrupted(boolean val) {
            interrupted = val;
        }

        public boolean getInterrupted() {
            return interrupted;
        }
    }

    @Test
    public void run0() throws Throwable {
        runTest("test");
    }

}
