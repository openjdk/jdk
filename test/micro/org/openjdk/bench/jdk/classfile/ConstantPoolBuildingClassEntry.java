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
package org.openjdk.bench.jdk.classfile;

import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.constant.ClassDesc;
import java.util.List;
import java.util.Random;

import jdk.internal.classfile.impl.Util;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import static java.lang.constant.ConstantDescs.*;

import static org.openjdk.bench.jdk.classfile.TestConstants.*;

/**
 * Tests constant pool builder lookup performance for existing entries.
 */
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(value = 1, jvmArgsAppend = {
        "--enable-preview",
        "--add-exports", "java.base/jdk.internal.classfile.impl=ALL-UNNAMED"})
@State(Scope.Benchmark)
public class ConstantPoolBuildingClassEntry {
    // JDK-8338546
    static final int RUNS = 0xff; // Q: Should we measure consecutive runs at all?
    ConstantPoolBuilder builder;
    List<ClassDesc> classDescs;
    List<ClassDesc> nonIdenticalClassDescs;
    List<String> internalNames;
    Random random;
    int size;

    @Setup(Level.Iteration)
    public void setup() {
        builder = ConstantPoolBuilder.of();
        classDescs = List.of(
                CD_Byte, CD_Object, CD_Long.arrayType(), CD_String, CD_String, CD_Object, CD_Short,
                CD_MethodHandle, CD_MethodHandle, CD_Object, CD_Character, CD_List, CD_ArrayList,
                CD_List, CD_Set, CD_Integer, CD_Object.arrayType(), CD_Enum, CD_Object, CD_MethodHandles_Lookup,
                CD_Long, CD_Set, CD_Object, CD_Character, CD_Integer, CD_System, CD_String, CD_String,
                CD_CallSite, CD_Collection, CD_List, CD_Collection, CD_String
        );
        size = classDescs.size();
        nonIdenticalClassDescs = classDescs.stream().map(cd -> {
            var ret = ClassDesc.ofDescriptor(cd.descriptorString());
            ret.hashCode(); // pre-compute hash code
            return ret;
        }).toList();
        internalNames = classDescs.stream().map(cd -> {
            // also sets up builder
            var ce = builder.classEntry(cd); // pre-computes hash code for cd
            return ce.name().stringValue(); // pre-computes hash code for stringValue
        }).toList();
        random = new Random(-555278801022917158L);
    }

    /**
     * Looking up with identical ClassDesc objects. Happens in bytecode generators reusing
     * constant CD_Xxx.
     */
    @Benchmark
    public void identicalLookup(Blackhole bh) {
        for (int i = 0; i < RUNS; i++) {
            int n = random.nextInt(size);
            bh.consume(builder.classEntry(classDescs.get(n)));
        }
    }

    /**
     * Looking up with non-identical ClassDesc objects. Happens in bytecode generators
     * using ad-hoc Class.describeConstable().orElseThrow() or other parsed ClassDesc.
     * Cannot use identity fast path compared to {@link #identicalLookup}.
     */
    @Benchmark
    public void nonIdenticalLookup(Blackhole bh) {
        for (int i = 0; i < RUNS; i++) {
            int n = random.nextInt(size);
            bh.consume(builder.classEntry(nonIdenticalClassDescs.get(n)));
        }
    }

    /**
     * Looking up with internal names. Closest to ASM behavior.
     * Baseline for {@link #identicalLookup}.
     */
    @Benchmark
    public void internalNameLookup(Blackhole bh) {
        for (int i = 0; i < RUNS; i++) {
            int n = random.nextInt(size);
            bh.consume(builder.classEntry(builder.utf8Entry(internalNames.get(n))));
        }
    }

    /**
     * The default implementation provided by {@link ConstantPoolBuilder#classEntry(ClassDesc)}.
     * Does substring so needs to rehash and has no caching, should be very slow.
     */
    @Benchmark
    public void oldStyleLookup(Blackhole bh) {
        for (int i = 0; i < RUNS; i++) {
            int n = random.nextInt(size);
            bh.consume(builder.classEntry(builder.utf8Entry(Util.toInternalName(classDescs.get(n)))));
        }
    }
}
