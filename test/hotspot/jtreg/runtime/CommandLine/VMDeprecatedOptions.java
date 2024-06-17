/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.ArrayList;

import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.cli.*;
import jdk.test.whitebox.WhiteBox;

/*
 * @test
 * @bug 8066821
 * @summary Test that various options are deprecated. See deprecated_jvm_flags in arguments.cpp.
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI VMDeprecatedOptions

 */
public class VMDeprecatedOptions {

    private final static WhiteBox wb = WhiteBox.getWhiteBox();
    /**
     * each entry is {[0]: option name, [1]: value to set
     * (true/false/n/string)}.
     */
    public static final String[][] DEPRECATED_OPTIONS;
    static {
        // Use an ArrayList so platform-specific flags can be
        // optionally added.
        ArrayList<String[]> deprecated = new ArrayList(
          Arrays.asList(new String[][] {
            // deprecated non-alias flags:
            {"AllowRedefinitionToAddDeleteMethods", "true"},
            {"ZGenerational", "false"},

            // deprecated alias flags (see also aliased_jvm_flags):
            {"CreateMinidumpOnCrash", "false"}
          }
        ));
        if (Platform.isX86() || Platform.isX64()) {
          deprecated.addAll(
            Arrays.asList(new String[][] {
            })
          );
        }
        if (wb.isJFRIncluded()) {
            deprecated.add(new String[] {"FlightRecorder", "false"});
        }
        DEPRECATED_OPTIONS = deprecated.toArray(new String[][]{});
    };

    static String getDeprecationString(String optionName) {
        return "Option " + optionName
            + " was deprecated in version [\\S]+ and will likely be removed in a future release";
    }

    static void testDeprecated(String[][] optionInfo) throws Throwable {
        String optionNames[] = new String[optionInfo.length];
        String expectedValues[] = new String[optionInfo.length];
        for (int i = 0; i < optionInfo.length; i++) {
            optionNames[i] = optionInfo[i][0];
            expectedValues[i] = optionInfo[i][1];
        }

        OutputAnalyzer output = CommandLineOptionTest.startVMWithOptions(optionNames, expectedValues);

        // check for option deprecation messages:
        output.shouldHaveExitValue(0);
        for (String[] deprecated : optionInfo) {
            String match = getDeprecationString(deprecated[0]);
            output.shouldMatch(match);
        }
    }

    // Deprecated diagnostic command line options need to be preceded on the
    // command line by -XX:+UnlockDiagnosticVMOptions.
    static void testDeprecatedDiagnostic(String option, String value)  throws Throwable {
        String XXoption = CommandLineOptionTest.prepareFlag(option, value);
        ProcessBuilder processBuilder = ProcessTools.createLimitedTestJavaProcessBuilder(
            CommandLineOptionTest.UNLOCK_DIAGNOSTIC_VM_OPTIONS, XXoption, "-version");
        OutputAnalyzer output = new OutputAnalyzer(processBuilder.start());
        // check for option deprecation message:
        output.shouldHaveExitValue(0);
        String match = getDeprecationString(option);
        output.shouldMatch(match);
    }

    // Deprecated experimental command line options need to be preceded on the
    // command line by -XX:+UnlockExperimentalVMOption.
    static void testDeprecatedExperimental(String option, String value)  throws Throwable {
        String XXoption = CommandLineOptionTest.prepareFlag(option, value);
        ProcessBuilder processBuilder = ProcessTools.createLimitedTestJavaProcessBuilder(
            CommandLineOptionTest.UNLOCK_EXPERIMENTAL_VM_OPTIONS, XXoption, "-version");
        OutputAnalyzer output = new OutputAnalyzer(processBuilder.start());
        // check for option deprecation message:
        output.shouldHaveExitValue(0);
        String match = getDeprecationString(option);
        output.shouldMatch(match);
    }

    public static void main(String[] args) throws Throwable {
        testDeprecated(DEPRECATED_OPTIONS);  // Make sure that each deprecated option is mentioned in the output.
    }
}
