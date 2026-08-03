/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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
 * @bug 8361702
 * @summary C2: assert(is_dominator(compute_early_ctrl(limit, limit_ctrl), pre_end)) failed: node pinned on loop exit test?
 * @requires vm.flavor == "server"
 * @run main/othervm -XX:-BackgroundCompilation -XX:LoopUnrollLimit=100 -XX:-UseLoopPredicate -XX:-UseProfiledLoopPredicate TestSunkRangeFromPreLoopRCE3
 */

/**
 * @test
 * @bug 8361702
 * @summary C2: assert(is_dominator(compute_early_ctrl(limit, limit_ctrl), pre_end)) failed: node pinned on loop exit test?
 * @run main TestSunkRangeFromPreLoopRCE3
 */

import java.util.Arrays;

public class TestSunkRangeFromPreLoopRCE3 {

    static final int nbIterations = 100;

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(0);
            test1(0);
        }
    }

    private static float test1(int k) {
        float v = 0;
        int j = 0;
        int[] lengths = new int[2];
        test1Helper(lengths);
        int constantFoldedTo4AfterCCP = 2;
        for (; constantFoldedTo4AfterCCP < 4; constantFoldedTo4AfterCCP *= 2);

        int constantFoldedTo0AfterCCPAnd1RoundLoopOpts;
        for (constantFoldedTo0AfterCCPAnd1RoundLoopOpts = 0; constantFoldedTo0AfterCCPAnd1RoundLoopOpts < 40; constantFoldedTo0AfterCCPAnd1RoundLoopOpts += constantFoldedTo4AfterCCP) {
        }
        constantFoldedTo0AfterCCPAnd1RoundLoopOpts -= 40;
        for (int i = 0; i < nbIterations; i++) {
            int arrayLength2 = Integer.max(Integer.min(lengths[j * k], 1000), 0);
            float[] array = new float[arrayLength2];
            v += array[(constantFoldedTo0AfterCCPAnd1RoundLoopOpts + 1) * i];

            int arrayLength = Integer.max(Integer.min(lengths[k], 1000), 0);

            v += arrayLength & constantFoldedTo0AfterCCPAnd1RoundLoopOpts;

            j = 1;
        }
        return v;
    }

    private static void test1Helper(int[] lengths) {
        lengths[0] = nbIterations+1;
        lengths[1] = nbIterations+1;
    }
}
