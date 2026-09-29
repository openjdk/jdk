/*
 * Copyright (c) 2025, Google LLC. All rights reserved.
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
 * @bug 8350563
 * @summary Test that And nodes are monotonic and added to the CCP worklist if they have a constant as input.
 * @run main/othervm -Xbatch -XX:-TieredCompilation compiler.ccp.TestAndConZeroCCP
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:RepeatCompilation=100 -XX:+StressIGVN -XX:+StressCCP -Xcomp
 *                   -XX:CompileOnly=java.lang.Integer::parseInt compiler.ccp.TestAndConZeroCCP compileonly
 * @run main compiler.ccp.TestAndConZeroCCP
 */
package compiler.ccp;

import java.util.Arrays;

public class TestAndConZeroCCP {

    public static void main(String[] args) {
        Integer.parseInt("1");
        if (args.length != 0) {
            return;
        }

        for (int i = 0; i < 100; ++i) {
            run();
        }
    }

    private static void run() {
        for (int cp = 0; cp <= 1 << 16; cp++) {
            Arrays.binarySearch(array, cp);
            Character.getType(cp);
            Character.isAlphabetic(cp);
        }
    }

    private static final int[] array = new int[3];
}
