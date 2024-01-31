/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8311023
 * @summary Crash encountered while converting the types of non-escaped object to instance types.
 *
 * @run main/othervm
 *      -XX:-TieredCompilation -Xbatch compiler.escapeAnalysis.TestEAVectorizedHashCode
 */

package compiler.escapeAnalysis;

import java.util.Arrays;

public class TestEAVectorizedHashCode {
    public static int micro() {
        int[] a = { 10, 20, 30, 40, 50, 60};
        return Arrays.hashCode(a);
    }

    public static void main(String [] args) {
        int res = 0;
        for (int i = 0; i < 10000; i++) {
            res += micro();
        }
        System.out.println("PASS:" +  res);
    }
}
