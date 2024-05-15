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
package org.openjdk.bench.java.lang.classfile;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Performance of conversion from type descriptor objects to type kind.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 6, time = 1)
@Fork(jvmArgsAppend = "--enable-preview", value = 1)
@State(Scope.Thread)
public class TypeKindBench {

    public enum ClassType {
        PRIMITIVE, REFERENCE, MIXED;
    }

    @Param
    ClassType type;
    Class<?>[] classes;
    ClassDesc[] classDescs;

    @Setup
    public void setup() {
        var references = List.of(Character.class, String.class, Integer.class,
                Long.class, Object.class, int[].class, TypeKindBench.class,
                Byte[].class, boolean[][].class);
        var primitives = List.of(int.class, long.class, void.class, double.class,
                float.class, boolean.class, char.class, short.class, byte.class);
        final List<Class<?>> candidates = switch (type) {
            case REFERENCE -> references;
            case PRIMITIVE -> primitives;
            case MIXED -> {
                var list = new ArrayList<Class<?>>(references.size() + primitives.size());
                list.addAll(references);
                list.addAll(primitives);
                yield list;
            }
        };

        // Use fixed seed to ensure results are comparable across
        // different JVMs
        classes = new Random(0xbf5fe40dd887d9e2L)
                .ints(100, 0, candidates.size())
                .mapToObj(candidates::get)
                .toArray(Class<?>[]::new);
        classDescs = Arrays.stream(classes)
                .map(cl -> cl.describeConstable().orElseThrow())
                .toArray(ClassDesc[]::new);
    }

    @Benchmark
    public void fromClasses(Blackhole bh) {
        for (var clz : classes) {
            bh.consume(TypeKind.from(clz));
        }
    }

    @Benchmark
    public void fromClassDescs(Blackhole bh) {
        for (var clz : classDescs) {
            bh.consume(TypeKind.from(clz));
        }
    }
}
