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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import static java.lang.constant.ConstantDescs.*;

import static org.openjdk.bench.jdk.classfile.TestConstants.*;

/**
 * Tests constant pool builder lookup performance for ClassEntry.
 * Note that ClassEntry is available only for reference types.
 */
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
@Fork(value = 1)
@State(Scope.Benchmark)
public class ConstantPoolBuildingClassEntry {
    // JDK-8338546
    ConstantPoolBuilder builder;
    List<ClassDesc> classDescs;
    List<ClassDesc> nonIdenticalClassDescs;
    List<String> internalNames;
    List<ClassDesc> nonDuplicateClassDescs;
    List<String> nonDuplicateInternalNames;
    int size;

    @Setup(Level.Iteration)
    public void setup() {
        builder = ConstantPoolBuilder.of();
        // Note these can only be reference types, no primitives
        classDescs = List.of(
                CD_Byte, CD_Object, CD_Long.arrayType(), CD_String, CD_String, CD_Object, CD_Short,
                CD_MethodHandle, CD_MethodHandle, CD_Object, CD_Character, CD_List, CD_ArrayList,
                CD_List, CD_Set, CD_Integer, CD_Object.arrayType(), CD_Enum, CD_Object, CD_MethodHandles_Lookup,
                CD_Long, CD_Set, CD_Object, CD_Character, CD_Integer, CD_System, CD_String, CD_String,
                CD_CallSite, CD_Collection, CD_List, CD_Collection, CD_String, CD_int.arrayType()
        );
        size = classDescs.size();
        nonIdenticalClassDescs = classDescs.stream().map(cd -> {
            var ret = ClassDesc.ofDescriptor(new String(cd.descriptorString()));
            ret.hashCode(); // pre-compute hash code for cd
            return ret;
        }).toList();
        internalNames = classDescs.stream().map(cd -> {
            // also sets up builder
            cd.hashCode(); // pre-computes hash code for cd
            var ce = builder.classEntry(cd);
            var ret = ce.name().stringValue();
            ret.hashCode(); // pre-computes hash code for stringValue
            return ret;
        }).toList();
        nonDuplicateClassDescs = List.copyOf(new LinkedHashSet<>(classDescs));
        nonDuplicateInternalNames = nonDuplicateClassDescs.stream().map(cd ->
                builder.classEntry(cd).asInternalName()).toList();
    }

    // Copied from jdk.internal.classfile.impl.Util::toInternalName
    // to reduce internal dependencies
    public static String toInternalName(ClassDesc cd) {
        var desc = cd.descriptorString();
        if (desc.charAt(0) == 'L')
            return desc.substring(1, desc.length() - 1);
        throw new IllegalArgumentException(desc);
    }

    /**
     * Looking up with identical ClassDesc objects. Happens in bytecode generators reusing
     * constant CD_Xxx.
     */
    @Benchmark
    public void identicalLookup(Blackhole bh) {
        for (var cd : classDescs) {
            bh.consume(builder.classEntry(cd));
        }
    }

    /**
     * Looking up with non-identical ClassDesc objects. Happens in bytecode generators
     * using ad-hoc Class.describeConstable().orElseThrow() or other parsed ClassDesc.
     * Cannot use identity fast path compared to {@link #identicalLookup}.
     */
    @Benchmark
    public void nonIdenticalLookup(Blackhole bh) {
        for (var cd : nonIdenticalClassDescs) {
            bh.consume(builder.classEntry(cd));
        }
    }

    /**
     * Looking up with internal names. Closest to ASM behavior.
     * Baseline for {@link #identicalLookup}.
     */
    @Benchmark
    public void internalNameLookup(Blackhole bh) {
        for (var name : internalNames) {
            bh.consume(builder.classEntry(builder.utf8Entry(name)));
        }
    }

    /**
     * The default implementation provided by {@link ConstantPoolBuilder#classEntry(ClassDesc)}.
     * Does substring so needs to rehash and has no caching, should be very slow.
     */
    @Benchmark
    public void oldStyleLookup(Blackhole bh) {
        for (var cd : classDescs) {
            var s = cd.isClassOrInterface() ? toInternalName(cd) : cd.descriptorString();
            bh.consume(builder.classEntry(builder.utf8Entry(s)));
        }
    }

    /**
     * Measures performance of creating new class entries in new constant pools with symbols.
     */
    @Benchmark
    public void freshCreationWithDescs(Blackhole bh) {
        var cp = ConstantPoolBuilder.of();
        for (var cd : nonDuplicateClassDescs) {
            bh.consume(cp.classEntry(cd));
        }
    }

    /**
     * Measures performance of creating new class entries in new constant pools with internal names.
     */
    @Benchmark
    public void freshCreationWithInternalNames(Blackhole bh) {
        var cp = ConstantPoolBuilder.of();
        for (var name : nonDuplicateInternalNames) {
            bh.consume(cp.classEntry(cp.utf8Entry(name)));
        }
    }
}
