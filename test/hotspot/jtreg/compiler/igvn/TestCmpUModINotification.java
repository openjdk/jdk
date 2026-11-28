/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @bug 8395943
 * @summary Test that CmpU is notified when the inputs of its ModI input change.
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:+IgnoreUnrecognizedVMOptions -XX:VerifyIterativeGVN=1110
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.igvn;

public class TestCmpUModINotification {
    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            test(i, i + 1);
        }
    }

    static int test(int x, int y) {
        x = Math.min(Math.max(x, 1), 100);
        y = Math.min(Math.max(y, 1), 100);

        // c is only known to be 0 after the loop is optimized away,
        // so z and y become the same node late in IGVN.
        int c = 200;
        for (int i = 0; i < 200; i++) {
            c--;
        }
        int z = Math.min(Math.max(y + c, 1), 100);

        return Integer.compareUnsigned(x % z, y) < 0 ? 0 : 1;
    }
}
