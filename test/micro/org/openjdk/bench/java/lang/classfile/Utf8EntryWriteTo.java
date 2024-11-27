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

import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.*;
import java.lang.constant.*;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

import jdk.internal.classfile.impl.*;
/**
 * Test various operations on
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 3, time = 1)
@Fork(jvmArgs = "--enable-preview", value = 3)
@State(Scope.Thread)
public class Utf8EntryWriteTo {
    static final ClassDesc STRING_BUILDER = ClassDesc.ofDescriptor("Ljava/lang/StringBuilder;");
    static final MethodTypeDesc MTD_append = MethodTypeDesc.of(STRING_BUILDER, CD_String);
    static final MethodTypeDesc MTD_String = MethodTypeDesc.of(CD_String);
    static final ClassDesc CLASS_DESC = ClassDesc.ofDescriptor("Lorg/openjdk/bench/java/lang/classfile/String$$StringConcat;");

    @Param({"ascii", "utf8_2_bytes", "utf8_3_bytes", "emoji"})
    public String charType;
    ConstantPoolBuilder poolBuilder;
    ClassEntry thisClass;

    @Setup
    public void setup() throws Exception {
        byte[] bytes = HexFormat.of().parseHex(
                switch (charType) {
                    case "ascii"        -> "78";
                    case "utf8_2_bytes" -> "c2a9";
                    case "utf8_3_bytes" -> "e6b8a9";
                    case "emoji"        -> "e29da3efb88f";
                    default -> throw new IllegalArgumentException("bad charType: " + charType);
                }
        );
        String s = new String(bytes, 0, bytes.length, StandardCharsets.UTF_8);
        String[] constants = new String[128];
        for (int i = 0; i < constants.length; i++) {
            constants[i] = "A".repeat(i).concat(s);
        }

        poolBuilder = ConstantPoolBuilder.of();
        thisClass = poolBuilder.classEntry(CLASS_DESC);
        for (var c : constants) {
            poolBuilder.utf8Entry(c);
        }
    }

    @Benchmark
    public void writeTo(Blackhole bh) {
        bh.consume(ClassFile
                .of()
                .build(thisClass, poolBuilder, (ClassBuilder clb) -> {}));
    }
}
