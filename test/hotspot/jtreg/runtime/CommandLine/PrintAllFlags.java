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

import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PrintAllFlags {
    private static final Pattern optPattern = Pattern.compile("\\s*\\w+\\s\\w+\\s+=");

    public static void main(String args[]) throws Exception {
        var flagsFinal = runAndMakeVMOptionSet("-XX:+PrintFlagsFinal", "-version");
        var flagsFinalUnlocked = runAndMakeVMOptionSet(
                "-XX:+UnlockExperimentalVMOptions", "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintFlagsFinal", "-version");
        if (!flagsFinal.equals(flagsFinalUnlocked)) {
            throw new RuntimeException("+PrintFlagsFinal should produce the same output" +
                    " whether or not UnlockExperimentalVMOptions and UnlockDiagnosticVMOptions are set");
        }
    }

    private static Set<String> runAndMakeVMOptionSet(String... args) throws IOException {
        var output = new OutputAnalyzer(ProcessTools.createLimitedTestJavaProcessBuilder(args).start());
        Set<String> optNameSet = output.asLines().stream()
                .map(optPattern::matcher)
                .filter(Matcher::find)
                .map(Matcher::group)
                .collect(Collectors.toSet());
        if (optNameSet.isEmpty()) {
            throw new RuntimeException("Sanity test failed: no match for option pattern in process output");
        }
        return optNameSet;
    }

}
