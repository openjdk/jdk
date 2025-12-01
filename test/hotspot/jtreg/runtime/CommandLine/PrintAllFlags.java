/*
 * Copyright (c) 2025, IBM Corporation.
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
 * @bug 8372802
 * @summary Test that +PrintFlagsFinal print the same options when +UnlockExperimentalVMOptions and
 *  +UnlockDiagnosticVMOptions are set than when they aren't.
 * @requires vm.flagless
 * @library /test/lib
 * @run driver PrintAllFlags
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class PrintAllFlags {
    public static void main(String args[]) throws Exception {
        Set<String> optNameSet = new HashSet<>();
        var optPattern = Pattern.compile("\\s*\\w+\\s\\w+\\s+=");
        var pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+PrintFlagsFinal", "-version");
        var output = new OutputAnalyzer(pb.start());
        for (var line : output.asLines()) {
            var m = optPattern.matcher(line);
            if (m.find()) {
                optNameSet.add(m.group());
            }
        }
        if (optNameSet.isEmpty()) {
            throw new RuntimeException("Sanity test failed: no match for option pattern in first output");
        }

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-XX:+UnlockExperimentalVMOptions", "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintFlagsFinal", "-version");
        output = new OutputAnalyzer(pb.start());
        for (var line : output.asLines()) {
            var m = optPattern.matcher(line);
            if (m.find()) {
                if (!optNameSet.contains(m.group())) {
                    output.reportDiagnosticSummary();
                    System.err.println("VMOption names from first run:");
                    optNameSet.forEach(System.err::println);
                    throw new RuntimeException("'" + m.group() +
                            "' not expected in PrintFlagsFinal output without vs with unlocked options");
                }
            }
        }

    }
}
