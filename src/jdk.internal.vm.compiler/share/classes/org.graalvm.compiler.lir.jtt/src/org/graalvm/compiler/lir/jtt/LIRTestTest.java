/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.jtt;

import org.junit.Test;

import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.meta.Value;

public class LIRTestTest extends LIRTest {
    private static final LIRTestSpecification stackCopy = new LIRTestSpecification() {
        @Override
        public void generate(LIRGeneratorTool gen, Value a, Value b) {
            setOutput("a", a);
            setOutput("b", b);
            setResult(a);
        }
    };

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static int copyInt(LIRTestSpecification spec, int a, int b) {
        return a;
    }

    public static int[] testGetOutput(int a, int b, int[] out) {
        out[0] = copyInt(stackCopy, a, b);
        out[1] = getOutput(stackCopy, "a", a);
        out[2] = getOutput(stackCopy, "b", b);
        return out;
    }

    @Test
    public void runInt() throws Throwable {
        runTest("testGetOutput", Integer.MIN_VALUE, 0, supply(() -> new int[3]));
        runTest("testGetOutput", -1, Integer.MAX_VALUE, supply(() -> new int[3]));
        runTest("testGetOutput", 0, 42, supply(() -> new int[3]));
        runTest("testGetOutput", 1, -0xFFAA44, supply(() -> new int[3]));
        runTest("testGetOutput", Integer.MAX_VALUE, -42, supply(() -> new int[3]));
    }
}
