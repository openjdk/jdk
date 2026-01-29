/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SigTestDriver {
    public static void main(String[] args) {
        // No signal tests on Windows yet; so setting to no-op
        if (Platform.isWindows()) {
            throw new SkippedException("no signal tests on Windows");
        }

        // At least one argument should be specified
        if ((args == null) || (args.length < 1)) {
            throw new IllegalArgumentException("At lease one argument should be specified, the signal name");
        }

        String signame = args[0];
        switch (signame) {
            case "SIGWAITING":
            case "SIGKILL":
            case "SIGSTOP":
            case "SIGUSR2": {
                throw new SkippedException("signals SIGWAITING, SIGKILL, SIGSTOP and SIGUSR2 can't be tested");
            }
        }

        Path test = Paths.get(Utils.TEST_NATIVE_PATH)
                         .resolve("sigtest")
                         .toAbsolutePath();
        String envVar = Platform.sharedLibraryPathVariableName();

        List<String> cmd = new ArrayList<>();
        Collections.addAll(cmd,
                test.toString(),
                "-sig",
                signame,
                "-mode",
                null, // modeIdx
                "-scenario",
                null // scenarioIdx
        );
        int modeIdx = 4;
        int scenarioIdx = 6;

        // add external flags
        cmd.addAll(vmargs());

        // add test specific arguments w/o signame
        var argList = Arrays.asList(args)
                            .subList(1, args.length);
        cmd.addAll(argList);

        String failureMessage = null;
        boolean passed = true;

        for (String mode : new String[] {"sigset", "sigaction"}) {
            // Scenarios postpre and postpost requires libjsig.
            // The other scenarios are run with libjsig to validate the deprecation warning.
            for (String scenario : new String[] {"nojvm", "prepre", "prepost", "postpre#libjsig", "postpost#libjsig",
                    "nojvm#libjsig", "prepre#libjsig", "prepost#libjsig", }) {
                cmd.set(modeIdx, mode);
                cmd.set(scenarioIdx, scenario.replace("#libjsig", ""));
                System.out.printf("START TESTING: SIGNAL = %s, MODE = %s, SCENARIO=%s%n", signame, mode, scenario);
                System.out.printf("Do execute: %s%n", cmd.toString());

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.environment().merge(envVar, Platform.jvmLibDir().toString(),
                        (x, y) -> y + File.pathSeparator + x);
                pb.environment().put("CLASSPATH", Utils.TEST_CLASS_PATH);

                boolean useLibjsig = scenario.endsWith("#libjsig");
                if (useLibjsig) {
                    pb.environment().merge("LD_PRELOAD", libjsig().toString(),
                            (x, y) -> y + File.pathSeparator + x);
                }

                try {
                    OutputAnalyzer oa = ProcessTools.executeProcess(pb);
                    oa.reportDiagnosticSummary();
                    int exitCode = oa.getExitValue();
                    if (exitCode == 0) {
                        // Skip deprecation warning check on MacOSX (see JDK-8346381)
                        if (useLibjsig && !Platform.isOSX()) {
                            // verify that deprecation warning for sigset/signal is printed
                            // only in the correct scenarios
                            boolean deprecatedSigFunctionUsed = mode.equals("sigset");
                            boolean jvmInvolved = !scenario.contains("nojvm");
                            boolean warningPrinted = oa.contains("VM warning: the use of signal() and sigset()");
                            boolean sigUsedByJVM = sigIsUsedByJVM(signame);
                            if (deprecatedSigFunctionUsed && jvmInvolved && sigUsedByJVM) {
                                if (!warningPrinted) {
                                    failureMessage = "FAILED: Missing deprecation warning for mode " + mode +
                                                     ", scenario: "+ scenario + ", signal " + signame;
                                    passed = false;
                                }
                            } else if (warningPrinted) {
                                failureMessage = "FAILED: Deprecation warning shouldn't be printed for mode " + mode +
                                                 ", scenario: "+ scenario + ", signal " + signame;
                                passed = false;
                            }
                        } else {
                            System.out.println("PASSED with exit code 0");
                        }
                    } else {
                        failureMessage = "FAILED with exit code " + exitCode;
                        passed = false;
                    }
                    if (!passed) {
                        System.out.println(failureMessage);
                    }
                } catch (Exception e) {
                    throw new Error("execution failed", e);
                }
            }
        }

        if (!passed) {
            throw new Error(failureMessage != null ? failureMessage : "test failed");
        }
    }

    private static List<String> vmargs() {
        return Stream.concat(Arrays.stream(Utils.VM_OPTIONS.split(" ")),
                             Arrays.stream(Utils.JAVA_OPTIONS.split(" ")))
                     .filter(s -> !s.isEmpty())
                     .filter(s -> s.startsWith("-X"))
                     .flatMap(arg -> Stream.of("-vmopt", arg))
                     .collect(Collectors.toList());
    }

    private static Path libjsig() {
        return Platform.jvmLibDir().resolve(Platform.buildSharedLibraryName("jsig"));
    }

    /**
     * Return true for the chainable signals that are used by the JVM.
     * See src/hotspot/os/posix/signals_posix.cpp
     * @param signame
     * @return true if signal is used by JVM, false otherwise
     */
    private static boolean sigIsUsedByJVM(String signame) {
        switch(signame) {
            case "SIGSEGV":
            case "SIGPIPE":
            case "SIGBUS":
            case "SIGILL":
            case "SIGFPE":
            case "SIGXFSZ":
                return true;
            case "SIGTRAP":
                return Platform.isPPC();
            default:
                return false;
        }
    }
}
