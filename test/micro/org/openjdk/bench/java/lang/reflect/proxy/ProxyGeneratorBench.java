/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang.reflect.proxy;

import java.util.List;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark measuring java.lang.reflect.ProxyGenerator.generateProxyClass.
 * It bypasses the cache of proxies to measure the time to construct a proxy.
 */
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgs = "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED")
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class ProxyGeneratorBench {

    /**
     * Sample results from a Dell T7610.
     * Benchmark                        Mode  Cnt      Score      Error  Units
     *      ProxyPerf.genIntf_1              avgt   10  35325.428 +/-  780.459  ns/op
     *      ProxyPerf.genStringsIntf_3       avgt   10  46600.366 +/-  663.812  ns/op
     *      ProxyPerf.genZeroParams          avgt   10  33245.048 +/-  437.988  ns/op
     *      ProxyPerf.genPrimsIntf_2         avgt   10  43987.819 +/-  837.443  ns/op
     */

    public interface Intf_1 {
        public Object mL(Object o);
    }

    public interface Intf_2 {
        public int m1I(int i);
        public long m2IJ(int i, long l);
    }

    public interface Intf_3 {
        public void mString(String s1);
        public String m2String(String s1);
        public String m2String(String s1, String s2);
    }

    private ClassLoader classloader;
    private Method proxyGen;
    private Method proxyGenV49;

    @Setup
    public void setup() {
        try {
            classloader = ClassLoader.getSystemClassLoader();
            Class<?> proxyGenClass = Class.forName("java.lang.reflect.ProxyGenerator");
            proxyGen = proxyGenClass.getDeclaredMethod("generateProxyClass",
                    ClassLoader.class, String.class, java.util.List.class, int.class);
            proxyGen.setAccessible(true);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException("ProxyClass setup fails", ex);
        }
    }

    @Benchmark
    public Object genZeroParams() throws Exception {
        List<Class<?>> interfaces = List.of(Runnable.class);
        return proxyGen.invoke(null, classloader, "ProxyImpl", interfaces, 1);
    }

    @Benchmark
    public Object genIntf_1() throws Exception {
        List<Class<?>> interfaces = List.of(Intf_1.class);
        return proxyGen.invoke(null, classloader, "ProxyImpl", interfaces, 1);
    }

    @Benchmark
    public Object genPrimsIntf_2() throws Exception {
        List<Class<?>> interfaces = List.of(Intf_2.class);
        return proxyGen.invoke(null, classloader, "ProxyImpl", interfaces, 1);
    }

    @Benchmark
    public Object genStringsIntf_3() throws Exception {
        List<Class<?>> interfaces = List.of(Intf_3.class);
        return proxyGen.invoke(null, classloader, "ProxyImpl", interfaces, 1);
    }

    public static void main(String... args) throws Exception {
        var benchmark = new ProxyGeneratorBench();
        benchmark.setup();
        benchmark.genZeroParams();
        benchmark.genIntf_1();
        benchmark.genPrimsIntf_2();
        benchmark.genStringsIntf_3();
    }
}
