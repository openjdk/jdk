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
import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

/**
 * Compare array clone with equivalent System.arraycopy-based routines
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 6, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class ReferenceClassDescResolve {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    static Class<?> resolve(MethodHandles.Lookup lookup, ClassDesc cd) throws ReflectiveOperationException {
        if (cd.isPrimitive()) {
            return (Class<?>) cd.resolveConstantDesc(lookup);
        }
        return resolveReference(lookup, cd);
    }

    static Class<?> resolveReference(MethodHandles.Lookup lookup, ClassDesc cd) throws ReflectiveOperationException {
        if (cd.isArray()) {
            var desc = cd.descriptorString();
            if (desc.charAt(desc.length() - 1) != ';') {
                // Primitive arrays
                return lookup.findClass(desc);
            }
            int depth = 0;
            while (desc.charAt(depth) == '[') {
                depth++;
            }
            Class<?> clazz = lookup.findClass(desc.substring(depth + 1, desc.length() - 1).replace('/', '.'));
            for (int i = 0; i < depth; i++)
                clazz = clazz.arrayType();
            return clazz;
        }
        var desc = cd.descriptorString();
        return lookup.findClass(desc.substring(1, desc.length() - 1).replace('/', '.'));
    }

    public enum Kind {
        CLASS(String.class),
        CLASS_ARRAY(Integer[][].class),
        PRIMITIVE_ARRAY(long[][][].class);

        final ClassDesc desc;

        Kind(Class<?> clz) {
            this.desc = clz.describeConstable().orElseThrow();
        }
    }

    public enum Implementation {
        OLD {
            @Override
            Class<?> call(MethodHandles.Lookup lookup, ClassDesc cd) throws ReflectiveOperationException {
                ClassDesc c = cd;
                var desc = c.descriptorString();
                int depth = 0;
                while (desc.charAt(depth) == '[') {
                    depth++;
                }
                for (int i = 0; i < depth; i++)
                    c = c.componentType();

                if (c.isPrimitive())
                    return lookup.findClass(desc);
                else {
                    desc = c.descriptorString();
                    Class<?> clazz = lookup.findClass(desc.substring(1, desc.length() - 1).replace('/', '.'));
                    for (int i = 0; i < depth; i++)
                        clazz = clazz.arrayType();
                    return clazz;
                }
            }
        },
        NEW {
            @Override
            Class<?> call(MethodHandles.Lookup lookup, ClassDesc cd) throws ReflectiveOperationException {
                return resolveReference(lookup, cd);
            }
        },
        REFERENCE {
            @Override
            Class<?> call(MethodHandles.Lookup lookup, ClassDesc cd) throws ReflectiveOperationException {
                return (Class<?>) cd.resolveConstantDesc(lookup);
            }
        };

        abstract Class<?> call(MethodHandles.Lookup lookup, ClassDesc cd) throws ReflectiveOperationException;
    }

    @Param
    public Kind kind;
    @Param
    public Implementation implementation;

    @Benchmark
    public Class<?> bench() throws ReflectiveOperationException {
        return implementation.call(LOOKUP, kind.desc);
    }
}
