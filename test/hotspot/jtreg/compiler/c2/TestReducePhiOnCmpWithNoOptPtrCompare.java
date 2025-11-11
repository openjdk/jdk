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
 * @bug 8361140
 * @summary Test ConnectionGraph::reduce_phi_on_cmp when OptimizePtrCompare is disabled
 * @library /test/lib /
 * @run driver compiler.c2.TestReducePhiOnCmpWithNoOptPtrCompare
 */

package compiler.c2;

import java.util.Random;
import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

public class TestReducePhiOnCmpWithNoOptPtrCompare {

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-OptimizePtrCompare","-XX:+VerifyReduceAllocationMerges","-XX:+IgnoreUnrecognizedVMOptions");
    }

    @Run(test = {"testReducePhiOnCmp_C2"})
    public void runner(RunInfo info) {
        Random random = info.getRandom();
        boolean cond = random.nextBoolean();
        int x = random.nextInt();
        int y = random.nextInt();
        Asserts.assertEQ(testReducePhiOnCmp_Interp(cond, x, y),testReducePhiOnCmp_C2(cond, x, y));
    }

    @Test
    int testReducePhiOnCmp_C2(boolean cond, int x, int y) { return testReducePhiOnCmp(cond, x, y); }

    @DontCompile
    int testReducePhiOnCmp_Interp(boolean cond, int x, int y) { return testReducePhiOnCmp(cond, x, y); }

    int testReducePhiOnCmp(boolean cond,int x,int y) {
        Point p = null;

        if (cond) {
            p = new Point(x*x, y*y);
        } else if (x > y) {
            p = new Point(x+y, x*y);
        }

        if (p != null) {
            return p.x * p.y;
        } else {
            return 1984;
        }
    }

    static class Point {
        int x, y;
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof Point)) return false;
            Point p = (Point) o;
            return (p.x == x) && (p.y == y);
        }
    }
}