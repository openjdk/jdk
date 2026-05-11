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

/**
 * @test
 * @bug 8357381
 * @summary C2 compilation fails with C2: assert(false) failed: should not be here
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:-TieredCompilation -Xbatch -XX:CompileCommand=compileonly,compiler.escapeAnalysis.TestReadOnlyStringIntrinsicDuringEA::main
 *                   compiler.escapeAnalysis.TestReadOnlyStringIntrinsicDuringEA
 */

package compiler.escapeAnalysis;

public class TestReadOnlyStringIntrinsicDuringEA extends c159.HelperBase {
    static int test1(String id, String nameKey) {
        try {
            java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
            java.math.BigInteger num = java.math.BigInteger.valueOf(123);
            int length = num.toByteArray().length;
            stream.write(num.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if ("UTC".equals(id) && id.equals(nameKey))
            ;
        return 0;
    }

    static boolean test2(String b) {
        String t1 = "";
        var s1 = new StringBuffer();
        var s2 = s1.append(String.valueOf(t1));
        var s3 = s2.append(7);
        var s4 = String.valueOf("AB");
        var s5 = s4.equals(String.valueOf(b));
        return s5;
    }

    public static void main(String[] strArr) {
        for (int t = 0; t < 10_000; t++) {
            test1("123456abc", "123456abc");
            test2("X");
        }
    }
}

