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
 *
 */

/**
 * @test id=g1
 * @requires vm.gc.G1
 * @bug 8392086
 * @summary Test AOT compilation for Valhalla
 * @requires vm.flagless
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib /test/setup_aot
 * @enablePreview
 * @build ${test.main.class}
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 TestLocalDateTime
 * @run driver/timeout=480 ${test.main.class} G1
 */

/**
 * @test id=parallel
 * @requires vm.gc.Parallel
 * @requires vm.flagless
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib /test/setup_aot
 * @enablePreview
 * @build ${test.main.class}
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 TestLocalDateTime
 * @run driver/timeout=480 ${test.main.class} Parallel
 */

/**
 * @test id=serial
 * @requires vm.gc.Serial
 * @requires vm.flagless
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib /test/setup_aot
 * @enablePreview
 * @build ${test.main.class}
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 TestLocalDateTime
 * @run driver/timeout=480 ${test.main.class} Serial
 */

/**
 * @test id=shenandoah
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib /test/setup_aot
 * @enablePreview
 * @build ${test.main.class}
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 TestLocalDateTime
 * @run driver/timeout=480 ${test.main.class} Shenandoah
 */

/**
 * @test id=Z
 * @requires vm.gc.Z
 * @requires vm.flagless
 * @requires vm.cds.supports.aot.code.caching
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @library /test/lib /test/setup_aot
 * @enablePreview
 * @build ${test.main.class}
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 TestLocalDateTime
 * @run driver/timeout=480 ${test.main.class} Z
 */

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class TestAOTLocalDateTime {

    public static void main(String... args) throws Exception {
        if (args.length == 0) {
            throw new RuntimeException("Expected GC name");
        }
        if (args[0].equals("Z")) {
            return; // JDK-8392746
        }
        Tester t;
        t = new Tester("C2", 4, "java.time.LocalDate::create", args[0], false);
        t.run(new String[] {"AOT", "--two-step-training"});
        t = new Tester("C1", 2, "java.time.LocalDateTime::<init>", args[0], false);
        t.run(new String[] {"AOT", "--two-step-training"});

        if (System.getProperty("jdk.debug", "").contains("debug")) {
          // Run with VerifyOops which is only supported in debug VM
          t = new Tester("C2", 4, "java.time.LocalDate::create", args[0], true);
          t.run(new String[] {"AOT", "--two-step-training"});
          t = new Tester("C1", 2, "java.time.LocalDateTime::<init>", args[0], true);
          t.run(new String[] {"AOT", "--two-step-training"});
        }
    }

    static class Tester extends CDSAppTester {
        private String gcName;
        private boolean withVerifyOops;
        private String compName;
        private String methodName;
        private int compTier;
        private int compCount;

        public Tester(String comp, int tier, String method, String gc, boolean verifyOops) {
            super("TestAOTLocalDateTime");
            gcName = gc;
            withVerifyOops = verifyOops;
            compName = comp;
            methodName = method;
            compTier = tier;
            compCount = tier / 2; // 2 for C2 and 1 for C1 only
        }

        public List<String> getGCArgs() {
            List<String> args = new ArrayList<String>();
            args.add("-Xmx100M");
            if (withVerifyOops) {
              args.add("-XX:+VerifyOops");
            }
            switch (gcName) {
            case "G1":
            case "Parallel":
            case "Serial":
            case "Shenandoah":
            case "Z":
                args.add("-XX:+Use" + gcName + "GC");
            return args;
            default:
                throw new RuntimeException("Unexpected GC name " + gcName);
            }
        }

        @Override
        public String classpath(RunMode runMode) {
            return "app.jar";
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            List<String> args = getGCArgs();
            args.addAll(List.of("--enable-preview",
                                "-Xbatch",
                                "-XX:+UnlockDiagnosticVMOptions",
                                "-XX:+AbortVMOnAOTCodeFailure",
                                "-XX:CICompilerCount=" + compCount,
                                "-XX:+TieredCompilation",
                                "-XX:TieredStopAtLevel=" + compTier,
                                "-XX:+PrintCompilation",
                                "-XX:CompileCommand=quiet",
                                "-XX:CompileCommand=compileonly," + methodName,
                                "-XX:CompileCommand=exclude," + methodName + ",1", // exclude Tier 1 compilation
                                "-XX:CompileCommand=dontinline,*::makeLocalDateTime"));
            // Add flags for logs
            args.addAll(List.of("-Xlog:aot+codecache+nmethod=debug",
                                "-Xlog:aot+codecache+init=debug",
                                "-Xlog:aot+codecache+exit=debug"));

            if (runMode == RunMode.ASSEMBLY) {
                args.addAll(List.of("-Xlog:aot+codecache+oops=debug",
                                    "-Xlog:aot+codecache+metadata=debug"));
            }
            return args.toArray(new String[args.size()]);
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] { "TestLocalDateTime" };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.ASSEMBLY) {
                if (compTier == 4) {
                  out.shouldMatch("aot,codecache,nmethod.*\\(A4\\).*LocalDate.*create.*wrote to AOT Code Cache");
                }
                if (compTier == 2) {
                  out.shouldMatch("aot,codecache,nmethod.*\\(A2\\).*LocalDateTime.*<init>.*wrote to AOT Code Cache");
                }
                out.shouldMatch("aot,codecache,exit.*\\s+AOT code cache size: [1-9]\\d+ bytes");
            } else if (runMode == RunMode.PRODUCTION) {
                if (compTier == 4) {
                  out.shouldMatch("aot,codecache,nmethod.*\\(A4\\).*LocalDate.*create.*Loaded nmethod from AOT Code Cache");
                }
                /* In production run VM may request Tier 2 compilation before
                 * TrainingReplayThread can process class init dependencies.
                 * As result normal JIT Tier2 compilation may happen before
                 * A2 code load is requested. And with presence of JIT code
                 * AOT code is not requested for this methodi any more.
                 */
                /*
                if (compTier == 2) {
                  out.shouldMatch("aot,codecache,nmethod.*\\(A2\\).*LocalDateTime.*<init>.*Loaded nmethod from AOT Code Cache");
                }
                */
                out.shouldMatch("aot,codecache,init.*\\s+Loaded [1-9]\\d+ AOT code entries from AOT Code Cache");
                out.shouldContain("Result: 1970-01-02T03:46:39");
            }
            out.shouldHaveExitValue(0);
        }
    }
}

class TestLocalDateTime {
    public static void main(String... args) {
        LocalDateTime localDateTime = makeLocalDateTime(0);
        for (int i = 0; i < 100000; i++) {
            localDateTime = makeLocalDateTime(i);
        }
        System.out.println("Result: " + localDateTime);
    }

    public static LocalDateTime makeLocalDateTime(int i) {
        int sec = i % 60;
        int min = (i / 60) % 60;
        int hour = (i / (60 * 60)) % 24;
        int day = ((i / (60 * 60 * 24)) % 31) + 1;
        return LocalDateTime.of(1970, 1, day, hour, min, sec);
    }
}
