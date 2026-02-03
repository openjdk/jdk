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

/*
 * @test
 * @bug 8350329
 * @summary C2: Div looses dependency on condition that guarantees divisor not zero in counted loop after peeling
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:+IgnoreUnrecognizedVMOptions -XX:-UseLoopPredicate
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:StressSeed=31780379 TestPeeledLoopNoBackedgeFloatingDiv
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:+IgnoreUnrecognizedVMOptions -XX:-UseLoopPredicate
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM TestPeeledLoopNoBackedgeFloatingDiv
 * @run main/othervm TestPeeledLoopNoBackedgeFloatingDiv
 */

public class TestPeeledLoopNoBackedgeFloatingDiv {
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(1000, 0, false, false);
        }
        test1(1, 0, false, false);
    }

    private static int test1(int stop, int res, boolean alwaysTrueInPeeled, boolean alwaysFalse) {
        stop = Integer.max(stop, 1);
        for (int i = stop; i >= 1; i--) {
            res = res / i;
            if (alwaysFalse) {

            }
            if (alwaysTrueInPeeled) {
                break;
            }
            alwaysTrueInPeeled = true;
        }
        return res;
    }

}
