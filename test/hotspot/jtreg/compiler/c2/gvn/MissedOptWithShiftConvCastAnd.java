/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8320909
 * @summary Similar to MissedOptWithShiftConvAnd, but with CastII on the way.
 * @library /test/lib
 *
 * @run main/othervm
 *          -XX:CompileOnly=MissedOptWithShiftConvCastAnd::test
 *          -XX:-TieredCompilation -Xbatch
 *          -XX:+IgnoreUnrecognizedVMOptions -XX:VerifyIterativeGVN=10
 *          MissedOptWithShiftConvCastAnd
 */

/*
 * @test
 * @bug 8320909
 * @library /test/lib
 *
 * @run main/othervm MissedOptWithShiftConvCastAnd
 */

import jdk.test.lib.Utils;

public class MissedOptWithShiftConvCastAnd {
    static long instanceCount;

    public static void main(String[] args) throws Exception {
        Thread thread = new Thread() {
            public void run() {
                test(0);
            }
        };
        // Give thread some time to trigger compilation
        thread.setDaemon(true);
        thread.start();
        Thread.sleep(Utils.adjustTimeout(500));
    }

    static void test(int x) {
        for (int i = 3; ; ++i) {
            for (int j = 5; j > 1; --j) {
                instanceCount >>= x <<= 16;
            }
            x >>>= 16;
            for (int j = 1; j < 5; j++) {
                try {
                    x = 1;
                } catch (ArithmeticException a_e) {
                }
            }
        }
    }
}
