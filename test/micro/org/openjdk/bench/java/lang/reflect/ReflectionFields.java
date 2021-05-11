/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.lang.reflect;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(value = 1, warmups = 0)
public class ReflectionFields {
    static final Field staticFieldConst;
    static final Field instanceFieldConst;

    static Field staticFieldVar;
    static Field instanceFieldVar;

    static {
        try {
            staticFieldVar = staticFieldConst = ReflectionFields.class.getDeclaredField("staticFoo");
            instanceFieldVar = instanceFieldConst = ReflectionFields.class.getDeclaredField("foo");
        } catch (NoSuchFieldException e) {
            throw new NoSuchMethodError(e.getMessage());
        }
    }

    public static int staticFoo;
    public int foo;
    @Benchmark
    public int getfield_static() {
        return staticFoo;
    }

    @Benchmark
    public int putfield_static() {
        return staticFoo = 10;
    }

    @Benchmark
    public int getfield_instance() {
        return foo;
    }

    @Benchmark
    public int putfield_instance() {
        return foo = 10;
    }

    @Benchmark
    public int getInt_static_field() throws IllegalAccessException {
        return staticFieldConst.getInt(null);
    }

    @Benchmark
    public int getInt_instance_field() throws IllegalAccessException {
        return instanceFieldConst.getInt(this);
    }

    @Benchmark
    public void setInt_static_field() throws IllegalAccessException {
        staticFieldConst.setInt(null, 10);
    }

    @Benchmark
    public void setInt_instance_field() throws IllegalAccessException {
        instanceFieldConst.setInt(this, 20);
    }

    @Benchmark
    public int getInt_static_field_var() throws IllegalAccessException {
        return staticFieldVar.getInt(null);
    }

    @Benchmark
    public int getInt_instance_field_var() throws IllegalAccessException {
        return instanceFieldVar.getInt(this);
    }

    @Benchmark
    public void setInt_static_field_var() throws IllegalAccessException {
        staticFieldVar.setInt(null, 10);
    }

    @Benchmark
    public void setInt_instance_field_var() throws IllegalAccessException {
        instanceFieldVar.setInt(this, 20);
    }
}
