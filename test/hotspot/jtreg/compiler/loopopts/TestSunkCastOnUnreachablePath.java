/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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
 * @bug 8272562
 * @summary C2: assert(false) failed: Bad graph detected in build_loop_late
 *
 * @run main/othervm -XX:CompileOnly=TestSunkCastOnUnreachablePath -XX:-TieredCompilation -Xbatch TestSunkCastOnUnreachablePath
 *
 */

public class TestSunkCastOnUnreachablePath {

    public static void main(String[] strArr) {
        for (int i = 0; i < 1000; i++) {
            vMeth();
        }
    }

    static int vMeth() {
        int i2 = 3, iArr1[] = new int[200];

        for (int i9 = 3; i9 < 100; i9++) {
            try {
                int i10 = (iArr1[i9 - 1]);
                i2 = (i10 / i9);
            } catch (ArithmeticException a_e) {
            }
        }
        return i2;
    }
}
