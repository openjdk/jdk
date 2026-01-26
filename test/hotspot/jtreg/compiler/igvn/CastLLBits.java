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
package compiler.igvn;

/**
 * @test
 * @bug 8375618
 * @summary A CastLLNode may change only a bit of its input, which triggers the incorrect assertion
 *          that the signed range must changes
 * @run main/othervm -XX:-TieredCompilation -Xbatch ${test.main.class}
 * @run main ${test.main.class}
 */
public class CastLLBits {
    static long instanceCount;

    public static void main(String[] args) {
        for (int i = 0; i < 2000; i++) {
            test();
        }
    }

    static void test() {
        int i, i1 = 6, i9, i10, i11, i12;
        boolean b = false;
        for (i = 25; i > 5; i--) {
            i9 = 1;
            do {
                i1 += 0;
                for (i10 = 1; i10 < 3; ++i10) {
                    instanceCount = i9;
                }
                i12 = 3;
                while (--i12 > 0) {
                    i11 = (int) instanceCount;
                    i1 = i11;
                    if (b) {};
                    instanceCount &= 21;
                }
                i9++;
            } while (i9 < 9);
        }
    }
}
