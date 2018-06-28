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

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.nodes.ConstantNode.getConstantNodes;

/**
 * use
 *
 * <pre>
 * mx unittest AheadOfTimeCompilationTest @-XX:CompileCommand='print,*AheadOfTimeCompilationTest.*'
 * </pre>
 *
 * to print disassembly.
 */
public class AheadOfTimeCompilationTest extends HotSpotGraalCompilerTest {

    public static final Object STATICFINALOBJECT = new Object();
    public static final String STATICFINALSTRING = "test string";

    public static Object getStaticFinalObject() {
        return AheadOfTimeCompilationTest.STATICFINALOBJECT;
    }

    @Before
    public void setUp() {
        // Ignore on SPARC
        Assume.assumeFalse("skipping on AArch64", getTarget().arch instanceof AArch64);
    }

    @Test
    public void testStaticFinalObjectAOT() {
        StructuredGraph result = compile("getStaticFinalObject", true);
        assertDeepEquals(1, getConstantNodes(result).count());
        Stamp constantStamp = getConstantNodes(result).first().stamp(NodeView.DEFAULT);
        Assert.assertTrue(constantStamp.toString(), constantStamp instanceof KlassPointerStamp);
        int expected = runtime().getVMConfig().classMirrorIsHandle ? 3 : 2;
        assertDeepEquals(expected, result.getNodes().filter(ReadNode.class).count());
    }

    @Test
    public void testStaticFinalObject() {
        StructuredGraph result = compile("getStaticFinalObject", false);
        assertDeepEquals(1, getConstantNodes(result).count());
        assertDeepEquals(JavaKind.Object, getConstantNodes(result).first().getStackKind());
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
        int expected = runtime().getVMConfig().classMirrorIsHandle ? 2 : 1;
        assertDeepEquals(expected, result.getNodes().filter(ReadNode.class).count());
    }

    @Test
    public void testClassObject() {
        StructuredGraph result = compile("getClassObject", false);

        NodeIterable<ConstantNode> filter = getConstantNodes(result);
        assertDeepEquals(1, filter.count());
        JavaConstant c = filter.first().asJavaConstant();
        Assert.assertEquals(getSnippetReflection().asObject(Class.class, c), AheadOfTimeCompilationTest.class);

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
        Stamp constantStamp = filter.first().stamp(NodeView.DEFAULT);
        Assert.assertTrue(constantStamp instanceof KlassPointerStamp);
        int expected = runtime().getVMConfig().classMirrorIsHandle ? 3 : 2;
        assertDeepEquals(expected, result.getNodes().filter(ReadNode.class).count());
    }

    @Test
    public void testPrimitiveClassObject() {
        StructuredGraph result = compile("getPrimitiveClassObject", false);
        NodeIterable<ConstantNode> filter = getConstantNodes(result);
        assertDeepEquals(1, filter.count());
        JavaConstant c = filter.first().asJavaConstant();
        Assert.assertEquals(getSnippetReflection().asObject(Class.class, c), Integer.TYPE);

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

    @Test
    public void testBoxedBooleanAOT() {
        StructuredGraph result = compile("getBoxedBoolean", true);

        assertDeepEquals(0, result.getNodes().filter(FloatingReadNode.class).count());
        assertDeepEquals(0, result.getNodes(PiNode.TYPE).count());
        assertDeepEquals(1, getConstantNodes(result).count());
        ConstantNode constant = getConstantNodes(result).first();
        assertDeepEquals(JavaKind.Object, constant.getStackKind());

        JavaConstant c = constant.asJavaConstant();
        Assert.assertEquals(getSnippetReflection().asObject(Boolean.class, c), Boolean.TRUE);
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
        OptionValues options = new OptionValues(getInitialOptions(), ImmutableCode, compileAOT);
        StructuredGraph graph = parseEager(test, AllowAssumptions.YES, options);
        compile(graph.method(), graph);
        return graph;
    }
}
