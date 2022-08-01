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

/**
* @test
* @bug 8287517
* @summary Test bug fix for JDK-8287517 related to fuzzer test failure in x86_64
* @requires vm.compiler2.enabled
* @run main/othervm -Xcomp -XX:CompileOnly=compiler/vectorization/TestSmallVectorPopIndex.test -XX:MaxVectorSize=8 compiler.vectorization.TestSmallVectorPopIndex
*/

package compiler.vectorization;

public class TestSmallVectorPopIndex {
    private static final int count = 1000;

    private static float[] f;

    public static void main(String args[]) {
        TestSmallVectorPopIndex t = new TestSmallVectorPopIndex();
        t.test();
    }

    public TestSmallVectorPopIndex() {
        f = new float[count];
    }

    public void test() {
        for (int i = 0; i < count; i++) {
            f[i] = i * i + 100;
        }
        checkResult();
    }

    public void checkResult() {
        for (int i = 0; i < count; i++) {
            float expected = i * i  + 100;
            if (f[i] != expected) {
                throw new RuntimeException("Invalid result: f[" + i + "] = " + f[i] + " != " + expected);
            }
        }
    }
}
