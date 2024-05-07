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
package org.openjdk.bench.vm.compiler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
import org.openjdk.jmh.infra.Blackhole;

import org.openjdk.bench.util.InMemoryJavaCompiler;

@State(Scope.Benchmark)
@Warmup(iterations = 20, time = 2)
@Measurement(iterations = 15, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
@Fork(value = 2)
public class CodeCacheStress {

    // The number of distinct classes generated from the source string below
    // All the classes are "warmed up" by invoking their methods to get compiled by the jit
    @Param({"100"})
    public int numberOfClasses;

    // The range of these classes to use in the measured phase after the warm up
    @Param({"100"})
    public int rangeOfClasses;

    // How deep is the recursion when calling into the generated classes
    @Param({"20"})
    public int recurse;

    // How many instances of each generated class to create and call in the measurement phase
    @Param({"100"})
    public int instanceCount;

    byte[][] compiledClasses;
    Class[] loadedClasses;
    String[] classNames;

    int index = 0;
    Map<Object, Method[]> classToMethodsMap = new HashMap<>();
    ArrayList<Map<String, Integer>> argumentMaps = new ArrayList<>();
    Map<Class, Object[]> instancesOfClassMap = new HashMap<>();

    static final String k = "key";
    static final Integer v = 1000;

    static final String methodNames[] = {
            "get"
    };

    static String B(int count) {
        return "import java.util.*; "
                + " "
                + "public class B" + count + " {"
                + " "
                + "   static int staticA = 0;"
                + "   static int staticB = 0;"
                + "   static int staticC = 0;"
                + "   static int staticD = 0;"
                + " "
                + " static synchronized void setA(int a) {"
                + "   staticB = a;"
                + " }"
                + " "
                + " static synchronized void setB(int b) {"
                + "   staticB = b;"
                + " }"
                + " "
                + " static synchronized void setC(int c) {"
                + "   staticC = c;"
                + " }"
                + " "
                + " static synchronized void setD(int d) {"
                + "   staticD = d;"
                + " }"
                + " "
                + "    int instA = 0;"
                + " "
                + "    int padAA = 0;"
                + "    int padAB = 0;"
                + "    int padAC = 0;"
                + "    int padAD = 0;"
                + "    int padAE = 0;"
                + "    int padAF = 0;"
                + "    int padAG = 0;"
                + "    int padAH = 0;"
                + "    int padAI = 0;"
                + "    int padAJ = 0;"
                + "    int padAK = 0;"
                + "    int padAL = 0;"
                + "    int padAM = 0;"
                + "    int padAN = 0;"
                + "    int padAO = 0;"
                + "    int padAP = 0;"
                + "    int padAQ = 0;"
                + "    int padAR = 0;"
                + "    int padAS = 0;"
                + "    int padAT = 0;"
                + " "
                + "    int instB = 0;"
                + " "
                + "    int padBA = 0;"
                + "    int padBB = 0;"
                + "    int padBC = 0;"
                + "    int padBD = 0;"
                + "    int padBE = 0;"
                + "    int padBF = 0;"
                + "    int padBG = 0;"
                + "    int padBH = 0;"
                + "    int padBI = 0;"
                + "    int padBJ = 0;"
                + "    int padBK = 0;"
                + "    int padBL = 0;"
                + "    int padBM = 0;"
                + "    int padBN = 0;"
                + "    int padBO = 0;"
                + "    int padBP = 0;"
                + "    int padBQ = 0;"
                + "    int padBR = 0;"
                + "    int padBS = 0;"
                + "    int padBT = 0;"
                + " "
                + "    int instC = 0;"
                + " "
                + "    int padCA = 0;"
                + "    int padCB = 0;"
                + "    int padCC = 0;"
                + "    int padCD = 0;"
                + "    int padCE = 0;"
                + "    int padCF = 0;"
                + "    int padCG = 0;"
                + "    int padCH = 0;"
                + "    int padCI = 0;"
                + "    int padCJ = 0;"
                + "    int padCK = 0;"
                + "    int padCL = 0;"
                + "    int padCM = 0;"
                + "    int padCN = 0;"
                + "    int padCO = 0;"
                + "    int padCP = 0;"
                + "    int padCQ = 0;"
                + "    int padCR = 0;"
                + "    int padCS = 0;"
                + "    int padCT = 0;"
                + " "
                + "    int instD = 0;"
                + " "
                + " "
                + "   public Integer get(Map m, String k, Integer depth) {"
                + "       if (depth > 0) {"
                + "         instA += ((depth % 2) + staticA);"
                + "         return (Integer) m.get(k) + get2(m, k, --depth);"
                + "       } else {"
                + "         setA(depth);"
                + "         return (Integer) m.get(k)+ 10;"
                + "       }"
                + "   }"
                + " "
                + "   public Integer get2(Map m, String k, Integer depth) {"
                + "       if (depth > 0) {"
                + "         instB += ((depth % 2) + staticB);"
                + "         return (Integer) m.get(k) + get3(m, k, --depth);"
                + "       } else {"
                + "         setB(depth);"
                + "         return (Integer) m.get(k)+ 20;"
                + "       }"
                + "   }"
                + " "
                + "   public Integer get3(Map m, String k, Integer depth) {"
                + "       if (depth > 0) {"
                + "         instC += ((depth % 2) + staticC);"
                + "         return (Integer) m.get(k) + get4(m, k, --depth);"
                + "       } else {"
                + "         setC(depth);"
                + "         return (Integer) m.get(k)+ 30;"
                + "       }"
                + "   }"
                + " "
                + " "
                + "   public Integer get4(Map m, String k, Integer depth) {"
                + "       if (depth > 0) {"
                + "         instD += ((depth % 2) + staticD);"
                + "         return (Integer) m.get(k) + get5(m, k, --depth);"
                + "       } else {"
                + "         setD(depth);"
                + "         return (Integer) m.get(k)+ 40;"
                + "       }"
                + "   }"
                + " "
                + " "
                + "   public Integer get5(Map m, String k, Integer depth) {"
                + "       if (depth > 0) {"
                + "         return (Integer) m.get(k) + get6(m, k, --depth);"
                + "       } else {"
                + "         return (Integer) m.get(k)+ instA;"
                + "       }"
                + "   }"
                + " "
                + "   public Integer get6(Map m, String k, Integer depth) {"
                + "       if (depth > 0) {"
                + "         return (Integer) m.get(k) + get7(m, k, --depth);"
                + "       } else {"
                + "         return (Integer) m.get(k)+ instB;"
                + "       }"
                + "   }"
                + " "
                + "   public Integer get7(Map m, String k, Integer depth) {"
                + "       if (depth > 0) {"
                + "         return (Integer) m.get(k) + get8(m, k, --depth);"
                + "       } else {"
                + "         return (Integer) m.get(k)+ instC;"
                + "       }"
                + "   }"
                + " "
                + "   public Integer get8(Map m, String k, Integer depth) {"
                + "       if (depth > 0) {"
                + "         return (Integer) m.get(k) + get9(m, k, --depth);"
                + "       } else {"
                + "         return (Integer) m.get(k)+ instD;"
                + "       }"
                + "   }"
                + " "
                + "   public Integer get9(Map m, String k, Integer depth) {"
                + "       if (depth > 0) {"
                + "         return (Integer) m.get(k) + get10(m, k, --depth);"
                + "       } else {"
                + "         return (Integer) m.get(k)+ instA;"
                + "       }"
                + "   }"
                + " "
                + "   public Integer get10(Map m, String k, Integer depth) {"
                + "       if (depth > 0) {"
                + "         return (Integer) m.get(k) + get11(m, k, --depth);"
                + "       } else {"
                + "         return (Integer) m.get(k)+ instB;"
                + "       }"
                + "   }"
                + " "
                + "   public Integer get11(Map m, String k, Integer depth) {"
                + "       if (depth > 0) {"
                + "         return (Integer) m.get(k) + get12(m, k, --depth);"
                + "       } else {"
                + "         return (Integer) m.get(k)+ instC;"
                + "       }"
                + "   }"
                + " "
                + "   public Integer get12(Map m, String k, Integer depth) {"
                + "       if (depth > 0) {"
                + "         return (Integer) m.get(k) + get(m, k, --depth);"
                + "       } else {"
                + "         return (Integer) m.get(k)+ instD;"
                + "       }"
                + "   }"
                + "}";
    }


    class BenchLoader extends ClassLoader {

        BenchLoader() {
            super();
        }

        BenchLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(classNames[index])) {
                assert compiledClasses[index] != null;
                return defineClass(name, compiledClasses[index],
                        0,
                        (compiledClasses[index]).length);
            } else {
                return super.findClass(name);
            }
        }
    }

    CodeCacheStress.BenchLoader loader1 = new CodeCacheStress.BenchLoader();

    @Setup(Level.Trial)
    public void setupClasses() throws Exception {
        Object[] receivers1;

        compiledClasses = new byte[numberOfClasses][];
        loadedClasses = new Class[numberOfClasses];
        classNames = new String[numberOfClasses];

        argumentMaps.add(new HashMap<String, Integer>());
        argumentMaps.add(new LinkedHashMap<String, Integer>());
        argumentMaps.add(new WeakHashMap<String, Integer>());

        argumentMaps.get(0).put(k, v);
        argumentMaps.get(1).put(k, v);
        argumentMaps.get(2).put(k, v);

        for (int i = 0; i < numberOfClasses; i++) {
            classNames[i] = "B" + i;
            compiledClasses[i] = InMemoryJavaCompiler.compile(classNames[i], B(i));
        }

        for (index = 0; index < compiledClasses.length; index++) {
            Class<?> c = loader1.findClass(classNames[index]);
            loadedClasses[index] = c;

            Constructor<?>[] ca = c.getConstructors();
            assert ca.length == 1;

            receivers1 = new Object[instanceCount];
            for (int j = 0; j < instanceCount; j++) {
                receivers1[j] = ca[0].newInstance();
            }
            instancesOfClassMap.put(c, receivers1);

            Method[] methods = new Method[methodNames.length];
            IntStream.range(0, methodNames.length).forEach(m -> {
                try {
                    methods[m] = c.getMethod(methodNames[m], java.util.Map.class, String.class, Integer.class);
                } catch (Exception e) {
                    System.out.println("Exception = " + e);
                    e.printStackTrace();
                    System.exit(-1);
                }
            });

            classToMethodsMap.put((receivers1[0]).getClass(), methods);

            // Warmup the methods to get compiled
            IntStream.range(0, methodNames.length).parallel().forEach(m -> {
                IntStream.range(0, 12000).forEach(x -> {
                    try {
                        Object r = ((Object[]) instancesOfClassMap.get(c))[0];
                        Method[] mi = classToMethodsMap.get(r.getClass());
                        mi[m].invoke(r, argumentMaps.get(0), k, 5);
                    } catch (Exception e) {
                        System.out.println("Exception = " + e);
                        e.printStackTrace();
                        System.exit(-1);
                    }
                });

            });
        }

        System.gc();
    }

    Class chooseClass() {
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        int whichClass = tlr.nextInt(rangeOfClasses);
        return loadedClasses[whichClass];
    }

    Object chooseInstance(Class c) {
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        int whichInst = tlr.nextInt(instanceCount);
        return ((Object[]) instancesOfClassMap.get(c))[whichInst];
    }

    Method chooseMethod(Class c) {
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        int whichM = tlr.nextInt(methodNames.length);
        return classToMethodsMap.get(c)[whichM];
    }

    Map chooseMap() {
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        int whichMap = tlr.nextInt(argumentMaps.size());
        return argumentMaps.get(whichMap);
    }

    Integer callTheMethod(Method m, Object r, String k, Map map) throws Exception {
        return (Integer) m.invoke(r, map, k, recurse);
    }

    @Benchmark
    public Integer work() throws Exception {
        int sum = 0;

        // Call a method of a random instance of a random class up to the specified range
        for (int index = 0; index < compiledClasses.length; index++) {
            try {
                Class c = chooseClass();
                Object r = chooseInstance(c);
                Method m = chooseMethod(c);
                assert m != null;
                Map map = chooseMap();
                Integer result = callTheMethod(m, r, k, map);
                assert result != null && result >= v;
                sum += result;
            } catch (Exception e) {
                System.out.println("Exception = " + e);
                e.printStackTrace();
            }
        }
        return sum;
    }

}
