/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Collection;

import static java.lang.reflect.Modifier.isStatic;

@RunWith(Parameterized.class)
public class SystemArrayCopyTest extends GraalCompilerTest {

    @Parameter(0) public Object src;
    @Parameter(1) public Object dst;
    @Parameter(2) public int len;
    @Parameter(3) public String name;

    @Parameters(name = "{3}")
    public static Collection<Object[]> data() {
        Object[] srcs = {new int[4], new double[4], new Integer[4], new Number[4], new String[4], new Object[]{"Graal", 0, 0, 0}, new Object()};
        Object[] dsts = {new int[4], new Number[4]};
        int[] lens = {-1, 0, 2, 8};

        ArrayList<Object[]> ret = new ArrayList<>(srcs.length * dsts.length * lens.length);
        for (Object src : srcs) {
            for (Object dst : dsts) {
                for (int length : lens) {
                    ret.add(new Object[]{src, dst, length, src.getClass().getSimpleName() + ", 0, " + dst.getClass().getSimpleName() + ", 0, " + length});
                }
            }
        }
        return ret;
    }

    public static void testArrayCopySnippet(Object src, Object dst, int length) {
        System.arraycopy(src, 0, dst, 0, length);
    }

    private static final int PARAMETER_LENGTH = 3;
    private Object[] argsToBind;

    @Test
    public void testArrayCopy() {
        ResolvedJavaMethod method = getResolvedJavaMethod("testArrayCopySnippet");
        Object receiver = method.isStatic() ? null : this;
        Object[] args = {src, dst, len};

        Result expect = executeExpected(method, receiver, args);
        testAgainstExpected(method, expect, receiver, args);

        // test composition of constant binding
        for (int i = 1; i < (1 << PARAMETER_LENGTH); i++) {
            argsToBind = new Object[PARAMETER_LENGTH];
            for (int j = 0; j < PARAMETER_LENGTH; j++) {
                if ((i & (1 << j)) != 0) {
                    argsToBind[j] = args[j];
                }
            }
            testAgainstExpected(method, expect, receiver, args);
        }
    }

    @Override
    protected StructuredGraph parse(StructuredGraph.Builder builder, PhaseSuite<HighTierContext> graphBuilderSuite) {
        StructuredGraph graph = super.parse(builder, graphBuilderSuite);
        if (argsToBind != null) {
            ResolvedJavaMethod m = graph.method();
            Object receiver = isStatic(m.getModifiers()) ? null : this;
            Object[] args = argsWithReceiver(receiver, argsToBind);
            JavaType[] parameterTypes = m.toParameterTypes();
            assert parameterTypes.length == args.length;
            for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                int index = param.index();
                if (args[index] != null) {
                    JavaConstant c = getSnippetReflection().forBoxed(parameterTypes[index].getJavaKind(), args[index]);
                    ConstantNode replacement = ConstantNode.forConstant(c, getMetaAccess(), graph);
                    param.replaceAtUsages(replacement);
                }
            }
        }
        return graph;
    }

    @Override
    protected InstalledCode getCode(ResolvedJavaMethod method, StructuredGraph graph, boolean forceCompile, boolean installAsDefault, OptionValues options) {
        return super.getCode(method, graph, true, installAsDefault, options);
    }

}
