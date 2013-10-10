/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestSummarizeRSetStatsPerRegion.java
 * @bug 8014078
 * @library /testlibrary
 * @build TestSummarizeRSetStatsTools TestSummarizeRSetStatsPerRegion
 * @summary Verify output of -XX:+G1SummarizeRSetStats in regards to per-region type output
 * @run main TestSummarizeRSetStatsPerRegion
 */

import com.oracle.java.testlibrary.*;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.Arrays;

public class TestSummarizeRSetStatsPerRegion {

    public static void main(String[] args) throws Exception {
        String result;

        if (!TestSummarizeRSetStatsTools.testingG1GC()) {
            return;
        }

        // single remembered set summary output at the end
        result = TestSummarizeRSetStatsTools.runTest(new String[] { "-XX:+G1SummarizeRSetStats" }, 0);
        TestSummarizeRSetStatsTools.expectPerRegionRSetSummaries(result, 1, 0);

        // two times remembered set summary output
        result = TestSummarizeRSetStatsTools.runTest(new String[] { "-XX:+G1SummarizeRSetStats", "-XX:G1SummarizeRSetStatsPeriod=1" }, 1);
        TestSummarizeRSetStatsTools.expectPerRegionRSetSummaries(result, 1, 2);
    }
}
