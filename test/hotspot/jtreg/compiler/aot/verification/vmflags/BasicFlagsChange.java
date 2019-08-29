/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

package compiler.aot.verification.vmflags;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Utils;
import compiler.aot.HelloWorldPrinter;
import compiler.aot.AotCompiler;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A class with common launch and check logic for testing vm flags change
 */
public class BasicFlagsChange {
    private static final boolean CAN_LOAD = true;
    /**
     * A main method which parse arguments, expecting vm option name to
     *     be present, launch java process with combinations of provided flag
     *     enabled/disable in aot library and vm flag expecting different flag
     *     values in library and vm to be negative cases
     * @param args should have true/false treated as "loadAlways" for
     *     tracked/non-tracked options and vm option name
     */
    public static void main(String args[]) {
        if (args.length != 2) {
            throw new Error("TESTBUG: Unexpected number of arguments: "
                    + args.length);
        }
        if (!"false".equals(args[0]) && !"true".equals(args[0])) {
            throw new Error("TESTBUG: unexpected value of 1st parameter: "
                    + args[0]);
        }
        boolean loadAlways = Boolean.parseBoolean(args[0]);
        String optName = args[1];
        String optEnabled = "-XX:+" + optName;
        String optDisabled = "-XX:-" + optName;
        String enabledLibName = "libEnabled.so";
        String disabledLibName = "libDisabled.so";
        // compile libraries
        compileLibrary(optEnabled, enabledLibName);
        compileLibrary(optDisabled, disabledLibName);
        // run 4 combinations
        runAndCheck(optEnabled, enabledLibName, CAN_LOAD || loadAlways);
        runAndCheck(optDisabled, enabledLibName, !CAN_LOAD || loadAlways);
        runAndCheck(optEnabled, disabledLibName, !CAN_LOAD || loadAlways);
        runAndCheck(optDisabled, disabledLibName, CAN_LOAD || loadAlways);
    }

    private static void compileLibrary(String option, String libName) {
        String className = BasicFlagsChange.class.getName();
        List<String> extraOpts = new ArrayList<>();
        extraOpts.add(option);
        extraOpts.add("-classpath");
        extraOpts.add(Utils.TEST_CLASS_PATH + File.pathSeparator + Utils.TEST_SRC);
        AotCompiler.launchCompiler(libName, className, extraOpts, null);
    }

    private static void runAndCheck(String option, String libName,
            boolean positiveCase) {
        ProcessBuilder pb;
        try {
            /* using +PrintAOT to check if library has been loaded or skipped,
               so, a message like "skipped $pathTolibrary aot library" or
               "loaded    $pathToLibrary  aot library" is present for cases of
               incompatible or compatible flags respectively */
            pb = ProcessTools.createJavaProcessBuilder(true, "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseAOT", "-XX:+PrintAOT", "-XX:AOTLibrary=./" + libName, option,
                    HelloWorldPrinter.class.getName());
        } catch (Exception ex) {
            throw new Error("Problems creating ProcessBuilder using " + option
                    + " Caused by: " + ex, ex);
        }
        OutputAnalyzer oa;
        try {
            oa = ProcessTools.executeProcess(pb);
        } catch (Exception ex) {
            throw new Error("Problems execution child process using case "
                    + option + " Caused by: " + ex, ex);
        }
        oa.shouldHaveExitValue(0);
        oa.shouldContain(HelloWorldPrinter.MESSAGE);
        if (positiveCase) {
            oa.shouldContain("loaded    ./" + libName + "  aot library");
        } else {
            oa.shouldContain("skipped ./" + libName + "  aot library");
        }
    }
}
