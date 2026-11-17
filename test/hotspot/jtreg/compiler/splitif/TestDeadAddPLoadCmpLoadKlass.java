/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8392714
 * @summary AddPNode killed in PhaseIdealLoop::clone_cmp_loadklass_down() not recognized as dead
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -Xbatch -XX:+StressLoopPeeling
 *                   -XX:CompileThresholdScaling=0.01
 *                   -XX:CompileOnly=${test.main.class}::test ${test.main.class}
 * @run main ${test.main.class}
 */


public class TestDeadAddPLoadCmpLoadKlass {
    Object[] oArray;
    int[] iArray;

    void test(int limit) {
        if (limit <= 0) {
            return;
        }
        if (oArray == null) {
            oArray = new int[1][];
            iArray = new int[1];
        }
        int sum = 0;
        for (int i = 0; sum < limit; i++) {
            oArray[i] = new int[i];
            iArray[i] = ++sum;
        }
    }

    public static void main(String[] args) {
        TestDeadAddPLoadCmpLoadKlass value = new TestDeadAddPLoadCmpLoadKlass();
        for (int i = 0; i <= 60; i++) {
            value = new TestDeadAddPLoadCmpLoadKlass();
            value.test(1);
        }
        for (int i = 0; i < 1000; i++) {
            value.test(1);
        }
    }
}
