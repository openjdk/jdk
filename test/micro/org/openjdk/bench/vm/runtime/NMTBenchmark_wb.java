/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.vm.runtime;

import org.openjdk.bench.vm.lang.Throw;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.lang.*;
import java.util.*;

import jdk.internal.misc.Unsafe;
import jdk.test.whitebox.WhiteBox;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public abstract class NMTBenchmark_wb {

    @Param({"2", "4", "8", "16"})
    public int THREADS;

    @Param({"25", "50", "100", "200", "400"})
    public int REGIONS;

    @Param({"25", "50", "100"})
    public int SUB_REGIONS;

    private static final int PAGE_SIZE = 1024 * 4;

    // Need to wrap WhiteBox in a holder class so that it doesn't get initialized on the host VM (which won't
    // have WB enabled.
    private static class WhiteBoxHolder {

        private static final WhiteBox WB;

        static {
            System.out.println("Current user dir: " + System.getProperty("user.dir"));
            WhiteBox wb = null;
            try {
                wb = WhiteBox.getWhiteBox();
            } catch (Throwable t) {
                System.out.println("WhiteBox initialization failure: " + t);
                t.printStackTrace();
                // Explicit exit to avoid initialization loops.
                System.exit(17);
            }
            WB = wb;
        }
    }

    private static long reserve (long size           ) { return WhiteBoxHolder.WB.NMTReserveMemory (size                             ); }
    private static void commit  (long base, int pno  ) {        WhiteBoxHolder.WB.NMTCommitMemory  (base + pno * PAGE_SIZE, PAGE_SIZE); }
    private static void uncommit(long base, int pno  ) {        WhiteBoxHolder.WB.NMTUncommitMemory(base + pno * PAGE_SIZE, PAGE_SIZE); }
    private static void release (long base, long size) {        WhiteBoxHolder.WB.NMTReleaseMemory (base                  , size     ); }

    public static void doAllMemoryOps(int reserved_regions_count, int committed_regions_count) {
        long region_size = committed_regions_count * PAGE_SIZE;
        long[] base_array = new long[reserved_regions_count];

        for (int i = 0; i < reserved_regions_count; i++)
          base_array[i] = reserve(region_size);

        for (int r = 0; r < reserved_regions_count; r++) {
          long base = base_array[r];
          for (int i = 0; i < committed_regions_count; i += 4) commit  (base, i    );
          for (int i = 1; i < committed_regions_count; i += 4) commit  (base, i    ); // causes merge from right
          for (int i = 4; i < committed_regions_count; i += 4) commit  (base, i - 1); // causes merge from left
          for (int i = 4; i < committed_regions_count; i += 4) uncommit(base, i - 1); // causes split from left
          for (int i = 1; i < committed_regions_count; i += 4) uncommit(base, i    ); // causes split from right
          for (int i = 0; i < committed_regions_count; i += 4) uncommit(base, i    ); // remove the regions
        }

        for (int i = 0; i < reserved_regions_count; i++)
          release(base_array[i], region_size);
    }

    public static void doTest(int reserved_regions_count, int threads_count, int committed_regions_count) throws InterruptedException{
        int regions_per_thread = reserved_regions_count / threads_count;
        Thread[] threads =  new Thread[threads_count];
        for (int t = 0; t < threads_count; t++) {
            threads[t] = new Thread(() -> doAllMemoryOps(regions_per_thread, committed_regions_count));
            threads[t].start();
        }
        for (Thread t: threads) t.join();
    }

    @Benchmark
    public void virtualMemoryTests() {
        try { doTest(REGIONS, THREADS, SUB_REGIONS); }
        catch (Throwable t) {
            System.out.println(t.getMessage());
        }
    }

    public static final String ADD_EXPORTS = "--add-exports";

    public static final String MISC_PACKAGE = "java.base/jdk.internal.misc=ALL-UNNAMED"; // used for Unsafe API

    public static final String WB_UNLOCK_OPTION = "-XX:+UnlockDiagnosticVMOptions";

    public static final String WB_API = "-XX:+WhiteBoxAPI";

    public static final String WB_JAR_APPEND = "-Xbootclasspath/a:lib-test/wb.jar";

    @Fork(value = 2, jvmArgsPrepend = { WB_JAR_APPEND, WB_UNLOCK_OPTION, WB_API, ADD_EXPORTS, MISC_PACKAGE, "-XX:NativeMemoryTracking=off"})
    public static class NMTOff extends NMTBenchmark_wb { }

    @Fork(value = 2, jvmArgsPrepend = { WB_JAR_APPEND, WB_UNLOCK_OPTION, WB_API, ADD_EXPORTS, MISC_PACKAGE, "-XX:NativeMemoryTracking=summary"})
    public static class NMTSummary extends NMTBenchmark_wb { }

    // @Fork(value = 2, jvmArgsPrepend = { WB_JAR_APPEND, WB_UNLOCK_OPTION, WB_API, ADD_EXPORTS, MISC_PACKAGE, "-XX:NativeMemoryTracking=detail"})
    // public static class NMTDetail extends NMTBenchmark_wb { }

}