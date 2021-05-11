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
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(value = 1, warmups = 0)
public class ReflectionMethods {
    static final Method staticMethodConst;
    static final Method instanceMethodConts;
    static final Method staticMethodConst_3arg;
    static final Method instanceMethodConts_3arg;
    static final Method classForName1argConst;
    static final Method classForName3argConst;
    static final Constructor<?> ctorConst;

    static Method staticMethodVar;
    static Method instanceMethodVar;
    static Method staticMethodVar_3arg;
    static Method instanceMethodVar_3arg;
    static Method classForName1argVar;
    static Method classForName3argVar;
    static Constructor<?> ctorVar;

    static {
        try {
            staticMethodVar = staticMethodConst = ReflectionMethods.class.getDeclaredMethod("sumStatic", int.class, int.class);
            instanceMethodVar = instanceMethodConts = ReflectionMethods.class.getDeclaredMethod("sumInstance", int.class, int.class);
            staticMethodVar_3arg = staticMethodConst_3arg = ReflectionMethods.class.getDeclaredMethod("sumStatic", int.class, int.class, int.class);
            instanceMethodVar_3arg = instanceMethodConts_3arg = ReflectionMethods.class.getDeclaredMethod("sumInstance", int.class, int.class, int.class);
            classForName1argVar = classForName1argConst = Class.class.getMethod("forName", String.class);
            classForName3argVar = classForName3argConst = Class.class.getMethod("forName", String.class, boolean.class, ClassLoader.class);
            ctorVar = ctorConst = ReflectionMethods.Foo.class.getDeclaredConstructor(String.class);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        }
    }

    private int a, b, c;
    static class Foo {
        public Foo(String s) {}
    }

    @Setup(Level.Iteration)
    public void setup() {
        a = ThreadLocalRandom.current().nextInt(1024, Integer.MAX_VALUE);
        b = ThreadLocalRandom.current().nextInt(1024, Integer.MAX_VALUE);
        c = ThreadLocalRandom.current().nextInt(1024, Integer.MAX_VALUE);
    }

    public static int sumStatic(int a, int b) {
        return a + b;
    }

    public int sumInstance(int a, int b) {
        return a + b;
    }

    public static int sumStatic(int a, int b, int c) { return a + b + c; }

    public int sumInstance(int a, int b, int c) {
        return a + b + c;
    }

    @Benchmark
    public int direct_static_method() {
        return sumStatic(a, b);
    }

    @Benchmark
    public int direct_instance_method() {
        return sumInstance(a, b);
    }

    @Benchmark
    public Object static_method() throws InvocationTargetException, IllegalAccessException {
        return staticMethodConst.invoke(null, a, b);
    }

    @Benchmark
    public Object static_method_3arg() throws InvocationTargetException, IllegalAccessException {
        return staticMethodConst_3arg.invoke(null, a, b, c);
    }

    @Benchmark
    public Object instance_method() throws InvocationTargetException, IllegalAccessException {
        return instanceMethodConts.invoke(this, a, b);
    }

    @Benchmark
    public Object instance_method_3arg() throws InvocationTargetException, IllegalAccessException {
        return instanceMethodConts_3arg.invoke(this, a, b, c);
    }

    @Benchmark
    public Class<?> class_forName_1arg() throws InvocationTargetException, IllegalAccessException {
        return (Class<?>) classForName1argVar.invoke(null, "java.lang.System");
    }

    @Benchmark
    public Class<?> class_forName_3arg() throws InvocationTargetException, IllegalAccessException {
        return (Class<?>) classForName3argVar.invoke(null, "java.lang.System", false, null);
    }

    @Benchmark
    public Object static_method_var() throws InvocationTargetException, IllegalAccessException {
        return staticMethodVar.invoke(null, a, b);
    }

    @Benchmark
    public Object instance_method_var() throws InvocationTargetException, IllegalAccessException {
        return instanceMethodVar.invoke(this, a, b);
    }

    @Benchmark
    public Object static_method_var_3arg() throws InvocationTargetException, IllegalAccessException {
        return staticMethodVar_3arg.invoke(null, a, b, c);
    }

    @Benchmark
    public Object instance_method_var_3arg() throws InvocationTargetException, IllegalAccessException {
        return instanceMethodVar_3arg.invoke(this, a, b, c);
    }


    @Benchmark
    public Class<?> class_forName_1arg_var() throws InvocationTargetException, IllegalAccessException {
        return (Class<?>) classForName1argVar.invoke(null, "java.lang.System");
    }

    @Benchmark
    public Class<?> class_forName_3arg_var() throws InvocationTargetException, IllegalAccessException {
        return (Class<?>) classForName3argVar.invoke(null, "java.lang.System", false, null);
    }

    @Benchmark
    public Object ctor_newInstance() throws InvocationTargetException, InstantiationException, IllegalAccessException {
        return ctorConst.newInstance("foo");
    }

    @Benchmark
    public Object ctor_newInstance_var() throws InvocationTargetException, InstantiationException, IllegalAccessException {
        return ctorVar.newInstance("foo");
    }
}
