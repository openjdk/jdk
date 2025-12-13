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
 *
 */

package org.openjdk.bench.vm.gc;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import org.openjdk.bench.util.InMemoryJavaCompiler;

import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.NMethod;

/*
 * Nmethods have OOPs and GC barriers emmedded into their code.
 * GCs patch them which causes invalidation of nmethods' code.
 *
 * This benchmark can be used to estimate the cost of patching
 * OOPs and GC barriers.
 *
 * We create 5000 nmethods which access fields of a class.
 * We measure the time of different GC cycles to see
 * the impact of patching nmethods.
 *
 * The benchmark parameters are method count and accessed field count.
 */

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 3, jvmArgsAppend = {
    "-XX:+UnlockDiagnosticVMOptions",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+WhiteBoxAPI",
    "-Xbootclasspath/a:lib-test/wb.jar",
    "-XX:-UseCodeCacheFlushing"
})
public class GCPatchingNmethodCost {

    private static final int COMP_LEVEL = 1;
    private static final String FIELD_USER = "FieldUser";

    public static Fields fields;

    private static TestMethod[] methods = {};
    private static byte[] BYTE_CODE;
    private static WhiteBox WB;

    @Param({"5000"})
    public int methodCount;

    @Param({"0", "2", "4", "8"})
    public int accessedFieldCount;

    public static class Fields {
        public String f1;
        public String f2;
        public String f3;
        public String f4;
        public String f5;
        public String f6;
        public String f7;
        public String f8;
        public String f9;
    }

    private static final class TestMethod {
        private final Method method;

        public TestMethod(Method method) throws Exception {
            this.method = method;
            WB.testSetDontInlineMethod(method, true);
        }

        public void profile() throws Exception {
            method.invoke(null);
            WB.markMethodProfiled(method);
        }

        public void invoke() throws Exception {
            method.invoke(null);
        }

        public void compile() throws Exception {
            WB.enqueueMethodForCompilation(method, COMP_LEVEL);
            while (WB.isMethodQueuedForCompilation(method)) {
                Thread.onSpinWait();
            }
            if (WB.getMethodCompilationLevel(method) != COMP_LEVEL) {
                throw new IllegalStateException("Method " + method + " is not compiled at the compilation level: " + COMP_LEVEL + ". Got: " + WB.getMethodCompilationLevel(method));
            }
        }

        public NMethod getNMethod() {
            return NMethod.get(method, false);
        }
    }

    private static ClassLoader createClassLoader() {
        return new ClassLoader() {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (!name.equals(FIELD_USER)) {
                    return super.loadClass(name);
                }

                return defineClass(name, BYTE_CODE, 0, BYTE_CODE.length);
            }
        };
    }

    private static void createTestMethods(int accessedFieldCount, int count) throws Exception {
        String javaCode = "public class " + FIELD_USER + " {";
        String field = GCPatchingNmethodCost.class.getName() + ".fields.f";
        javaCode += "public static void accessFields() {";
        for (int i = 1; i <= accessedFieldCount; i++) {
            javaCode += field + i + "= " + field + i + " + " + i + ";";
        }
        javaCode += "}}";

        BYTE_CODE = InMemoryJavaCompiler.compile(FIELD_USER, javaCode);

        fields = new Fields();

        methods = new TestMethod[count];
        for (int i = 0; i < count; i++) {
            var cl = createClassLoader().loadClass(FIELD_USER);
            Method method = cl.getMethod("accessFields");
            methods[i] = new TestMethod(method);
            methods[i].profile();
            methods[i].compile();
        }
    }

    private static void initWhiteBox() {
        WB = WhiteBox.getWhiteBox();
    }

    @Setup(Level.Trial)
    public void setupCodeCache() throws Exception {
        initWhiteBox();
        createTestMethods(accessedFieldCount, methodCount);
        Thread.sleep(1000);
    }

    @Benchmark
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    public void youngGC() throws Exception {
        fields = null;
        WB.youngGC();
    }

    @Benchmark
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    public void fullGC() throws Exception {
        fields = null;
        WB.fullGC();
    }

    @Benchmark
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    public void systemGC() throws Exception {
        fields = null;
        System.gc();
    }
}
