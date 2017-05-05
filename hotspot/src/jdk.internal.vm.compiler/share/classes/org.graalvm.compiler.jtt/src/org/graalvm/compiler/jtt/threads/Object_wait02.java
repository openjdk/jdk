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

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

public class Object_wait02 extends JTTTest {

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
        new Thread(new TestClass()).start();
        synchronized (object) {
            while (!done) {
                object.wait(200);
            }
        }
        return done;
    }

    @Test(timeout = 20000)
    public void run0() throws Throwable {
        runTest("test", 0);
    }

    @Test(timeout = 20000)
    public void run1() throws Throwable {
        runTest("test", 1);
    }

    @Test(timeout = 20000)
    public void run2() throws Throwable {
        runTest("test", 2);
    }

}
