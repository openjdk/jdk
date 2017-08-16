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

public class Object_wait01 extends JTTTest {

    @Rule public TestRule timeout = NotOnDebug.create(Timeout.seconds(20));

    private static class TestClass implements Runnable {
        @Override
        public void run() {
            int i = 0;
            while (i++ < 1000000 && !done) {
                synchronized (object) {
                    count++;
                    object.notifyAll();
                }
            }
        }
    }

    static volatile int count = 0;
    static volatile boolean done;
    static final Object object = new Object();

    public static boolean test(int i) throws InterruptedException {
        count = 0;
        done = false;
        new Thread(new TestClass()).start();
        synchronized (object) {
            while (count < i) {
                object.wait();
            }
            done = true;
            return count >= i;
        }
    }

    @Test
    public void run0() throws Throwable {
        initializeForTimeout();
        runTest("test", 0);
    }

    @Test
    public void run1() throws Throwable {
        initializeForTimeout();
        runTest("test", 1);
    }

    @Test
    public void run2() throws Throwable {
        initializeForTimeout();
        runTest("test", 3);
    }

    @Test
    public void run3() throws Throwable {
        initializeForTimeout();
        runTest("test", 15);
    }

}
