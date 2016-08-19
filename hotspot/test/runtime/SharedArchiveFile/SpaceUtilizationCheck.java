/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test SpaceUtilizationCheck
 * @summary Check if the space utilization for shared spaces is adequate
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main SpaceUtilizationCheck
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.lang.Integer;

public class SpaceUtilizationCheck {
    // Minimum allowed utilization value (percent)
    // The goal is to have this number to be 50% for RO and RW regions
    // Once that feature is implemented, increase the MIN_UTILIZATION to 50
    private static final int MIN_UTILIZATION = 30;

    // Only RO and RW regions are considered for this check, since they
    // currently account for the bulk of the shared space
    private static final int NUMBER_OF_CHECKED_SHARED_REGIONS = 2;

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
           "-XX:+UnlockDiagnosticVMOptions",
           "-XX:SharedArchiveFile=./SpaceUtilizationCheck.jsa",
           "-Xshare:dump");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        String stdout = output.getStdout();
        ArrayList<String> utilization = findUtilization(stdout);

        if (utilization.size() != NUMBER_OF_CHECKED_SHARED_REGIONS )
            throw new RuntimeException("The output format of sharing summary has changed");

        for(String str : utilization) {
            int value = Integer.parseInt(str);
            if (value < MIN_UTILIZATION) {
                System.out.println(stdout);
                throw new RuntimeException("Utilization for one of the regions" +
                    "is below a threshold of " + MIN_UTILIZATION + "%");
            }
        }
    }

    public static ArrayList<String> findUtilization(String input) {
        ArrayList<String> regions = filterRegionsOfInterest(input.split("\n"));
        return filterByPattern(filterByPattern(regions, "bytes \\[.*% used\\]"), "\\d+");
    }

    private static ArrayList<String> filterByPattern(Iterable<String> input, String pattern) {
        ArrayList<String> result = new ArrayList<String>();
        for (String str : input) {
            Matcher matcher = Pattern.compile(pattern).matcher(str);
            if (matcher.find()) {
                result.add(matcher.group());
            }
        }
        return result;
    }

    private static ArrayList<String> filterRegionsOfInterest(String[] inputLines) {
        ArrayList<String> result = new ArrayList<String>();
        for (String str : inputLines) {
            if (str.contains("ro space:") || str.contains("rw space:")) {
                result.add(str);
            }
        }
        return result;
    }
}
