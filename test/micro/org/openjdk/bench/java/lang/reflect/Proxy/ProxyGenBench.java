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
package org.openjdk.bench.java.lang.reflect.Proxy;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.components.ClassRemapper;
import java.lang.constant.ClassDesc;

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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.SingleShotTime)
@Fork(value = 1, jvmArgsAppend = {"--enable-preview"})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 2)
@State(Scope.Benchmark)
public class ProxyGenBench {
    ClsLoader loader;
    Map<String, Class<?>> clsMap;

    @Setup(Level.Invocation)
    public void setup() throws IOException {
        ClassModel tempModel = ClassFile.of().parse(ProxyGenBench.class.getResourceAsStream("ProxyGenBench$Interfaze.class").readAllBytes());
        ClassDesc tempDesc = ClassDesc.ofDescriptor(Interfaze.class.descriptorString());
        loader = new ClsLoader();
        clsMap = new HashMap<>(100);
        for (int i = 0; i < 100; i++) {
            String intfName = Interfaze.class.getName() + i;
            loader.defClass(intfName, ClassRemapper.of(Map.of(tempDesc, ClassDesc.of(intfName))).remapClass(ClassFile.of(), tempModel));
        }
    }

    @Benchmark
    public void generateProxies(Blackhole bh) {
        for (Class<?> intf : clsMap.values()) {
            bh.consume(Proxy.newProxyInstance(
                    loader,
                    new Class<?>[]{intf},
                    new IHandler()
            ));
        }
    }

    public interface Interfaze {
        default int sum(int a, int b, int c) {
            return a + b + c;
        }
    }

    static class IHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            throw new UnsupportedOperationException();
        }
    }

    public class ClsLoader extends ClassLoader {

        public ClsLoader() {
            super(ProxyGenBench.class.getClassLoader());
        }

        Class<?> defClass(String className, byte[] classData) {
            Class<?> cls = defineClass(className, classData, 0, classData.length);
            clsMap.put(className, cls);
            return cls;
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            return clsMap.get(name);
        }
    }
}
