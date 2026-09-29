/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8336702
 * @summary C2 compilation fails with "all memory state should have been processed" assert
 *
 * @run main/othervm TestSafePointWithEAState
 *
 */

public class TestSafePointWithEAState {
    int[] b = new int[400];

    void c() {
        int e;
        float f;
        for (long d = 0; d < 5000; d++) {
            e = 1;
            while ((e += 3) < 200) {
                if (d < b.length) {
                    for (int g = 0; g < 10000; ++g) ;
                }
            }
            synchronized (TestSafePointWithEAState.class) {
                f = new h(e).n;
            }
        }
    }

    public static void main(String[] m) {
        TestSafePointWithEAState o = new TestSafePointWithEAState();
        o.c();
    }
}

class h {
    float n;
    h(float n) {
        this.n = n;
    }
}
