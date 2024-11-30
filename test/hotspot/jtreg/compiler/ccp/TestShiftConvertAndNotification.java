/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8313672
 * @summary Test CCP notification for value update of AndL through LShiftI and
 *          ConvI2L.
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions
 *                   -XX:RepeatCompilation=20 -XX:-TieredCompilation
 *                   -XX:+StressIGVN -Xcomp
 *                   -XX:CompileCommand=compileonly,compiler.ccp.TestShiftConvertAndNotification::test
 *                   compiler.ccp.TestShiftConvertAndNotification
 */

/*
 * @test
 * @bug 8313672
 * @summary Test CCP notification for value update of AndL through LShiftI and
 *          ConvI2L (no flags).
 * @run main compiler.ccp.TestShiftConvertAndNotification
 *
 */

package compiler.ccp;

public class TestShiftConvertAndNotification {
    static long instanceCount;
    static void test() {
        int i, i1 = 7;
        for (i = 7; i < 45; i++) {
            instanceCount = i;
            instanceCount &= i1 * i << i * Math.max(instanceCount, instanceCount);
            switch (i % 2) {
                case 8:
                    i1 = 0;
            }
        }
    }
    public static void main(String[] strArr) {
        for (int i = 0; i < 20_000; i++)
            test();
    }
}
