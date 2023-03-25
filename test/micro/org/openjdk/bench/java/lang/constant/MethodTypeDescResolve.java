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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;

import static java.lang.constant.ConstantDescs.*;

/**
 * Compare array clone with equivalent System.arraycopy-based routines
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 6, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class MethodTypeDescResolve {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public enum Kind {
        GENERIC(CD_Object, CD_Object, CD_Object),
        VOID(CD_void),
        NO_PARAM(CD_Class.arrayType()),
        ARBITRARY(CD_int, CD_String, CD_String.arrayType(), CD_double.arrayType());

        final MethodTypeDesc desc;

        Kind(ClassDesc ret, ClassDesc... args) {
            this.desc = MethodTypeDesc.of(ret, args);
        }
    }

    public enum Implementation {
        OLD {
            @Override
            MethodType call(MethodHandles.Lookup lookup, MethodTypeDesc mtd) throws ReflectiveOperationException {
                return MethodType.fromMethodDescriptorString(mtd.descriptorString(),
                        lookup.lookupClass().getClassLoader());
            }
        },
        NEW {
            @Override
            MethodType call(MethodHandles.Lookup lookup, MethodTypeDesc mtd) throws ReflectiveOperationException {
                Class<?> returnType = ReferenceClassDescResolve.resolve(lookup, mtd.returnType());
                if (mtd.parameterCount() == 0) {
                    return MethodType.methodType(returnType);
                }
                Class<?>[] parameterTypes = new Class<?>[mtd.parameterCount()];
                for (int i = 0; i < parameterTypes.length; i++) {
                    parameterTypes[i] = ReferenceClassDescResolve.resolve(lookup, mtd.parameterType(i));
                }
                return MethodType.methodType(returnType, parameterTypes);
            }
        },
        REFERENCE {
            @Override
            MethodType call(MethodHandles.Lookup lookup, MethodTypeDesc mtd) throws ReflectiveOperationException {
                return (MethodType) mtd.resolveConstantDesc(lookup);
            }
        };

        abstract MethodType call(MethodHandles.Lookup lookup, MethodTypeDesc mtd) throws ReflectiveOperationException;
    }

    @Param
    public Kind kind;
    @Param
    public Implementation implementation;

    @Benchmark
    public MethodType bench() throws ReflectiveOperationException {
        return implementation.call(LOOKUP, kind.desc);
    }
}
