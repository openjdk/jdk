/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.loop;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

public class LoopSpilling extends JTTTest {

    private static final int ITERATION = 64;

    /**
     * Modification of sun.security.provider.SHA2.implCompress().
     */
    void test(int[] state) {

        int a1 = state[0];
        int b1 = state[1];
        int c1 = state[2];
        int d1 = state[3];
        int e1 = state[4];
        int f1 = state[5];
        int g1 = state[6];
        int h1 = state[7];

        // 2nd
        int a2 = state[8];
        int b2 = state[9];
        int c2 = state[10];
        int d2 = state[11];
        int e2 = state[12];
        int f2 = state[13];
        int g2 = state[14];
        int h2 = state[15];

        for (int i = 0; i < ITERATION; i++) {
            h1 = g1;
            g1 = f1;
            f1 = e1;
            e1 = d1;
            d1 = c1;
            c1 = b1;
            b1 = a1;
            a1 = h1;
            // 2nd
            h2 = g2;
            g2 = f2;
            f2 = e2;
            e2 = d2;
            d2 = c2;
            c2 = b2;
            b2 = a2;
            a2 = h2;
        }
        state[0] += a1;
        state[1] += b1;
        state[2] += c1;
        state[3] += d1;
        state[4] += e1;
        state[5] += f1;
        state[6] += g1;
        state[7] += h1;
        // 2nd
        state[8] += a2;
        state[9] += b2;
        state[10] += c2;
        state[11] += d2;
        state[12] += e2;
        state[13] += f2;
        state[14] += g2;
        state[15] += h2;
    }

    private static final int[] INITIAL_HASHES = {0xc1059ed8, 0x367cd507, 0x3070dd17, 0xf70e5939, 0xffc00b31, 0x68581511, 0x64f98fa7, 0xbefa4fa4, 0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
                    0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};

    @Test
    public void run0() throws Throwable {
        runTest("test", supply(() -> INITIAL_HASHES.clone()));
    }

}
