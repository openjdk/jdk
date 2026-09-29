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
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class SingleJavacBenchmark extends JavacBenchmark {

    @Param
    public Stage stopStage;

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    @Fork(warmups = 1, value = 15, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void compileCold(Blackhole bh) throws IOException {
        compile(bh, stopStage);
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 8)
    @Measurement(iterations = 10)
    @Fork(warmups = 0, value = 1, jvmArgsPrepend = { "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",  "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED" })
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void compileHot(Blackhole bh) throws IOException {
        compile(bh, stopStage);
    }
}
