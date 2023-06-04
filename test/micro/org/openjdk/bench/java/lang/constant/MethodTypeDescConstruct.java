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
package org.openjdk.bench.java.lang.constant;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.constant.ConstantDescs.*;

/**
 * Performance of different MethodTypeDesc factory methods
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 6, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class MethodTypeDescConstruct {
    public enum Kind {
        GENERIC(CD_Object, CD_Object, CD_Object),
        VOID(CD_void),
        NO_PARAM(CD_Class.arrayType()),
        ARBITRARY(CD_int, CD_String, CD_String.arrayType(), CD_double.arrayType());

        final String desc;
        final ClassDesc ret;
        final ClassDesc[] args;
        final List<ClassDesc> argsList;

        Kind(ClassDesc ret, ClassDesc... args) {
            this.desc = MethodTypeDesc.of(ret, args).descriptorString();
            this.ret = ret;
            this.args = args;
            this.argsList = List.of(args);
        }
    }

    @Param
    public Kind kind;

    @Benchmark
    public MethodTypeDesc ofDescriptorBench() {
        return MethodTypeDesc.ofDescriptor(kind.desc);
    }

    @Benchmark
    public MethodTypeDesc ofArrayBench() {
        return MethodTypeDesc.of(kind.ret, kind.args);
    }

    @Benchmark
    public MethodTypeDesc ofListBench() {
        return MethodTypeDesc.of(kind.ret, kind.argsList);
    }
}
