/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8214761
 * @run testng NegativeCompensation
 * @summary When combining two DoubleSummaryStatistics, the compensation
 *          has to be subtracted.
 */

import java.util.DoubleSummaryStatistics;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class NegativeCompensation {
    static final double VAL = 1.000000001;
    static final int LOG_ITER = 21;

    @Test
    public static void testErrorComparision() {
        DoubleSummaryStatistics stat0 = new DoubleSummaryStatistics();
        DoubleSummaryStatistics stat1 = new DoubleSummaryStatistics();
        DoubleSummaryStatistics stat2 = new DoubleSummaryStatistics();

        stat1.accept(VAL);
        stat1.accept(VAL);
        stat2.accept(VAL);
        stat2.accept(VAL);
        stat2.accept(VAL);

        for (int i = 0; i < LOG_ITER; ++i) {
            stat1.combine(stat2);
            stat2.combine(stat1);
        }

        for (long i = 0, iend = stat2.getCount(); i < iend; ++i) {
            stat0.accept(VAL);
        }

        double res = 0;
        for(long i = 0, iend = stat2.getCount(); i < iend; ++i) {
            res += VAL;
        }

        double absErrN = Math.abs(res - stat2.getSum());
        double absErr = Math.abs(stat0.getSum() - stat2.getSum());
        assertTrue(absErrN >= absErr,
                "Naive sum error is not greater than or equal to Summary sum");
        assertEquals(absErr, 0.0, "Absolute error is not zero");
    }
}