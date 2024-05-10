/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test ClearArray.java
 * @bug 8284883
 * @compile ClearArray.java
 * @summary ClearArray instruction overflows scratch buffer
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:-TieredCompilation -Xbatch
 *   -XX:InitArrayShortSize=32768 -XX:-IdealizeClearArrayNode compiler.c2.ClearArray
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation -Xbatch
 *   -XX:InitArrayShortSize=32768 -XX:-IdealizeClearArrayNode -XX:UseAVX=3 compiler.c2.ClearArray
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation -Xbatch
 *   -XX:InitArrayShortSize=32768 -XX:MaxVectorSize=8 -XX:-IdealizeClearArrayNode -XX:UseAVX=3 compiler.c2.ClearArray
 */

package compiler.c2;

public class ClearArray {

    static long[] STATIC;

    static void foo() {
        STATIC = new long[2048 - 1];
    }

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; ++i) {
            foo();
        }
    }
}
