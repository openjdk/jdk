/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * Results of running the JstatGcTool ("jstat -gccause <pid>")
 *
 * Output example:
 * S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     GCT    LGCC                 GCC
 * 0.00   6.25  46.19   0.34  57.98  54.63  15305 1270.551     0    0.000 1270.551 Allocation Failure   No GC

 * Output description:
 * S0         Survivor space 0 utilization as a percentage of the space's current capacity.
 * S1         Survivor space 1 utilization as a percentage of the space's current capacity.
 * E           Eden space utilization as a percentage of the space's current capacity.
 * O           Old space utilization as a percentage of the space's current capacity.
 * M          Metaspace utilization as a percentage of the space's current capacity.
 * CCS     Compressed Class Space
 * YGC     Number of young generation GC events.
 * YGCT    Young generation garbage collection time.
 * FGC      Number of full GC events.
 * FGCT    Full garbage collection time.
 * GCT      Total garbage collection time.
 * LGCC    Cause of last Garbage Collection.
 * GCC      Cause of current Garbage Collection.
 */
package utils;

import common.ToolResults;

public class JstatGcCauseResults extends JstatResults {

    public JstatGcCauseResults(ToolResults rawResults) {
        super(rawResults);
    }

    /**
     * Checks the overall consistency of the results reported by the tool
     */
    @Override
    public void assertConsistency() {

        assertThat(getExitCode() == 0, "Unexpected exit code: " + getExitCode());

        int YGC = getIntValue("YGC");
        float YGCT = getFloatValue("YGCT");
        assertThat(YGCT >= 0, "Incorrect time value for YGCT");
        if (YGC > 0) {
            assertThat(YGCT > 0, "Number of young generation GC Events is " + YGC + ", but YGCT is 0");
        }

        float GCT = getFloatValue("GCT");
        assertThat(GCT >= 0, "Incorrect time value for GCT");
        assertThat(GCT >= YGCT, "GCT < YGCT (total garbage collection time < young generation garbage collection time)");

        int FGC = getIntValue("FGC");
        float FGCT = getFloatValue("FGCT");
        assertThat(FGCT >= 0, "Incorrect time value for FGCT");
        if (FGC > 0) {
            assertThat(FGCT > 0, "Number of full GC events is " + FGC + ", but FGCT is 0");
        }

        assertThat(GCT >= FGCT, "GCT < YGCT (total garbage collection time < full generation garbage collection time)");

        assertThat(checkFloatIsSum(GCT, YGCT, FGCT), "GCT != (YGCT + FGCT) " + "(GCT = " + GCT + ", YGCT = " + YGCT
                + ", FGCT = " + FGCT + ", (YCGT + FGCT) = " + (YGCT + FGCT) + ")");
    }
}
