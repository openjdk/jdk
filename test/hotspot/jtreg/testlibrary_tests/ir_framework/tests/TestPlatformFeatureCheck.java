/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.cpu.features ~= ".*avx512f.*"
 * @requires os.arch=="amd64" | os.arch=="x86_64"
 * @library /test/lib /
 * @run driver ir_framework.tests.TestPlatformFeatureCheck
 */

public class TestPlatformFeatureCheck {
    private static final int SIZE = 1000;
    private static int a[] = new int[SIZE];
    private static int b[] = new int[SIZE];
    private static int res[] = new int[SIZE];

    public static void setup() {
        for (int i = 0; i < SIZE; i++) {
            a[i] = i;
            b[i] = i;
        }
    }

    public static void main(String args[]) {
        setup();
        TestFramework.runWithFlags("-XX:+UseKNLSetting");
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, "> 0"}, applyIfPlatformFeature = {"amd64", "true"})
    public static void test1() {
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] + b[i];
        }
    }

    // IR rule is enforced if all the feature conditions holds good
    @Test
    @IR(counts = {IRNode.ADD_VI, "> 0"}, applyIfPlatformFeatureAnd={"amd64", "true", "64-bit", "true"})
    public static void test2() {
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] + b[i];
        }
    }

    // IR rule is enforced if any of the feature condition holds good
    @Test
    @IR(counts = {IRNode.ADD_VI, "> 0"}, applyIfPlatformFeatureOr={"amd64", "true", "x86_64", "true"})
    public static void test3() {
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] + b[i];
        }
    }
}
