/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt;

import static java.lang.reflect.Modifier.isStatic;

import java.util.Collections;
import java.util.Set;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.Builder;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Assert;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Base class for the JTT tests.
 * <p>
 * These tests are executed twice: once with arguments passed to the execution and once with the
 * arguments bound to the test's parameters during compilation. The latter is a good test of
 * canonicalization.
 */
public class JTTTest extends GraalCompilerTest {

    public static final class DummyTestClass {
    }

    protected static final Set<DeoptimizationReason> EMPTY = Collections.<DeoptimizationReason> emptySet();
    /**
     * The arguments which, if non-null, will replace the Locals in the test method's graph.
     */
    Object[] argsToBind;

    public JTTTest() {
        Assert.assertNotNull(getCodeCache());
    }

    @Override
    protected StructuredGraph parse(Builder builder, PhaseSuite<HighTierContext> graphBuilderSuite) {
        StructuredGraph graph = super.parse(builder, graphBuilderSuite);
        if (argsToBind != null) {
            ResolvedJavaMethod m = graph.method();
            Object receiver = isStatic(m.getModifiers()) ? null : this;
            Object[] args = argsWithReceiver(receiver, argsToBind);
            JavaType[] parameterTypes = m.toParameterTypes();
            assert parameterTypes.length == args.length;
            for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                JavaConstant c = getSnippetReflection().forBoxed(parameterTypes[param.index()].getJavaKind(), args[param.index()]);
                ConstantNode replacement = ConstantNode.forConstant(c, getMetaAccess(), graph);
                param.replaceAtUsages(replacement);
            }
        }
        return graph;
    }

    @Override
    protected InstalledCode getCode(ResolvedJavaMethod method, StructuredGraph graph, boolean forceCompile, boolean installAsDefault, OptionValues options) {
        return super.getCode(method, graph, argsToBind != null, installAsDefault, options);
    }

    Double delta;

    @Override
    protected void assertDeepEquals(Object expected, Object actual) {
        if (delta != null) {
            Assert.assertEquals(((Number) expected).doubleValue(), ((Number) actual).doubleValue(), delta);
        } else {
            super.assertDeepEquals(expected, actual);
        }
    }

    @SuppressWarnings("hiding")
    protected void runTestWithDelta(double delta, String name, Object... args) {
        this.delta = Double.valueOf(delta);
        runTest(name, args);
    }

    protected void runTest(String name, Object... args) {
        runTest(getInitialOptions(), name, args);
    }

    protected void runTest(OptionValues options, String name, Object... args) {
        runTest(options, EMPTY, true, false, name, args);
    }

    protected void runTest(Set<DeoptimizationReason> shouldNotDeopt, String name, Object... args) {
        runTest(getInitialOptions(), shouldNotDeopt, true, false, name, args);
    }

    protected void runTest(OptionValues options, Set<DeoptimizationReason> shouldNotDeopt, boolean bind, boolean noProfile, String name, Object... args) {
        ResolvedJavaMethod method = getResolvedJavaMethod(name);
        Object receiver = method.isStatic() ? null : this;

        Result expect = executeExpected(method, receiver, args);

        if (noProfile) {
            method.reprofile();
        }

        testAgainstExpected(options, method, expect, shouldNotDeopt, receiver, args);
        if (args.length > 0 && bind) {
            if (noProfile) {
                method.reprofile();
            }

            this.argsToBind = args;
            testAgainstExpected(options, method, expect, shouldNotDeopt, receiver, args);
            this.argsToBind = null;
        }
    }
}
