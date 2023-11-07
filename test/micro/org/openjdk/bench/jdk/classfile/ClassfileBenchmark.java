/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.jdk.classfile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.ClassTransform;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.CodeElement;
import jdk.internal.classfile.CodeTransform;
import jdk.internal.classfile.CompoundElement;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;

import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * ClassfileBenchmark
 */
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(value = 1, jvmArgsAppend = {
        "--add-exports", "java.base/jdk.internal.classfile=ALL-UNNAMED"})

@State(Scope.Benchmark)
public class ClassfileBenchmark {
    private byte[] benchBytes;
    private ClassModel benchModel;
    private Classfile sharedCP, newCP;
    private ClassTransform threeLevelNoop;
    private ClassTransform addNOP;

    @Setup
    public void setup() throws IOException {
        benchBytes = Files.readAllBytes(
                FileSystems.getFileSystem(URI.create("jrt:/"))
                .getPath("modules/java.base/java/util/AbstractMap.class"));
        sharedCP = Classfile.of();
        newCP = Classfile.of(Classfile.ConstantPoolSharingOption.NEW_POOL);
        benchModel = Classfile.of().parse(benchBytes);
        threeLevelNoop = ClassTransform.transformingMethodBodies(CodeTransform.ACCEPT_ALL);
        addNOP = ClassTransform.transformingMethodBodies(new CodeTransform() {
            @Override
            public void atStart(CodeBuilder cob) {
                cob.nop();
            }
            @Override
            public void accept(CodeBuilder cob, CodeElement coe) {
                cob.with(coe);
            }
        });
        //expand the model
        consume(benchModel);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void parse(Blackhole bh) {
        consume(sharedCP.parse(benchBytes));
    }

    private static void consume(CompoundElement<?> parent) {
        parent.forEach(e -> {
            if (e instanceof CompoundElement<?> ce) consume(ce);
        });
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void transformWithSharedCP(Blackhole bh) {
        bh.consume(sharedCP.transform(benchModel, threeLevelNoop));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void transformWithNewCP(Blackhole bh) {
        bh.consume(newCP.transform(benchModel, threeLevelNoop));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void transformWithAddedNOP(Blackhole bh) {
        bh.consume(sharedCP.transform(benchModel, addNOP));
    }
}
