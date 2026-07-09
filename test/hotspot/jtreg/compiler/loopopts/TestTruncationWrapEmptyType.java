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

package compiler.loopopts;

/*
 * @test
 * @bug 8386482
 * @summary Test case for CountedLoopConverter::filtered_type_from_dominators/
 *          CountedLoopConverter::has_truncation_wrap where the type becomes
 *          empty / top. This used to trigger the assert:
 *          assert(_base == Int) failed: Not an Int
 * @library /test/lib /
 * @run main/othervm -Xcomp
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

public class TestTruncationWrapEmptyType {
    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            test(1);
        }
    }

    static int test(int init) {
        if (init < 1) {
            return -1;
        }
        // if implies: init in [1..max_int]

        int i = init;
        while (i < 0) {
            // while implies: init in [min_int .. 0]
            // That contradicts the "if" above: empty intersection
            //
            // See CountedLoopConverter::filtered_type_from_dominators:
            //   rtn_t = rtn_t->join(if_t)->is_int()
            // -> top type in join, fails "is_int".
            i = (short)(i + 1);
        }
        return 0;
    }
}
