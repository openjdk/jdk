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
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Performance of conversion from and to method type descriptor symbols with
 * descriptor strings and class descriptor symbols
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 6, time = 1)
@Fork(1)
@State(Scope.Thread)
public class MethodTypeDescFactories {

    private static final ClassDesc DUMMY_CD = ClassDesc.of("Dummy_invalid");

    @Param({
            "(Ljava/lang/Object;Ljava/lang/String;)I",
            "()V",
            "([IJLjava/lang/String;Z)Ljava/util/List;",
            "()[Ljava/lang/String;"
    })
    public String descString;
    public MethodTypeDesc desc;
    public ClassDesc ret;
    public ClassDesc[] args;
    public List<ClassDesc> argsList;

    @Setup
    public void setup() {
        desc = MethodTypeDesc.ofDescriptor(descString);
        ret = desc.returnType();
        args = desc.parameterArray();
        argsList = desc.parameterList();
    }

    @Benchmark
    public String descriptorString(Blackhole blackhole) {
        // swaps return types with dummy classdesc;
        // this shares parameter arrays and avoids revalidation
        // while it drops the descriptor string cache
        var mtd = desc.changeReturnType(DUMMY_CD);
        blackhole.consume(mtd);
        mtd = mtd.changeReturnType(desc.returnType());
        blackhole.consume(mtd);

        return mtd.descriptorString();
    }

    @Benchmark
    public MethodTypeDesc ofDescriptor() {
        return MethodTypeDesc.ofDescriptor(descString);
    }

    @Benchmark
    public MethodTypeDesc ofArray() {
        return MethodTypeDesc.of(ret, args);
    }

    @Benchmark
    public MethodTypeDesc ofList() {
        return MethodTypeDesc.of(ret, argsList);
    }
}
