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
 * @bug 8271340
 * @summary Crash PhaseIdealLoop::clone_outer_loop
 * @library /test/lib
 *
 * @run main/othervm TestInfiniteLoopCCP
 *
 */

import jdk.test.lib.Utils;

public class TestInfiniteLoopCCP {

    public boolean m3 (long a_lo0, short a_sh1){
        long loa6[][] = new long[560][1792];

        loa6[-128][0] = 1345348328L;

        return false;
    }

    public void mainTest (String[] args){
        short sh16 = (short)-32;
        for (int i17 = 0; i17 < 29; i17++) {
            if (i17 == 8) {
                i17 = 129;
                for (int i18 = 0; i18 < 27; i18++) {
                    i18 = 16;
                }
            }
        }

        try {
            m3(4130511410L, (short)(sh16 % sh16));
        } catch (ArithmeticException a_e) {}
    }


    public static void main(String[] args) throws Exception{
        Thread thread = new Thread() {
                public void run() {
                    TestInfiniteLoopCCP instance = new TestInfiniteLoopCCP();
                    for (int i = 0; i < 100; ++i) {
                        instance.mainTest(args);
                    }
                }
            };
        // Give thread some time to trigger compilation
        thread.setDaemon(true);
        thread.start();
        Thread.sleep(Utils.adjustTimeout(4000));
    }
}
