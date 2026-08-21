/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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
 * @bug JDK-8387015
 * @summary C2: crash with "named projection 2 not found" from ArrayCopyNode::finish_transform() for clone
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:CompileOnly=${test.main.class}::test1
 *                   -XX:CompileCommand=dontinline,${test.main.class}::notInlined -XX:+StressIGVN
 *                   -XX:StressSeed=1324432947 ${test.main.class}
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:CompileOnly=${test.main.class}::test1
 *                   -XX:CompileCommand=dontinline,${test.main.class}::notInlined -XX:+StressIGVN
 *                   ${test.main.class}
 */

package compiler.arraycopy;

public class TestDeadCloneMem {
    private static int field;

    public static void main(String[] args) {
        int[] array = new int[10];
        array.clone();
        Object o = new Object();
        test1(42, false);
    }

    private static int test1(int flag, boolean flag2) {
        int len;
        if (flag != 42) {
            if (flag2) {
                field = 42;
            }
            int[] array2;
            if (flag != 42) {
                len = -1;
                array2 = new int[4];
            } else {
                len = 42;
                array2 = new int[100];
            }
            int[] array = new int[len];
            int length = array.length;
            int i = 0;
            do {
                synchronized (new Object()) {}
                notInlined();
                array2.clone();
                i++;
            } while (i < 10);
            return length;
        }
        return 0;
    }

    private static void notInlined() {

    }
}
