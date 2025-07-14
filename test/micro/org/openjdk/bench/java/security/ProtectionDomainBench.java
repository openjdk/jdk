/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.security;

import java.security.*;
import java.net.*;
import java.io.*;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import org.openjdk.bench.util.InMemoryJavaCompiler;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.Throughput)
public class ProtectionDomainBench {
    @Param({"100"})
    static int numberOfClasses;

    @Param({"10"})
    static int numberOfCodeSources;

    @State(Scope.Thread)
    public static class MyState {

        byte[][] compiledClasses;
        Class[] loadedClasses;
        String[] classNames;
        int index = 0;
        CodeSource[] cs;

        String B(int count, long n) {
            return "public class B" + count + n +" {"
                    + "   static int intField;"
                    + "   public static void compiledMethod() { "
                    + "       intField++;"
                    + "   }"
                    + "}";
        }

        @Setup
        public void setupClasses() throws Exception {
            compiledClasses = new byte[numberOfClasses][];
            loadedClasses = new Class[numberOfClasses];
            classNames = new String[numberOfClasses];
            cs = new CodeSource[numberOfCodeSources];
            long n = Thread.currentThread().threadId();
//System.out.println("XXX " + n);

            for (int i = 0; i < numberOfCodeSources; i++) {
                @SuppressWarnings("deprecation")
                URL u = new URL("file:/tmp/duke" + i);
                cs[i] = new CodeSource(u, (java.security.cert.Certificate[]) null);
            }

            for (int i = 0; i < numberOfClasses; i++) {
                classNames[i] = "B" + i + n;
//System.out.println("YYY " + classNames[i] +" ");
                compiledClasses[i] = InMemoryJavaCompiler.compile(classNames[i], B(i, n));
            }
        }
    }

    public class ProtectionDomainBenchLoader extends SecureClassLoader {

        ProtectionDomainBenchLoader() {
            super();
        }

        ProtectionDomainBenchLoader(ClassLoader parent) {
            super(parent);
        }

        protected Class<?> findClass(MyState state, String name) throws ClassNotFoundException {
            if (name.equals(state.classNames[state.index] /* "B" + index */)) {
                assert state.compiledClasses[state.index]  != null;
                return defineClass(name, state.compiledClasses[state.index] , 0, (state.compiledClasses[state.index]).length, state.cs[state.index % state.cs.length] );
            } else {
                return super.findClass(name);
            }
        }
    }

    void work(MyState state) throws ClassNotFoundException {
        ProtectionDomainBench.ProtectionDomainBenchLoader loader1 = new
                ProtectionDomainBench.ProtectionDomainBenchLoader();

        for (state.index = 0; state.index < state.compiledClasses.length; state.index++) {
            Class c = loader1.findClass(state, state.classNames[state.index]);
            state.loadedClasses[state.index] = c;
        }
    }

    @Benchmark
    @Fork(value = 3)
    public void noSecurityManager(MyState state)  throws ClassNotFoundException {
        work(state);
    }
}
