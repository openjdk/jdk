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
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 3, time = 1)
@Fork(jvmArgsAppend = "--enable-preview", value = 3)
@State(Scope.Thread)
public class StringConcat {
    static final ClassDesc STRING_BUILDER = ClassDesc.ofDescriptor("Ljava/lang/StringBuilder;");
    static final MethodTypeDesc MTD_append = MethodTypeDesc.of(STRING_BUILDER, CD_int);
    static final MethodTypeDesc MTD_String = MethodTypeDesc.of(CD_String);
    static final ClassDesc CLASS_DESC = ClassDesc.ofDescriptor("Lorg/openjdk/bench/java/lang/classfile/String$$StringConcat;");

    @Param({"1", "10", "100", "1000"})
    public String invokeCount;
    private int invokeCountValue;

    @Setup
    public void setup() throws Exception {
        invokeCountValue = Integer.parseInt(invokeCount);
    }

    @Benchmark
    public void codegen(Blackhole bh) {
        bh.consume(generate(invokeCountValue));
    }

    private static byte[] generate(int invokeCount) {
        return ClassFile.of().build(
                CLASS_DESC,
                new Consumer<ClassBuilder>() {
                    @Override
                    public void accept(ClassBuilder clb) {
                        clb.withFlags(ACC_FINAL | ACC_SUPER | ACC_SYNTHETIC)
                           .withMethodBody(
                                   "concat",
                                   MTD_String,
                                   ACC_FINAL | ACC_PRIVATE | ACC_STATIC,
                                   new Consumer<CodeBuilder>() {
                                       @Override
                                       public void accept(CodeBuilder cb) {
                                           cb.new_(STRING_BUILDER)
                                             .dup()
                                             .invokespecial(STRING_BUILDER, "<init>", MTD_void);
                                           for (int i = 0; i < invokeCount; i++) {
                                               cb.loadConstant(i)
                                                 .invokevirtual(STRING_BUILDER, "append", MTD_append);
                                           }
                                           cb.invokevirtual(STRING_BUILDER, "toString", MTD_String)
                                             .areturn();
                                       }
                                   }
                           );
                    }
                }
        );
    }
}
