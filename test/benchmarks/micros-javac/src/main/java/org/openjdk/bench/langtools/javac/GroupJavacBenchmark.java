/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.openjdk.bench.langtools.javac;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class GroupJavacBenchmark extends JavacBenchmark {

    public static final String COLD_GROUP_NAME = "coldGroup";
    public static final int COLD_ITERATION_WARMUPS = 0;
    public static final int COLD_ITERATIONS = 1;
    public static final int COLD_FORK_WARMUPS = 1;
    public static final int COLD_FORKS = 15;

    public static final String HOT_GROUP_NAME = "hotGroup";
    public static final int HOT_ITERATION_WARMUPS = 8;
    public static final int HOT_ITERATIONS = 10;
    public static final int HOT_FORK_WARMUPS = 0;
    public static final int HOT_FORKS = 1;

    @Benchmark
    @Group(COLD_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = COLD_ITERATION_WARMUPS)
    @Measurement(iterations = COLD_ITERATIONS)
    @Fork(warmups = COLD_FORK_WARMUPS, value = COLD_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void cold1_Init() throws InterruptedException {
        Stage.Init.waitFor();
    }

    @Benchmark
    @Group(COLD_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = COLD_ITERATION_WARMUPS)
    @Measurement(iterations = COLD_ITERATIONS)
    @Fork(warmups = COLD_FORK_WARMUPS, value = COLD_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void cold2_Parse() throws InterruptedException {
        Stage.Parse.waitFor();
    }

    @Benchmark
    @Group(COLD_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = COLD_ITERATION_WARMUPS)
    @Measurement(iterations = COLD_ITERATIONS)
    @Fork(warmups = COLD_FORK_WARMUPS, value = COLD_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void cold3_InitModules() throws InterruptedException {
        Stage.InitModules.waitFor();
    }

    @Benchmark
    @Group(COLD_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = COLD_ITERATION_WARMUPS)
    @Measurement(iterations = COLD_ITERATIONS)
    @Fork(warmups = COLD_FORK_WARMUPS, value = COLD_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void cold4_Enter() throws InterruptedException {
        Stage.Enter.waitFor();
    }

    @Benchmark
    @Group(COLD_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = COLD_ITERATION_WARMUPS)
    @Measurement(iterations = COLD_ITERATIONS)
    @Fork(warmups = COLD_FORK_WARMUPS, value = COLD_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void cold5_Attribute() throws InterruptedException {
        Stage.Attribute.waitFor();
    }

    @Benchmark
    @Group(COLD_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = COLD_ITERATION_WARMUPS)
    @Measurement(iterations = COLD_ITERATIONS)
    @Fork(warmups = COLD_FORK_WARMUPS, value = COLD_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void cold6_Flow() throws InterruptedException {
        Stage.Flow.waitFor();
    }

    @Benchmark
    @Group(COLD_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = COLD_ITERATION_WARMUPS)
    @Measurement(iterations = COLD_ITERATIONS)
    @Fork(warmups = COLD_FORK_WARMUPS, value = COLD_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void cold7_Desugar() throws InterruptedException {
        Stage.Desugar.waitFor();
    }

    @Benchmark
    @Group(COLD_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = COLD_ITERATION_WARMUPS)
    @Measurement(iterations = COLD_ITERATIONS)
    @Fork(warmups = COLD_FORK_WARMUPS, value = COLD_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void cold8_Generate(Blackhole bh) throws IOException {
        compile(bh, Stage.Generate);
    }

    @Benchmark
    @Group(HOT_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = HOT_ITERATION_WARMUPS)
    @Measurement(iterations = HOT_ITERATIONS)
    @Fork(warmups = HOT_FORK_WARMUPS, value = HOT_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void hot1_Init() throws InterruptedException {
        Stage.Init.waitFor();
    }

    @Benchmark
    @Group(HOT_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = HOT_ITERATION_WARMUPS)
    @Measurement(iterations = HOT_ITERATIONS)
    @Fork(warmups = HOT_FORK_WARMUPS, value = HOT_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void hot2_Parse() throws InterruptedException {
        Stage.Parse.waitFor();
    }

    @Benchmark
    @Group(HOT_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = HOT_ITERATION_WARMUPS)
    @Measurement(iterations = HOT_ITERATIONS)
    @Fork(warmups = HOT_FORK_WARMUPS, value = HOT_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void hot3_InitModules() throws InterruptedException {
        Stage.InitModules.waitFor();
    }

    @Benchmark
    @Group(HOT_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = HOT_ITERATION_WARMUPS)
    @Measurement(iterations = HOT_ITERATIONS)
    @Fork(warmups = HOT_FORK_WARMUPS, value = HOT_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void hot4_Enter() throws InterruptedException {
        Stage.Enter.waitFor();
    }

    @Benchmark
    @Group(HOT_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = HOT_ITERATION_WARMUPS)
    @Measurement(iterations = HOT_ITERATIONS)
    @Fork(warmups = HOT_FORK_WARMUPS, value = HOT_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void hot5_Attribute() throws InterruptedException {
        Stage.Attribute.waitFor();
    }

    @Benchmark
    @Group(HOT_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = HOT_ITERATION_WARMUPS)
    @Measurement(iterations = HOT_ITERATIONS)
    @Fork(warmups = HOT_FORK_WARMUPS, value = HOT_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void hot6_Flow() throws InterruptedException {
        Stage.Flow.waitFor();
    }

    @Benchmark
    @Group(HOT_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = HOT_ITERATION_WARMUPS)
    @Measurement(iterations = HOT_ITERATIONS)
    @Fork(warmups = HOT_FORK_WARMUPS, value = HOT_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void hot7_Desugar() throws InterruptedException {
        Stage.Desugar.waitFor();
    }

    @Benchmark
    @Group(HOT_GROUP_NAME)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = HOT_ITERATION_WARMUPS)
    @Measurement(iterations = HOT_ITERATIONS)
    @Fork(warmups = HOT_FORK_WARMUPS, value = HOT_FORKS, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void hot8_Generate(Blackhole bh) throws IOException {
        compile(bh, Stage.Generate);
    }
}
