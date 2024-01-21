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
 */

package ir_framework.tests;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.driver.irmatching.IRViolationException;

/*
 * @test 8280120
 * @summary Add attribute to IR to enable/disable IR matching based on the architecture
 * @library /test/lib /
 * @run driver ir_framework.tests.TestPlatformChecks
 */

public class TestPlatformChecks {
    private static final int SIZE = 1000;
    private static int[] a = new int[SIZE];
    private static int[] b = new int[SIZE];
    private static int[] res = new int[SIZE];

    public static void setup() {
        for (int i = 0; i < SIZE; i++) {
            a[i] = i;
            b[i] = i;
        }
    }

    public static void main(String[] args) {
        setup();
        TestFramework.run();
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, "> 0"},
        applyIfPlatform = {"x64", "true"},
        applyIfCPUFeature = {"sse4.1", "true"})
    public static void test1() {
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] + b[i];
        }
    }

    // IR rule is enforced if all the platform constraints hold
    @Test
    @IR(counts = {IRNode.ADD_VI, "> 0"},
        applyIfPlatformAnd = {"x64", "true", "linux", "true"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "true"})
    public static void test2() {
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] + b[i];
        }
    }

    // IR rule is enforced if any of the platform constraints hold
    @Test
    @IR(counts = {IRNode.ADD_VI, "> 0"},
        applyIfPlatformOr = {"linux", "true", "mac", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "avx2", "true"})
    public static void test3() {
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] + b[i];
        }
    }
}
