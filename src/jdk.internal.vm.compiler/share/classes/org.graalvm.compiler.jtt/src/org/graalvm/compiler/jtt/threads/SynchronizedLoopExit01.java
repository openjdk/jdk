/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

/**
 * Inspired by {@code com.sun.media.sound.DirectAudioDevice$DirectDL.drain()}.
 *
 * Two loop exits hold a monitor while merging.
 *
 */
public final class SynchronizedLoopExit01 extends JTTTest {

    @Rule public TestRule timeout = createTimeoutSeconds(20);

    protected Object object = new Object();
    protected volatile boolean drained = false;
    protected volatile boolean someBoolean = true;
    protected volatile int someInt = 3;

    public boolean test() {
        boolean b = true;
        while (!drained) {
            synchronized (object) {
                boolean c = b = someBoolean;
                if (c || drained) {
                    break;
                }
            }
        }
        return b;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test");
    }

    public synchronized boolean test1() {
        boolean b = true;
        while (!drained) {
            synchronized (object) {
                boolean c = b = someBoolean;
                if (c || drained) {
                    break;
                }
            }
        }
        return b;
    }

    @Test
    public void run1() throws Throwable {
        runTest("test1");
    }

    public synchronized boolean test2() {
        boolean b = true;
        while (!drained) {
            synchronized (object) {
                boolean c = b = someBoolean;
                if (c || drained) {
                    break;
                }
                if (someInt > 0) {
                    throw new RuntimeException();
                }
            }
            if (someInt < -10) {
                throw new IndexOutOfBoundsException();
            }
        }
        if (someInt < -5) {
            throw new IllegalArgumentException();
        }
        return b;
    }

    @Test
    public void run2() throws Throwable {
        runTest("test2");
    }

}
