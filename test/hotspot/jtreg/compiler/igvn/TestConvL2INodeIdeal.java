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

/*
 * @test
 * @bug 8392942
 * @summary Test that IGVN revisits ConvL2I when the mask input of an AndL becomes
 *          0xFFFFFFFF in the ConvL2I(AndL(x, 0xFFFFFFFF)) pattern.
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                   -Xbatch
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   -XX:VerifyIterativeGVN=1110
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */
public class TestConvL2INodeIdeal {
    static int iFld;

    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            test();
        }
    }

    static void test() {
        for (int i = 0; i < 2; i++) {
            iFld = (int) (iFld & ((i - 5L) >>> 32));
        }
    }
}
