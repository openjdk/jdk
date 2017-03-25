/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import static org.graalvm.compiler.core.GraalCompiler.compileGraph;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.nodes.ConstantNode.getConstantNodes;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.OptionValue.OverrideScope;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.tiers.SuitesProvider;
import org.graalvm.compiler.runtime.RuntimeProvider;

import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * use
 *
 * <pre>
 * mx unittest AheadOfTimeCompilationTest @-XX:CompileCommand='print,*AheadOfTimeCompilationTest.*'
 * </pre>
 *
 * to print disassembly.
 */
public class AheadOfTimeCompilationTest extends GraalCompilerTest {

    public static final Object STATICFINALOBJECT = new Object();
    public static final String STATICFINALSTRING = "test string";

    public static Object getStaticFinalObject() {
        return AheadOfTimeCompilationTest.STATICFINALOBJECT;
    }

    @Test
    public void testStaticFinalObjectAOT() {
        StructuredGraph result = compile("getStaticFinalObject", true);
        assertDeepEquals(1, getConstantNodes(result).count());
        Stamp constantStamp = getConstantNodes(result).first().stamp();
        Assert.assertTrue(constantStamp.toString(), constantStamp instanceof KlassPointerStamp);
        assertDeepEquals(2, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    @Test
    public void testStaticFinalObject() {
        StructuredGraph result = compile("getStaticFinalObject", false);
        assertDeepEquals(1, getConstantNodes(result).count());
        assertDeepEquals(JavaKind.Object, getConstantNodes(result).first().getStackKind());
        assertDeepEquals(0, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    public static Class<AheadOfTimeCompilationTest> getClassObject() {
        return AheadOfTimeCompilationTest.class;
    }

    @Test
    public void testClassObjectAOT() {
        StructuredGraph result = compile("getClassObject", true);

        NodeIterable<ConstantNode> filter = getConstantNodes(result);
        assertDeepEquals(1, filter.count());
        HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) getMetaAccess().lookupJavaType(AheadOfTimeCompilationTest.class);
        assertDeepEquals(type.klass(), filter.first().asConstant());

        assertDeepEquals(1, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    @Test
    public void testClassObject() {
        StructuredGraph result = compile("getClassObject", false);

        NodeIterable<ConstantNode> filter = getConstantNodes(result);
        assertDeepEquals(1, filter.count());
        JavaConstant c = filter.first().asJavaConstant();
        Assert.assertEquals(getSnippetReflection().asObject(Class.class, c), AheadOfTimeCompilationTest.class);

        assertDeepEquals(0, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    public static Class<Integer> getPrimitiveClassObject() {
        return int.class;
    }

    @Test
    public void testPrimitiveClassObjectAOT() {
        StructuredGraph result = compile("getPrimitiveClassObject", true);
        NodeIterable<ConstantNode> filter = getConstantNodes(result);
        assertDeepEquals(1, filter.count());
        Stamp constantStamp = filter.first().stamp();
        Assert.assertTrue(constantStamp instanceof KlassPointerStamp);

        assertDeepEquals(2, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    @Test
    public void testPrimitiveClassObject() {
        StructuredGraph result = compile("getPrimitiveClassObject", false);
        NodeIterable<ConstantNode> filter = getConstantNodes(result);
        assertDeepEquals(1, filter.count());
        JavaConstant c = filter.first().asJavaConstant();
        Assert.assertEquals(getSnippetReflection().asObject(Class.class, c), Integer.TYPE);

        assertDeepEquals(0, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    public static String getStringObject() {
        return AheadOfTimeCompilationTest.STATICFINALSTRING;
    }

    @Test
    public void testStringObjectAOT() {
        // embedded strings are fine
        testStringObjectCommon(true);
    }

    @Test
    public void testStringObject() {
        testStringObjectCommon(false);
    }

    private void testStringObjectCommon(boolean compileAOT) {
        StructuredGraph result = compile("getStringObject", compileAOT);

        NodeIterable<ConstantNode> filter = getConstantNodes(result);
        assertDeepEquals(1, filter.count());
        JavaConstant c = filter.first().asJavaConstant();
        Assert.assertEquals(getSnippetReflection().asObject(String.class, c), "test string");

        assertDeepEquals(0, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes().filter(ReadNode.class).count());
    }

    public static Boolean getBoxedBoolean() {
        return Boolean.valueOf(true);
    }

    @Ignore("ImmutableCode override may not work reliably in non-hosted mode")
    @Test
    public void testBoxedBooleanAOT() {
        StructuredGraph result = compile("getBoxedBoolean", true);

        assertDeepEquals(2, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(1, result.getNodes(PiNode.TYPE).count());
        assertDeepEquals(1, getConstantNodes(result).count());
        ConstantNode constant = getConstantNodes(result).first();
        assertDeepEquals(JavaKind.Long, constant.getStackKind());
        assertDeepEquals(((HotSpotResolvedObjectType) getMetaAccess().lookupJavaType(Boolean.class)).klass(), constant.asConstant());
    }

    @Test
    public void testBoxedBoolean() {
        StructuredGraph result = compile("getBoxedBoolean", false);
        assertDeepEquals(0, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes(PiNode.TYPE).count());
        assertDeepEquals(1, getConstantNodes(result).count());
        ConstantNode constant = getConstantNodes(result).first();
        assertDeepEquals(JavaKind.Object, constant.getStackKind());

        JavaConstant c = constant.asJavaConstant();
        Assert.assertEquals(getSnippetReflection().asObject(Boolean.class, c), Boolean.TRUE);
    }

    @SuppressWarnings("try")
    private StructuredGraph compile(String test, boolean compileAOT) {
        try (OverrideScope s = OptionValue.override(ImmutableCode, compileAOT)) {
            StructuredGraph graph = parseEager(test, AllowAssumptions.YES);
            ResolvedJavaMethod method = graph.method();
            // create suites everytime, as we modify options for the compiler
            SuitesProvider suitesProvider = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getSuites();
            final Suites suitesLocal = suitesProvider.getDefaultSuites();
            final LIRSuites lirSuitesLocal = suitesProvider.getDefaultLIRSuites();
            final CompilationResult compResult = compileGraph(graph, method, getProviders(), getBackend(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL, graph.getProfilingInfo(),
                            suitesLocal, lirSuitesLocal, new CompilationResult(), CompilationResultBuilderFactory.Default);
            addMethod(method, compResult);
            return graph;
        }
    }
}
