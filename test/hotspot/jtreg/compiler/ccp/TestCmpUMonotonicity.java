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
package compiler.ccp;

/*
 * @test
 * @bug 8375653
 * @summary Test that CmpUNode::sub conforms monotonicity
 *
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,${test.main.class}::test* ${test.main.class}
 */
public class TestCmpUMonotonicity {
    public static void main(String[] args) {
        for (int i = 0; i < 20000; i++) {
            testInt(true, 1, 100, 2);
            testInt(false, 1, 100, 2);
            testLong(true, 1, 100, 2);
            testLong(false, 1, 100, 2);
        }
    }

    private static int testInt(boolean b, int start, int limit, int step) {
        int v2 = b ? 1 : -1;
        int v1 = 0;
        for (int i = start; i < limit; i *= step) {
            if (Integer.compareUnsigned(v1, v2) < 0) {
                v1 = 2;
            } else {
                v1 = 0;
            }
        }
        return v1;
    }

    private static long testLong(boolean b, int start, int limit, int step) {
        long v2 = b ? 1 : -1;
        long v1 = 0;
        for (int i = start; i < limit; i *= step) {
            if (Long.compareUnsigned(v1, v2) < 0) {
                v1 = 2;
            } else {
                v1 = 0;
            }
        }
        return v1;
    }
}
