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

/*
 * @test
 * @bug 8396445
 * @summary Test that the two-hop notification for the ReverseBytes(U)S involution happens.
 * @library /test/lib
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:+IgnoreUnrecognizedVMOptions -XX:VerifyIterativeGVN=1110
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.igvn;

import jdk.test.lib.Asserts;

public class TestReverseBytesInvolutionNotification {
    public static void main(String[] args) {
        int resultSInt = testS(42);
        int resultUSInt = testUS(42);
        for (int i = 0; i < 10_000; i++) {
            testS(i);
            testUS(i);
        }
        int resultSComp = testS(42);
        int resultUSComp = testUS(42);
        Asserts.assertEQ(resultSInt, resultSComp, "wrong result for ReversBytesS");
        Asserts.assertEQ(resultUSInt, resultUSComp, "wrong result for ReversBytesUS");
    }

    static int testS(long l) {
        int x = 8;
        short s = 2;
        for (int i = 0; i < 10_000; i++) {
            x *= Short.reverseBytes(Short.reverseBytes(s));
            s = (short)l;
        }
        return x;
    }

    static int testUS(long l) {
        int x = 8;
        char c = 2;
        for (int i = 0; i < 10_000; i++) {
            x *= Character.reverseBytes(Character.reverseBytes(c));
            c = (char)l;
        }
        return x;
    }
}
