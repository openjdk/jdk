/*
 * Copyright Amazon.com Inc. or its affiliates. All rights reserved.
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
 * @bug 8375442
 * @summary fold_compares_helper must clean up speculative lo node when bailing out with deep revisit
 * @library /test/lib /
 * @run main/othervm -XX:-TieredCompilation -Xbatch -XX:+IgnoreUnrecognizedVMOptions -XX:VerifyIterativeGVN=1110
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 *
 * @run main ${test.main.class}
 */
package compiler.igvn;

import jdk.test.lib.Asserts;

public class TestFoldComparesCleanup {
    // Constants chosen so that fold_compares_helper computes adjusted_lim which overflows negative.
    static final int A = -2_000_000_000;
    static final int B =  2_000_000_000;

    static int test(int z) {
        int sum = 0;
        if (z > A) sum += 1;
        if (z < B) sum += 2;
        return sum;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 50_000; i++) {
            Asserts.assertEquals(3, test(i));
        }
    }
}
