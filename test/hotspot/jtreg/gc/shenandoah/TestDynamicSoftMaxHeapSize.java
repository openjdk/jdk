/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

/*
 * @test id=dynamicSoftMaxHeapSize
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run driver TestDynamicSoftMaxHeapSize
 */
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.dcmd.PidJcmdExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class TestDynamicSoftMaxHeapSize {
    static final int K = 1024;
    static final List<String> COMMON_COMMANDS = Arrays.asList("-Xms16m",
            "-Xmx512m",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UseShenandoahGC",
            "-Xlog:gc=debug",
            "-Dtest.jdk=" + System.getProperty("test.jdk"),
            "-Dcompile.jdk=" + System.getProperty("compile.jdk"),
            "-Dtarget=10000"
            );
    static final List<String> HEURISTICS = Arrays.asList("adaptive", "aggressive", "compact", "static");


    public static void main(String[] args) throws Exception {
        // satb gc mode
        List<String> satbGCModeArgsNoDegeneratedGC = new ArrayList<>(COMMON_COMMANDS);
        satbGCModeArgsNoDegeneratedGC.add("-XX:ShenandoahGCMode=satb");

        for (String heuristic : HEURISTICS) {
            satbGCModeArgsNoDegeneratedGC.add("-XX:ShenandoahGCHeuristics=" + heuristic);
            satbGCModeArgsNoDegeneratedGC.add(SoftMaxSetFlagOnlyTest.class.getName());

            ProcessBuilder satbPb = ProcessTools.createLimitedTestJavaProcessBuilder(satbGCModeArgsNoDegeneratedGC);
            new OutputAnalyzer(satbPb.start()).shouldHaveExitValue(0);
        }

        // passive gc mode
        List<String> degeneratedJvmArgs = Arrays.asList("-XX:+ShenandoahDegeneratedGC", "-XX:-ShenandoahDegeneratedGC");

        List<String> passiveGCModeArgs = new ArrayList<>(COMMON_COMMANDS);
        passiveGCModeArgs.add("-XX:ShenandoahGCMode=passive");

        for (String degeneratedJvmArg : degeneratedJvmArgs) {
            passiveGCModeArgs.add(degeneratedJvmArg);
            passiveGCModeArgs.add(SoftMaxSetFlagOnlyTest.class.getName());

            ProcessBuilder passivePb = ProcessTools.createLimitedTestJavaProcessBuilder(passiveGCModeArgs);
            new OutputAnalyzer(passivePb.start()).shouldHaveExitValue(0);
        }

        // generational gc mode. gen shen only works in pair with adaptive heuristic
        List<String> genShenGCModeSetFlagOnlyArgs = new ArrayList<>(COMMON_COMMANDS);
        genShenGCModeSetFlagOnlyArgs.add("-XX:ShenandoahGCMode=generational");

        genShenGCModeSetFlagOnlyArgs.add(SoftMaxSetFlagOnlyTest.class.getName());

        ProcessBuilder genShenPbSetFlagOnly = ProcessTools.createLimitedTestJavaProcessBuilder(genShenGCModeSetFlagOnlyArgs);
        new OutputAnalyzer(genShenPbSetFlagOnly.start()).shouldHaveExitValue(0);

        // generational gc mode. Verify if it can detect soft max heap change when app is running
        int xMsHeapInByte = 16 * K * K;
        int xMxHeapInByte = 512 * K * K;
        int softMaxInByte = Utils.getRandomInstance().nextInt(xMsHeapInByte, xMxHeapInByte + 1);

        List<String> genShenGCModeValidateFlagArgs = new ArrayList<>(COMMON_COMMANDS);
        genShenGCModeValidateFlagArgs.add("-XX:ShenandoahGCMode=generational");
        genShenGCModeValidateFlagArgs.add("-DsoftMaxCapacity=" + softMaxInByte);

        genShenGCModeValidateFlagArgs.add(SoftMaxWithExpectationTest.class.getName());

        ProcessBuilder genShenPbValidateFlag = ProcessTools.createLimitedTestJavaProcessBuilder(genShenGCModeValidateFlagArgs);
        OutputAnalyzer output = new OutputAnalyzer(genShenPbValidateFlag.start());
        output.shouldHaveExitValue(0);
        output.shouldContain("soft_max_capacity: " + softMaxInByte);
    }

    public static class SoftMaxSetFlagOnlyTest {
        static final long TARGET_MB = Long.getLong("target", 10_000); // 10 Gb allocation
        static final long STRIDE = 10_000_000;

        static volatile Object sink;

        public static void main(String[] args) throws Exception {
            long count = TARGET_MB * 1024 * 1024 / 16;
            Random r = Utils.getRandomInstance();
            PidJcmdExecutor jcmd = new PidJcmdExecutor();

            for (long c = 0; c < count; c += STRIDE) {
                // Sizes specifically include heaps below Xms and above Xmx to test saturation code.
                jcmd.execute("VM.set_flag SoftMaxHeapSize " + r.nextInt(768*1024*1024), true);
                for (long s = 0; s < STRIDE; s++) {
                    sink = new Object();
                }
                Thread.sleep(1);
            }
        }
    }

    public static class SoftMaxWithExpectationTest {
        static final long TOTAL = 100_000_000;

        static volatile Object sink;

        public static void main(String[] args) throws Exception {
            int expectedSoftMaxHeapSize = Integer.getInteger("softMaxCapacity", 0);
            PidJcmdExecutor jcmd = new PidJcmdExecutor();
            jcmd.execute("VM.set_flag SoftMaxHeapSize " + expectedSoftMaxHeapSize, false);

            for (long s = 0; s < TOTAL; s++) {
                sink = new Object();
            }
        }
    }
}
