/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.classfile.TypeKind;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 3, time = 1)
@Fork(jvmArgsAppend = "--enable-preview", value = 3)
@State(Scope.Thread)
public class TypeKindFrom {
    @Param({"B", "C", "Z", "S", "I", "F", "J", "D", "V", "java.lang.Object"})
    public String typeName;
    public Class<?> type;
    public ClassDesc classDesc;

    @Setup
    public void setup() throws Exception {
        type = switch (typeName) {
            case "B" -> byte.class;
            case "C" -> char.class;
            case "Z" -> boolean.class;
            case "S" -> short.class;
            case "I" -> int.class;
            case "J" -> long.class;
            case "F" -> float.class;
            case "D" -> void.class;
            case "V" -> void.class;
            default -> Class.forName(typeName);
        };
        classDesc = ClassDesc.ofInternalName(typeName.replace('.', '/'));
    }

    @Benchmark
    public void fromClass(Blackhole bh) {
        bh.consume(TypeKind.from(type));
    }

    @Benchmark
    public void fromClassDesc(Blackhole bh) {
        bh.consume(TypeKind.from(classDesc));
    }
}
