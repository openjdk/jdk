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
 * @bug 8356647
 * @summary C2's unrolling code has a too strict assert when a counted loop's range is as wide as int's.
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.loopopts.UnrollWideLoopHitsTooStrictAssert::test -Xcomp
 *                   compiler.loopopts.UnrollWideLoopHitsTooStrictAssert
 * @run main compiler.loopopts.UnrollWideLoopHitsTooStrictAssert
 */

package compiler.loopopts;

public class UnrollWideLoopHitsTooStrictAssert {
    public static void main(String[] args) {
        test(true);
    }

    private static long test(boolean flag) {
        long x = 0;
        for (int i = Integer.MIN_VALUE; i < Integer.MAX_VALUE; ++i) {
            x += i;
            if (flag) {
                break;
            }
        }
        return x;
    }
}
