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
 * @summary When combining two DoubleSummaryStatistics, the compensation
 *          has to be subtracted.
 */

import java.util.DoubleSummaryStatistics;

public class NegativeCompensation {
    static final double VAL = 1.000000001;
    static final int LOG_ITER = 21;

    public static void main(String[] args) {
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

        System.out.println("count: " + stat2.getCount());
        for (long i = 0, iend = stat2.getCount(); i < iend; ++i) {
            stat0.accept(VAL);
        }

        double absErr = Math.abs(stat0.getSum() - stat2.getSum());
        System.out.println("serial sum: " + stat0.getSum());
        System.out.println("combined sum: " + stat2.getSum());
        System.out.println("abs error: " + absErr);
        if (absErr > 0.00000001) {
            throw new RuntimeException("Absolute error is too big: " + absErr);
        }
    }
}
