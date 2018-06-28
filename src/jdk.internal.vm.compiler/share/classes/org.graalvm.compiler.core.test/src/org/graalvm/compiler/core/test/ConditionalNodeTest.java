/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import org.junit.Test;

public class ConditionalNodeTest extends GraalCompilerTest {

    @SuppressWarnings("unused") private static int sink0;
    @SuppressWarnings("unused") private static int sink1;

    @Test
    public void test0() {
        test("conditionalTest0", 0);
        test("conditionalTest0", 1);
    }

    public static int conditionalTest0(int a) {
        int value;
        if (a == 1) {
            value = -1;
            sink1 = 0;
        } else {
            value = 6;
            sink1 = 1;
        }
        sink0 = 1;
        return Math.max(value, 6);
    }

    @Test
    public void test1() {
        test("conditionalTest1", 0);
        test("conditionalTest1", 1);
    }

    public static int conditionalTest1(int a) {
        int value;
        if (a == 1) {
            value = -1;
            sink1 = 0;
        } else {
            value = 6;
            sink1 = 1;
        }
        sink0 = 1;
        return Math.max(6, value);
    }

    @Test
    public void test2() {
        test("conditionalTest2", 0);
        test("conditionalTest2", 1);
    }

    public static int conditionalTest2(int a) {
        int value;
        if (a == 1) {
            value = -1;
            sink1 = 0;
        } else {
            value = 6;
            sink1 = 1;
        }
        sink0 = 1;
        return Math.min(value, -1);
    }

    @Test
    public void test3() {
        test("conditionalTest3", 0);
        test("conditionalTest3", 1);
    }

    public static int conditionalTest3(int a) {
        int value;
        if (a == 1) {
            value = -1;
            sink1 = 0;
        } else {
            value = 6;
            sink1 = 1;
        }
        sink0 = 1;
        return Math.min(-1, value);
    }
}
