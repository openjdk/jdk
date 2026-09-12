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
 * @key stress
 * @bug 8389578
 * @summary Test an implicit null check whose result is used by scalarized
 *          debug info on the null path.
 * @enablePreview
 * @run main ${test.main.class}
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:StressSeed=371
 *                   -XX:CompileCommand=compileonly,${test.main.class}::testImplicitNullCheck
 *                   ${test.main.class}
 */

public class TestImplicitNullCheckWithScalarizedDebugInfo {
    static char c1 = 'A';
    static double d;
    static Character c2 = '(';
    static short s = -2;
    static int i4;

    static int testImplicitNullCheck() {
        if (--s != 0) {
            c2.charValue();
        } else {
            Byte.valueOf((byte) 1).shortValue();
            d++;
        }

        char c3 = c1;
        int res = 0;
        int i1 = i4;
        int i2 = i1 + 1;
        int i3 = i1 + i2;
        i4 = i3;

        char debugInfoPayload = c2;
        boolean cond = ++c3 <= 1;
        char c4 = cond ? Character.valueOf('r') : c2;
        --s;

        res -= c1 > 1 ? 2 : 1;
        for (byte b = 9; b >= 0; b--) {
            long l = 2L << c3;
            res |= l;
        }
        return res + i1 + i2 + i3;
    }

    public static void main(String[] args) {
        testImplicitNullCheck();
    }
}
