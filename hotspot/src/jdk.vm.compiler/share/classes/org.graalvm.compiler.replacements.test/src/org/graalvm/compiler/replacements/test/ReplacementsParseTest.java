/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import java.util.function.Function;

import org.junit.Test;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests for expected behavior when parsing snippets and intrinsics.
 */
public class ReplacementsParseTest extends GraalCompilerTest {

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins ret = super.getDefaultGraphBuilderPlugins();
        // manually register generated factory, jvmci service providers don't work from unit tests
        new PluginFactory_ReplacementsParseTest().registerPlugins(ret.getInvocationPlugins(), null);
        return ret;
    }

    private static final Object THROW_EXCEPTION_MARKER = new Object() {
        @Override
        public String toString() {
            return "THROW_EXCEPTION_MARKER";
        }
    };

    static class TestMethods {
        static double next(double v) {
            return Math.nextAfter(v, 1.0);
        }

        static double next2(double v) {
            return Math.nextAfter(v, 1.0);
        }

        static double nextAfter(double x, double d) {
            return Math.nextAfter(x, d);
        }

        static String stringize(Object obj) {
            String res = String.valueOf(obj);
            if (res.equals(THROW_EXCEPTION_MARKER.toString())) {
                // Tests exception throwing from partial intrinsification
                throw new RuntimeException("ex: " + obj);
            }
            return res;
        }

        static String identity(String s) {
            return s;
        }
    }

    @ClassSubstitution(TestMethods.class)
    static class TestMethodsSubstitutions {

        @MethodSubstitution(isStatic = true)
        static double nextAfter(double x, double d) {
            double xx = (x == -0.0 ? 0.0 : x);
            return Math.nextAfter(xx, d);
        }

        /**
         * Tests partial intrinsification.
         */
        @MethodSubstitution
        static String stringize(Object obj) {
            if (obj != null && obj.getClass() == String.class) {
                return asNonNullString(obj);
            } else {
                // A recursive call denotes exiting/deoptimizing
                // out of the partial intrinsification to the
                // slow/uncommon case.
                return stringize(obj);
            }
        }

        public static String asNonNullString(Object object) {
            return asNonNullStringIntrinsic(object, String.class, true, true);
        }

        @NodeIntrinsic(PiNode.class)
        private static native String asNonNullStringIntrinsic(Object object, @ConstantNodeParameter Class<?> toType, @ConstantNodeParameter boolean exactType, @ConstantNodeParameter boolean nonNull);

        /**
         * Tests that non-capturing lambdas are folded away.
         */
        @MethodSubstitution
        static String identity(String value) {
            return apply(s -> s, value);
        }

        private static String apply(Function<String, String> f, String value) {
            return f.apply(value);
        }
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        InvocationPlugins invocationPlugins = conf.getPlugins().getInvocationPlugins();
        BytecodeProvider replacementBytecodeProvider = getReplacements().getReplacementBytecodeProvider();
        Registration r = new Registration(invocationPlugins, TestMethods.class, replacementBytecodeProvider);
        r.registerMethodSubstitution(TestMethodsSubstitutions.class, "nextAfter", double.class, double.class);
        r.registerMethodSubstitution(TestMethodsSubstitutions.class, "stringize", Object.class);
        if (replacementBytecodeProvider.supportsInvokedynamic()) {
            r.registerMethodSubstitution(TestMethodsSubstitutions.class, "identity", String.class);
        }
        return super.editGraphBuilderConfiguration(conf);
    }

    /**
     * Ensure that calling the original method from the substitution binds correctly.
     */
    @Test
    public void test1() {
        test("test1Snippet", 1.0);
    }

    public double test1Snippet(double d) {
        return TestMethods.next(d);
    }

    /**
     * Ensure that calling the substitution method binds to the original method properly.
     */
    @Test
    public void test2() {
        test("test2Snippet", 1.0);
    }

    public double test2Snippet(double d) {
        return TestMethods.next2(d);
    }

    /**
     * Ensure that substitution methods with assertions in them don't complain when the exception
     * constructor is deleted.
     */

    @Test
    public void testNextAfter() {
        double[] inArray = new double[1024];
        double[] outArray = new double[1024];
        for (int i = 0; i < inArray.length; i++) {
            inArray[i] = -0.0;
        }
        test("doNextAfter", inArray, outArray);
    }

    public void doNextAfter(double[] outArray, double[] inArray) {
        for (int i = 0; i < inArray.length; i++) {
            double direction = (i & 1) == 0 ? Double.POSITIVE_INFINITY : -Double.NEGATIVE_INFINITY;
            outArray[i] = TestMethods.nextAfter(inArray[i], direction);
        }
    }

    @Test
    public void testCallStringize() {
        test("callStringize", "a string");
        test("callStringize", THROW_EXCEPTION_MARKER);
        test("callStringize", Boolean.TRUE);
    }

    public static Object callStringize(Object obj) {
        return TestMethods.stringize(obj);
    }

    @Test
    public void testRootCompileStringize() {
        ResolvedJavaMethod method = getResolvedJavaMethod(TestMethods.class, "stringize");
        test(method, null, "a string");
        test(method, null, Boolean.TRUE);
        test(method, null, THROW_EXCEPTION_MARKER);
    }

    @Test
    public void testLambda() {
        test("callLambda", (String) null);
        test("callLambda", "a string");
    }

    public static String callLambda(String value) {
        return TestMethods.identity(value);
    }
}
