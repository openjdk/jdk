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
 * @bug 8341407
 * @summary C2: assert(main_limit == cl->limit() || get_ctrl(main_limit) == new_limit_ctrl) failed: wrong control for added limit
 *
 * @run main/othervm -XX:CompileCommand=compileonly,TestLimitControlWhenNoRCEliminated::* -Xcomp TestLimitControlWhenNoRCEliminated
 *
 */

public class TestLimitControlWhenNoRCEliminated {
    static long[] lArr;
    static int iFld;

    public static void main(String[] strArr) {
        try {
            test();
        } catch (NullPointerException npe) {}
    }

    static void test() {
        int x = iFld;
        int i = 1;
        do {
            lArr[i - 1] = 9;
            x += 1;
            iFld += x;
            if (x != 0) {
                A.foo();
            }
        } while (++i < 23);
    }
}

class A {
    static void foo() {
    }
}

