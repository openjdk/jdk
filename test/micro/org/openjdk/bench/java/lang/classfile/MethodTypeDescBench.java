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
import java.lang.constant.MethodTypeDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.concurrent.TimeUnit;
import java.util.List;

/**
 * Performance of MethodTypeDesc#ofDescriptor
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 6, time = 1)
@Fork(jvmArgsAppend = {"--enable-preview"}, value = 1)
@State(Scope.Thread)
public class MethodTypeDescBench {
    String[] descriptors;

    @Setup
    public void setup() {
        var types = new String[] {
                "Ljava/lang/Character;", "Ljava/lang/String;", "Ljava/lang/Integer;",
                "Ljava/lang/Long;", "Ljava/lang/Object;", "[I", "Ljava/util/List;",
                "[Ljava/lang/Byte;", "[[Z", "I", "J",  "D",
                "F", "Z", "C", "S", "B"
        };

        int descriptorsCount = 1000;
        descriptors = new String[descriptorsCount];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < descriptorsCount; i++) {
            int radix = types.length;
            String str = Integer.toString(i, radix);
            int length = str.length();

            sb.setLength(0);
            sb.append('(');
            for (int j = 0; j < length; j++) {
                int index = Integer.parseInt(str.substring(j, j + 1), radix);
                var ptype = types[index];
                sb.append(ptype);
            }
            sb.append(')');

            int rindex = i % (types.length + 1);
            var returnType = rindex == 0 ? "V" : types[rindex - 1];
            sb.append(returnType);
            descriptors[i] = sb.toString();
        }
    }

    @Benchmark
    public void ofDescriptor(Blackhole bh) {
        for (var descriptor : descriptors) {
            bh.consume(MethodTypeDesc.ofDescriptor(descriptor));
        }
    }
}
