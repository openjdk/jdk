/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
// Checkstyle: stop



package org.graalvm.compiler.jtt.hotpath;

import org.graalvm.compiler.jtt.JTTTest;
import org.junit.Test;

/*
 */
public class HP_series extends JTTTest {

    public static double test(int count) {
        final int arrayRows = count;
        final double[][] testArray = new double[2][arrayRows];
        double omega; // Fundamental frequency.
        testArray[0][0] = TrapezoidIntegrate(0.0, // Lower bound.
                        2.0, // Upper bound.
                        1000, // # of steps.
                        0.0, // No omega*n needed.
                        0) / 2.0; // 0 = term A[0].
        omega = 3.1415926535897932;
        for (int i = 1; i < arrayRows; i++) {
            testArray[0][i] = TrapezoidIntegrate(0.0, 2.0, 1000, omega * i, 1); // 1 = cosine
            // term.
            testArray[1][i] = TrapezoidIntegrate(0.0, 2.0, 1000, omega * i, 2); // 2 = sine
            // term.
        }
        final double ref[][] = {{2.8729524964837996, 0.0}, {1.1161046676147888, -1.8819691893398025}, {0.34429060398168704, -1.1645642623320958}, {0.15238898702519288, -0.8143461113044298}};
        double error = 0.0;
        double sum = 0.0;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 2; j++) {
                error += Math.abs(testArray[j][i] - ref[i][j]);
                sum += testArray[j][i];
            }
        }
        return sum + error;
    }

    private static double TrapezoidIntegrate(double x0, // Lower bound.
                    double x1, // Upper bound.
                    int ns, // # of steps.
                    double omegan, // omega * n.
                    int select) // Term type.
    {
        int nsteps = ns;
        double x; // Independent variable.
        double dx; // Step size.
        double rvalue; // Return value.

        x = x0;
        dx = (x1 - x0) / nsteps;
        rvalue = thefunction(x0, omegan, select) / 2.0;
        if (nsteps != 1) {
            --nsteps; // Already done 1 step.
            while (--nsteps > 0) {
                x += dx;
                rvalue += thefunction(x, omegan, select);
            }
        }
        rvalue = (rvalue + thefunction(x1, omegan, select) / 2.0) * dx;
        return (rvalue);
    }

    private static double thefunction(double x, // Independent variable.
                    double omegan, // Omega * term.
                    int select) // Choose type.
    {
        switch (select) {
            case 0:
                return (Math.pow(x + 1.0, x));
            case 1:
                return (Math.pow(x + 1.0, x) * Math.cos(omegan * x));
            case 2:
                return (Math.pow(x + 1.0, x) * Math.sin(omegan * x));
        }
        return (0.0);
    }

    /**
     * This test is sensitive to the implementation of {@link Math#pow}, {@link Math#cos} and
     * {@link Math#sin(double)}. Since for these functions, the specs says "The computed result must
     * be within 1 ulp of the exact result", different implementation may return different results.
     * The 11 ulp delta allowed for test(100) tries to account for that but is not guaranteed to
     * work forever.
     */
    @Test
    public void run0() throws Throwable {
        ulpDelta = 11.0D;
        runTest("test", 100);
    }
}
