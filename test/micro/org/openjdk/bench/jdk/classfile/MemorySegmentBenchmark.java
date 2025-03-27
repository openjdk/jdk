/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CompoundElement;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;

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
 * MemorySegmentBenchmark
 */
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(value = 1)
@State(Scope.Benchmark)
public class MemorySegmentBenchmark {
    private ClassFile classFile;
    private byte[] inputBytes0;
    private ClassModel model0;
    private byte[] inputBytes1;
    private ClassModel model1;
    private MemorySegment outputSegment;
    private SegmentAllocator segmentAllocator;

    @Setup
    public void setup() throws IOException {
        classFile = ClassFile.of();
        FileSystem jrtFs = FileSystems.getFileSystem(URI.create("jrt:/"));
        inputBytes0 = Files.readAllBytes(
                jrtFs.getPath("modules/java.base/java/util/AbstractMap.class"));
        inputBytes1 = Files.readAllBytes(
                jrtFs.getPath("modules/java.base/java/util/TreeMap.class"));
        // preallocate a big-enough space and zero it
        outputSegment = Arena.ofAuto().allocate(1 << 20);
        model0 = classFile.parse(inputBytes0);
        model1 = classFile.parse(inputBytes1);
        segmentAllocator = (_, _) -> outputSegment;
        // expand the models
        consume(model0);
        consume(model1);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void emitWithoutCopy0(Blackhole bh) {
        classFile.transformClass(segmentAllocator, model0, ClassTransform.ACCEPT_ALL);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void emitWithCopy0(Blackhole bh) {
        outputSegment.copyFrom(MemorySegment.ofArray(classFile.transformClass(model0, ClassTransform.ACCEPT_ALL)));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void emitWithoutCopy1(Blackhole bh) {
        classFile.transformClass(segmentAllocator, model1, ClassTransform.ACCEPT_ALL);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void emitWithCopy1(Blackhole bh) {
        outputSegment.copyFrom(MemorySegment.ofArray(classFile.transformClass(model1, ClassTransform.ACCEPT_ALL)));
    }

    private static void consume(CompoundElement<?> parent) {
        parent.forEach(e -> {
            if (e instanceof CompoundElement<?> ce) consume(ce);
        });
    }
}
