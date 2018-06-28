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

import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.jtt.hotspot.NotOnDebug;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

public final class Monitor_contended01 extends JTTTest {

    @Rule public TestRule timeout = NotOnDebug.create(Timeout.seconds(20));

    private static class TestClass implements Runnable {
        boolean started = false;
        boolean acquired = false;

        @Override
        public void run() {
            // signal that we have started up so first thread will release lock
            synchronized (cond) {
                started = true;
                cond.notifyAll();
            }
            synchronized (obj) {

            }
            // signal that we have successfully acquired and released the monitor
            synchronized (cond) {
                acquired = true;
                cond.notifyAll();
            }
        }
    }

    static final Object cond = new Object();
    static final Object obj = new Object();

    public static boolean test() throws InterruptedException {
        // test contention for monitor
        final TestClass object = new TestClass();
        synchronized (obj) {
            new Thread(object).start();
            // wait for other thread to startup and contend
            synchronized (cond) {
                cond.wait(1000);
                if (!object.started) {
                    return false;
                }
            }
        }
        // wait for other thread to acquire monitor and then exit
        synchronized (cond) {
            cond.wait(1000);
        }
        return object.acquired;
    }

    @Test
    public void run0() throws Throwable {
        initializeForTimeout();
        runTest("test");
    }

}
