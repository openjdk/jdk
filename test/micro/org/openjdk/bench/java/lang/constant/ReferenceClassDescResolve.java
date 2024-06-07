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
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

import static java.lang.constant.ConstantDescs.*;

/**
 * Measure the throughput of {@link ClassDesc#resolveConstantDesc} for different
 * reference types.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 6, time = 1)
@Fork(1)
@State(Scope.Thread)
public class ReferenceClassDescResolve {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final ClassDesc CLASS_OR_INTERFACE = CD_String;
    private static final ClassDesc REFERENCE_ARRAY = CD_Integer.arrayType(2);
    private static final ClassDesc PRIMITIVE_ARRAY = CD_int.arrayType(3);

    @Benchmark
    public Class<?> resolveClassOrInterface() throws ReflectiveOperationException {
        return CLASS_OR_INTERFACE.resolveConstantDesc(LOOKUP);
    }

    @Benchmark
    public Class<?> resolveReferenceArray() throws ReflectiveOperationException {
        return REFERENCE_ARRAY.resolveConstantDesc(LOOKUP);
    }

    @Benchmark
    public Class<?> resolvePrimitiveArray() throws ReflectiveOperationException {
        return PRIMITIVE_ARRAY.resolveConstantDesc(LOOKUP);
    }
}
