/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5)
@Measurement(iterations = 10)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class ProxyBench {

    /**
     * On Dell T7610:
     *
     * Benchmark w/ the old ProxyGenerator
     * Benchmark                      Mode  Cnt   Score   Error  Units
     * ProxyBench.getProxyClass1i     avgt   10  20.472 +/- 0.209  ns/op
     * ProxyBench.getProxyClass4i     avgt   10  57.353 +/- 0.461  ns/op
     * ProxyBench.newProxyInstance1i  avgt   10  31.459 +/- 0.516  ns/op
     * ProxyBench.newProxyInstance4i  avgt   10  66.580 +/- 0.983  ns/op
     *
     * Benchmark w/ the new ProxyGenerator using ASM
     * Benchmark                      Mode  Cnt   Score   Error  Units
     * ProxyBench.getProxyClass1i     avgt   10  21.291 +/- 0.475  ns/op
     * ProxyBench.getProxyClass4i     avgt   10  61.481 +/- 4.709  ns/op
     * ProxyBench.newProxyInstance1i  avgt   10  30.177 +/- 0.761  ns/op
     * ProxyBench.newProxyInstance4i  avgt   10  68.302 +/- 1.344  ns/op
     */

    interface PkgPrivate1 {
        void m1();
    }

    interface PkgPrivate2 {
        void m2();
    }

    static final InvocationHandler handler = (proxy, method, args) -> null;

    static final ClassLoader loader1 = null;
    static final Class<?>[] interfaces1 = {Runnable.class};

    static final ClassLoader loader4 = PkgPrivate1.class.getClassLoader();
    static final Class<?>[] interfaces4 = {Runnable.class, Callable.class,
                                           PkgPrivate1.class, PkgPrivate2.class};

    @Benchmark
    public Class<?> getProxyClass1i() {
        return Proxy.getProxyClass(loader1, interfaces1);
    }

    @Benchmark
    public Class<?> getProxyClass4i() {
        return Proxy.getProxyClass(loader4, interfaces4);
    }

    @Benchmark
    public Object newProxyInstance1i() {
        return Proxy.newProxyInstance(loader1, interfaces1, handler);
    }

    @Benchmark
    public Object newProxyInstance4i() {
        return Proxy.newProxyInstance(loader4, interfaces4, handler);
    }
}
