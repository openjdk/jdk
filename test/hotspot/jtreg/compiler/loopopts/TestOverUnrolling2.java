/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * @bug 8286625
 * @key stress
 * @summary C2 fails with assert(!n->is_Store() && !n->is_LoadStore()) failed: no node with a side effect
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:-BackgroundCompilation -XX:+StressIGVN -XX:StressSeed=4232417824 TestOverUnrolling2
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:-BackgroundCompilation -XX:+StressIGVN TestOverUnrolling2
 */

public class TestOverUnrolling2 {
   public static void main(String[] args) {
        final byte[] large = new byte[1000];
        final byte[] src = new byte[16];
        for (int i = 0; i < 20_000; i++) {
            test_helper(large, large);
            test(src);
        }
   }

    private static void test(byte[] src) {
        byte[] array = new byte[16];
        test_helper(src, array);
    }

    private static void test_helper(byte[] src, byte[] array) {
        for (int i = 0; i < src.length; i++) {
            array[array.length - 1 - i] = src[i];
        }
    }
}
