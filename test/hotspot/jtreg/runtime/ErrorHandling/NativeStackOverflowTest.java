/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=default
 * @requires os.family != "windows" & os.family != "aix"
 * @requires vm.flagless
 * @library /test/lib
 * @run driver NativeStackOverflowTest dflt
 */

/*
 * @test id=explicit
 * @requires os.family != "windows" & os.family != "aix"
 * @requires vm.flagless
 * @library /test/lib
 * @run driver NativeStackOverflowTest explicit
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

public class NativeStackOverflowTest {

    private static class Crasher {
        public static void main(String[] args) throws Throwable {
            System.loadLibrary("NativeStackOverflow");
            crash();
        }
        public static native void crash();
    }

    public static void test(boolean useExplicitStackSize) throws Exception {

        final int explicitStackSize = 1024;
        final int defaultStackSize = 128; // keep in sync with -XX:AltSigStackSize
        ArrayList<String> args =
                new ArrayList<>(Arrays.asList("-XX:+UnlockDiagnosticVMOptions", "-XX:+UseAltSigStacks",
                                              "-Xlog:os+thread=debug",
                                              "-Djava.library.path=" + System.getProperty("java.library.path")));
        if (useExplicitStackSize) {
            args.add("-XX:AltSigStackSize=" + explicitStackSize);
        }
        args.add(Crasher.class.getName());

        OutputAnalyzer output = ProcessTools.executeTestJava(args);

        final int expectedStackSize = useExplicitStackSize ? explicitStackSize : defaultStackSize;
        final int acceptableFudge = 128;

output.reportDiagnosticSummary();
        output.shouldNotHaveExitValue(0);

        output.shouldContain("Alternative signal stack size");
        final int realStackSize =
            Integer.parseInt(output.firstMatch(".*Alternative signal stack size (\\d+).*", 1)) / 1024;

        if (realStackSize < expectedStackSize || realStackSize > (expectedStackSize + acceptableFudge)) {
            throw new RuntimeException(String.format("Unexpected stack size (%d vs %d)", expectedStackSize, realStackSize));
        }

        output.shouldMatch(".*Thread \\d+ alternate signal stack: enabled.*");
        output.shouldContain("An irrecoverable stack overflow has occurred");

        File hsErrFile = HsErrFileUtils.openHsErrFileFromOutput(output);

        Pattern[] positivePatterns = {
                Pattern.compile(".*(SIGSEGV|SIGBUS).*"),
                Pattern.compile(".*irrecoverable stack overflow.*"),
                Pattern.compile(".*Java_NativeStackOverflowTest_00024Crasher_crash.*"),
                Pattern.compile(".*Java_NativeStackOverflowTest_00024Crasher_crash.*"),
                Pattern.compile(".*Java_NativeStackOverflowTest_00024Crasher_crash.*"),
                Pattern.compile(".*Java_NativeStackOverflowTest_00024Crasher_crash.*"),
                Pattern.compile(".*Java_NativeStackOverflowTest_00024Crasher_crash.*")
        };
        HsErrFileUtils.checkHsErrFileContent(hsErrFile, positivePatterns, null, true /* check end marker */, false /* verbose */, true /* print on error */);
    }

    public static void main(String[] args) throws Exception {
        final boolean useExplicitStackSize =
                switch(args[0]) { case "dflt" -> false;
                                  case "explicit" -> true;
                                  default -> throw new RuntimeException("invalid"); };
        test(useExplicitStackSize);
    }
}
