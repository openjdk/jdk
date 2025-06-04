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

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.Throughput)
public class ProtectionDomainBench {

    @Param({"100"})
    public int numberOfClasses;

    @Param({"10"})
    public int numberOfCodeSources;

    static byte[][] compiledClasses;
    static Class[] loadedClasses;
    static String[] classNames;
    static int index = 0;
    static CodeSource[] cs;

    static String B(int count) {
        return "public class B" + count + " {"
                + "   static int intField;"
                + "   public static void compiledMethod() { "
                + "       intField++;"
                + "   }"
                + "}";
    }

    @Setup(Level.Trial)
    public void setupClasses() throws Exception {
        compiledClasses = new byte[numberOfClasses][];
        loadedClasses = new Class[numberOfClasses];
        classNames = new String[numberOfClasses];
        cs = new CodeSource[numberOfCodeSources];

        for (int i = 0; i < numberOfCodeSources; i++) {
            @SuppressWarnings("deprecation")
            URL u = new URL("file:/tmp/duke" + i);
            cs[i] = new CodeSource(u, (java.security.cert.Certificate[]) null);
        }

        for (int i = 0; i < numberOfClasses; i++) {
            classNames[i] = "B" + i;
            compiledClasses[i] = InMemoryJavaCompiler.compile(classNames[i], B(i));
        }

    }

    static class ProtectionDomainBenchLoader extends SecureClassLoader {

        ProtectionDomainBenchLoader() {
            super();
        }

        ProtectionDomainBenchLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(classNames[index] /* "B" + index */)) {
                assert compiledClasses[index]  != null;
                return defineClass(name, compiledClasses[index] , 0, (compiledClasses[index]).length, cs[index % cs.length] );
            } else {
                return super.findClass(name);
            }
        }
    }

    void work() throws ClassNotFoundException {
        ProtectionDomainBench.ProtectionDomainBenchLoader loader1 = new
                ProtectionDomainBench.ProtectionDomainBenchLoader();

        for (index = 0; index < compiledClasses.length; index++) {
            Class c = loader1.findClass(classNames[index]);
            loadedClasses[index] = c;
        }
    }

    @Benchmark
    @Fork(value = 3)
    public void noSecurityManager()  throws ClassNotFoundException {
        work();
    }
}
