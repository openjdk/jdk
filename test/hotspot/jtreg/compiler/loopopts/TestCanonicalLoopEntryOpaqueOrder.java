/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * @bug 8269820
 * @summary C2 PhaseIdealLoop::do_unroll get wrong opaque node
 *
 * @run main/othervm -Xbatch -XX:-TieredCompilation TestCanonicalLoopEntryOpaqueOrder
 *
 */

public class TestCanonicalLoopEntryOpaqueOrder {
    static void test() {
        int ina8[] = new int[1478];
        int in2 = 136;
        long lo3 = 0L;
        try {
            for (int i = 0; i < 34; i++) {
                Math.log1p(1);
            }
        } catch (Exception e) {
            in2 = 1;
        }

        for (int i = 0; i < in2; i++) {
            if (in2 > 10) {  // split if and create wrong opaque order
                for (int j = 0; j < in2; j++) {
                    lo3 -= 1L;
                }
            }
        }
    }
    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            test();
        }
    }
}
