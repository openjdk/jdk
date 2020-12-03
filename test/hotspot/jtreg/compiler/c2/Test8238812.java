/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8238812
 * @summary Fix c2 assert(false) failed: bad AD file
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.c2.Test8238812::test
 *                   -XX:CompileCommand=dontinline,compiler.c2.Test8238812::test
 *                   -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:-TieredCompilation -XX:+UseSwitchProfiling
 *                   compiler.c2.Test8238812
 */

package compiler.c2;

public class Test8238812 {

    public static void test() {
        int i4, i5=99, i6, i9=89;
        for (i4 = 12; i4 < 365; i4++) {
            for (i6 = 5; i6 > 1; i6--) {
                switch ((i6 * 5) + 11) {
                case 13:
                case 19:
                case 26:
                case 31:
                case 35:
                case 41:
                case 43:
                case 61:
                case 71:
                case 83:
                case 314:
                    i9 = i5;
                    break;
                }
            }
        }
    }

    public static void main(String[] strArr) {
        for (int i = 0; i < 10; i++) {
            test();
        }
    }

}
