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
package org.openjdk.bench.vm.runtime;

import java.lang.invoke.*;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import org.openjdk.bench.util.InMemoryJavaCompiler;

@State(Scope.Benchmark)
@Warmup(iterations = 18, time = 5)
@Measurement(iterations = 10, time = 5)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Threads(1)
@Fork(value = 2)
public class MethodHandleStress {

    // The number of distinct classes generated from the source string below
    // All the classes are "warmed up" by invoking their methods to get compiled by the jit
    @Param({"1000"})
    public int classes;

    // How many instances of each generated class to create and use in the measurement phase
    @Param({"100"})
    public int instances;

    @Benchmark
    public Integer executeOne() throws Throwable {
        Class c = chooseClass();
        Object r = chooseInstance(c);
        MethodHandle m = prebindMethods.get(c).get(r);
        assert m != null;
        return callTheMethod(m, r);
    }

    private Map<Class, Object[]> instancesOfClassMap = new HashMap<>();
    private Map<Class, Map<Object, MethodHandle>> prebindMethods = new ConcurrentHashMap<>();

    private Class[] loadedClasses;

    private class BenchLoader extends ClassLoader {

        private static String classString(String name) {
            return "public class " + name + " {"
                    + "    int instA = 0;"
                    + "    int getA() {"
                    + "        return instA;"
                    + "    }"
                    + "    public Integer get(Integer depth) throws Throwable {"
                    + "        return getA();"
                    + "    }"
                    + "}";
        }

        private Class<?> generateClass(String name) {
            byte[] classBytes = InMemoryJavaCompiler.compile(name, classString(name));
            return defineClass(name, classBytes, 0, classBytes.length);
        }
    }

    @Setup(Level.Trial)
    public void setupClasses() throws Exception {
        MethodHandleStress.BenchLoader loader = new MethodHandleStress.BenchLoader();

        Object[] receivers1;

        loadedClasses = new Class[classes];

        MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
        MethodType generatedGetType = MethodType.methodType(Integer.class, Integer.class);

        for (int i = 0; i < classes; i++) {
            Class<?> c = loader.generateClass("B" + i);
            loadedClasses[i] = c;

            Constructor<?>[] ca = c.getConstructors();
            assert ca.length == 1;

            // Build the list of prebind MHs
            ConcurrentHashMap<Object, MethodHandle> prebinds = new ConcurrentHashMap<>();

            receivers1 = new Object[instances];
            for (int j = 0; j < instances; j++) {
                Object inst= ca[0].newInstance();
                receivers1[j] = inst;
                MethodHandle mh = publicLookup.findVirtual(c, "get", generatedGetType);
                mh = mh.bindTo(inst);
                prebinds.put(inst, mh);
            }
            instancesOfClassMap.put(c, receivers1);
            prebindMethods.put(c, prebinds);
        }

        // Warm up the methods
        for (int n = 0; n < classes; n++) {
            try {
                IntStream.range(0, 5000).parallel().forEach(x -> {
                    try {
                        executeOne();
                    } catch (Throwable e) {
                    }
                });
            } catch (Throwable e) {
                System.out.println("Exception = " + e);
                e.printStackTrace();
                System.exit(-1);
            }
        }

        System.gc();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    Class chooseClass() {
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        int whichClass = tlr.nextInt(classes);
        return loadedClasses[whichClass];
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    Object chooseInstance(Class c) {
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        int whichInst = tlr.nextInt(instances);
        return ((Object[]) instancesOfClassMap.get(c))[whichInst];
    }

    static final Integer recurse = 1;

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int callTheMethod(MethodHandle m, Object r) throws Throwable {
        return (Integer) m.invokeExact(recurse);
    }
}
