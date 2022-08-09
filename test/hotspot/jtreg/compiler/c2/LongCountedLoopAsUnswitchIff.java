/*
 * Copyright (c) 2021, Alibaba Group Holding Limited. All Rights Reserved.
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
 *
 */

/*
 * @test
 * @bug 8271203
 * @summary C2: assert(iff->Opcode() == Op_If || iff->Opcode() == Op_CountedLoopEnd || iff->Opcode() == Op_RangeCheck) failed: Check this code when new subtype is added
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:CompileOnly=compiler.c2.LongCountedLoopAsUnswitchIff::test compiler.c2.LongCountedLoopAsUnswitchIff
 */

package compiler.c2;

public class LongCountedLoopAsUnswitchIff {
    static int iArrFld[] = new int[400];

    public static void main(String[] strArr) {
        for (int i = 0; i < 10; i++) {
            test();
        }
    }

    static void test() {
        int i = 56, i2 = 22257;

        do {
            do {
                int i24 = 1;
                while (++i24 < 2) {
                }
                for (long l1 = i; l1 < 2; ++l1) {
                    iArrFld[0] += 5;
                }
            } while ((i2 -= 2) > 0);
            for (long l3 = 8; l3 < 194; ++l3) {
            }
        } while (--i > 0);
    }
}