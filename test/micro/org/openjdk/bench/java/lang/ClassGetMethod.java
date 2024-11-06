/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
package org.openjdk.bench.java.lang;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
public class ClassGetMethod {
    public static class ConcreteClass extends SuperClass implements TestIntf {
        @Override
        public void a_noiseMethod(byte[] bytes) {
        }

        public void a_noiseMethod(String s) {
        }

        @Override
        public void b_noiseMethod(byte[] bytes) {
        }

        public void b_noiseMethod(String s) {
        }

        public void c_noiseMethod(String s) {
        }

        public void d_noiseMethod(String s) {
        }

        public void fiveArgs(Object one, Object two, Object three, Object four, Object five) {
        }

        public void fiveArgs(Integer one, Integer two, Integer three, Integer four, Integer five) {
        }

        public void noArgs() {
        }

        public void x_noiseMethod(String s) {
        }

        public void y_noiseMethod(String s) {
        }

        public void z_noiseMethod(String s) {
        }

    }

    public static class SuperClass {
        public void a_superNoiseMethod(String s) {
        }

        public void b_superNoiseMethod(String s) {
        }

        public void c_superNoiseMethod(String s) {
        }

        public void d_superNoiseMethod(String s) {
        }

        public void superFiveArgs(Object one, Object two, Object three, Object four, Object five) {
        }

        public void superFiveArgs(Integer one, Integer two, Integer three, Integer four, Integer five) {
        }

        public void superNoArgs() {
        }

        public void x_superNoiseMethod(String s) {
        }

        public void y_superNoiseMethod(String s) {
        }

        public void z_superNoiseMethod(String s) {
        }

    }

    public interface TestIntf {
        public void a_noiseMethod(byte[] bytes);

        public void b_noiseMethod(byte[] bytes);

        default void defaultIntfFiveArgs(Object a, Object b, Object c, Object d, Object e) {
        }

        default void defaultIntfFiveArgs(Integer a, Integer b, Integer c, Integer d, Integer e) {
        }

        default void defaultIntfNoArgs() {
        }

        default void y_noiseMethod(byte[] bytes) {
        }

        default void z_noiseMethod(byte[] bytes) {
        }
    }

    private static final Class<?>[] FIVE_ARG_CLASSES = new Class<?>[] { Object.class, Object.class, Object.class,
            Object.class, Object.class };

    @Benchmark
    @Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
    public Method getConcreteFiveArg() throws NoSuchMethodException, SecurityException {
        return ConcreteClass.class.getMethod("fiveArgs", FIVE_ARG_CLASSES);
    }

    @Benchmark
    public Method getConcreteNoArg() throws NoSuchMethodException, SecurityException {
        return ConcreteClass.class.getMethod("noArgs");
    }

    @Benchmark
    public Method getIntfFiveArg() throws NoSuchMethodException, SecurityException {
        return ConcreteClass.class.getMethod("defaultIntfFiveArgs", FIVE_ARG_CLASSES);
    }

    @Benchmark
    public Method getIntfNoArg() throws NoSuchMethodException, SecurityException {
        return ConcreteClass.class.getMethod("defaultIntfNoArgs");
    }

    @Benchmark
    @Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
    public Method getNoSuchMethod() throws NoSuchMethodException, SecurityException {
        try {
            return ConcreteClass.class.getMethod("noSuchMethod");
        } catch (NoSuchMethodException nsme) {
            return null;
        }
    }

    @Benchmark
    public Method getSuperFiveArg() throws NoSuchMethodException, SecurityException {
        return ConcreteClass.class.getMethod("superFiveArgs", FIVE_ARG_CLASSES);
    }

    @Benchmark
    public Method getSuperNoArg() throws NoSuchMethodException, SecurityException {
        return ConcreteClass.class.getMethod("superNoArgs");
    }

}
