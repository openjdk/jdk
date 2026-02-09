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

/**
 * @test id=satb-adaptive
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -Xms100m -Xmx512m -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -Xlog:gc=info -Dtarget=10000
 *      -XX:ShenandoahGCMode=satb
 *      -XX:+ShenandoahDegeneratedGC
 *      -XX:ShenandoahGCHeuristics=adaptive
 *      TestDynamicSoftMaxHeapSize
 *
 */

/**
 * @test id=satb-aggressive
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -Xms100m -Xmx512m -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -Xlog:gc=info -Dtarget=10000
 *      -XX:ShenandoahGCMode=satb
 *      -XX:+ShenandoahDegeneratedGC
 *      -XX:ShenandoahGCHeuristics=aggressive
 *      TestDynamicSoftMaxHeapSize
 *
 */

/**
 * @test id=satb-compact
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -Xms100m -Xmx512m -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -Xlog:gc=info -Dtarget=10000
 *      -XX:ShenandoahGCMode=satb
 *      -XX:+ShenandoahDegeneratedGC
 *      -XX:ShenandoahGCHeuristics=compact
 *      TestDynamicSoftMaxHeapSize
 *
 */

/**
 * @test id=satb-static
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -Xms100m -Xmx512m -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -Xlog:gc=info -Dtarget=10000
 *      -XX:ShenandoahGCMode=satb
 *      -XX:+ShenandoahDegeneratedGC
 *      -XX:ShenandoahGCHeuristics=static
 *      TestDynamicSoftMaxHeapSize
 *
 */

/**
 * @test id=passive
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -Xms16m -Xmx512m -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=passive
 *      -XX:+ShenandoahDegeneratedGC
 *      -Dtarget=10000
 *      TestDynamicSoftMaxHeapSize
 *
 * @run main/othervm -Xms16m -Xmx512m -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -XX:ShenandoahGCMode=passive
 *      -XX:-ShenandoahDegeneratedGC
 *      -Dtarget=10000
 *      TestDynamicSoftMaxHeapSize
 */

/**
 * @test id=generational
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -Xms100m -Xmx512m -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -Xlog:gc=info -Dtarget=10000
 *      -XX:ShenandoahGCMode=generational
 *      -XX:ShenandoahGCHeuristics=adaptive
 *      TestDynamicSoftMaxHeapSize
 *
 */

/**
 * @test id=generational-softMaxHeapSizeValidation
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -DvalidateSoftMaxHeap=true
 *      TestDynamicSoftMaxHeapSize
 *      -Xms100m -Xmx512m -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions
 *      -XX:+UseShenandoahGC -Xlog:gc=info -Dtarget=10000 -DverifySoftMaxHeapValue=true
 *      -XX:ShenandoahGCMode=generational
 *      -XX:ShenandoahGCHeuristics=adaptive
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
    static final int XMS_MB = 100;
    static final int XMX_MB = 512;

    public static void main(String[] args) throws Exception {
        if ("true".equals(System.getProperty("validateSoftMaxHeap"))) {
            List<String> flagArgs = new ArrayList<>(Arrays.asList(args));

            int softMaxInMb = Utils.getRandomInstance().nextInt(XMS_MB, XMX_MB);
            flagArgs.add("-DsoftMaxCapacity=" + softMaxInMb * K * K);
            flagArgs.add("-Dtest.jdk=" + System.getProperty("test.jdk"));
            flagArgs.add("-Dcompile.jdk=" + System.getProperty("compile.jdk"));

            flagArgs.add(SoftMaxWithExpectationTest.class.getName());

            ProcessBuilder genShenPbValidateFlag = ProcessTools.createLimitedTestJavaProcessBuilder(flagArgs);
            OutputAnalyzer output = new OutputAnalyzer(genShenPbValidateFlag.start());
            output.shouldHaveExitValue(0);
            output.shouldContain(String.format("Soft Max Heap Size: %dM -> %dM", XMX_MB, softMaxInMb)); // By default, the soft max heap size is Xmx
        } else {
            SoftMaxSetFlagOnlyTest.test();
        }
    }

    public static class SoftMaxSetFlagOnlyTest {
        static final long TARGET_MB = Long.getLong("target", 10_000); // 10 Gb allocation
        static final long STRIDE = 10_000_000;

        static volatile Object sink;

        public static void test() throws Exception {
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
