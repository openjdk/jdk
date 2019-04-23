/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import org.junit.Test;

public final class SynchronizedParserInlineTest extends JTTTest {

    private static SynchronizedParserInlineTest object = new SynchronizedParserInlineTest();

    public static Integer test(boolean b) {
        foo(object);
        return b ? 42 : 1337;
    }

    @BytecodeParserForceInline
    public static synchronized void foo(SynchronizedParserInlineTest o) {
        o.notifyAll();
    }

    @Test
    public void run0() {
        runTest("test", false);
    }

    public static Integer test1(int b) {
        return foo1(b);
    }

    @BytecodeParserForceInline
    public static synchronized int foo1(int b) {
        if (b < 0) {
            return 7777;
        } else if (b > 100) {
            throw new RuntimeException();
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Test
    public void run1() {
        runTest("test1", -1);
        runTest("test1", 1);
        runTest("test1", 101);
    }

    public static Integer test2(int b) {
        return foo2(b);
    }

    @BytecodeParserForceInline
    public static int foo2(int b) {
        if (b < 0) {
            return 7777;
        } else if (b > 100) {
            throw new RuntimeException();
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Test
    public void run2() {
        runTest("test2", -1);
        runTest("test2", 1);
        runTest("test2", 101);
    }

}
