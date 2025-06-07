/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8351645
 * @summary C2: ExpandBitsNode::Ideal hits assert because of TOP input
 * @run main/othervm -Xbatch -Xmx128m compiler.intrinsics.Test8351645
 */

package compiler.intrinsics;

public class Test8351645 {
    public static long[] array_0 = fill(new long[10000]);
    public static long[] array_2 = fill(new long[10000]);

    public static long[] fill(long[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = 1;
        }
        return a;
    }

    public static long one = 1L;

    static final long[] GOLD = test();

    public static long[] test() {
        long[] out = new long[10000];
        for (int i = 0; i < out.length; i++) {
            long y = array_0[i] % one;
            long x = (array_2[i] | 4294967298L) << -7640610671680100954L;
            out[i] = Long.expand(y, x);
        }
        return out;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            test();
        }

        long[] res = test();
        for (int i = 0; i < 10_000; i++) {
            if (res[i] != GOLD[i]) {
                throw new RuntimeException("value mismatch: " + res[i] + " vs " + GOLD[i]);
            }
        }
    }
}
