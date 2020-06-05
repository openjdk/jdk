/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Red Hat Inc. All rights reserved.
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



package org.graalvm.compiler.core.test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.internal.vm.compiler.collections.EconomicMap;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.graalvm.compiler.core.common.GraalOptions.StressTestEarlyReads;

public class LateMembarInsertionTest extends GraalCompilerTest {

    private final ResolvedJavaType volatileAccessType = getMetaAccess().lookupJavaType(VolatileAccess.class);
    private final ResolvedJavaType regularAccessField = getMetaAccess().lookupJavaType(RegularAccess.class);
    private final ResolvedJavaType volatileAccess2Type = getMetaAccess().lookupJavaType(VolatileAccess2.class);

    static class VolatileAccess {
        static volatile int field;
    }

    static class VolatileAccess2 {
        static volatile int field;
    }

    static class RegularAccess {
        static int field;
    }

    public static int volatileFieldLoadFieldLoad() {
        int v1 = VolatileAccess.field;
        int v2 = RegularAccess.field;
        return v1 + v2;
    }

    @Test
    public void test01() {
        List<TypePair> accesses = getAccesses("volatileFieldLoadFieldLoad", stressTestEarlyReads());

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), volatileAccessType);
        Assert.assertEquals(accesses.get(1).getType(), regularAccessField);
        Assert.assertTrue(accesses.get(0).isRead());
        Assert.assertTrue(accesses.get(1).isRead());
    }

    public static int volatileFieldLoadVolatileFieldLoad() {
        int v1 = VolatileAccess.field;
        int v2 = VolatileAccess2.field;
        return v1 + v2;
    }

    @Test
    public void test02() {
        List<TypePair> accesses = getAccesses("volatileFieldLoadVolatileFieldLoad", stressTestEarlyReads());

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), volatileAccessType);
        Assert.assertEquals(accesses.get(1).getType(), volatileAccess2Type);
        Assert.assertTrue(accesses.get(0).isRead());
        Assert.assertTrue(accesses.get(1).isRead());
    }

    public static int volatileFieldLoadVolatileFieldStore(int v2) {
        int v1 = VolatileAccess.field;
        VolatileAccess2.field = v2;
        return v1;
    }

    @Test
    public void test03() {
        List<TypePair> accesses = getAccesses("volatileFieldLoadVolatileFieldStore");

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), volatileAccessType);
        Assert.assertEquals(accesses.get(1).getType(), volatileAccess2Type);
        Assert.assertTrue(accesses.get(0).isRead());
        Assert.assertTrue(accesses.get(1).isWrite());
    }

    public static int volatileFieldStoreVolatileFieldLoad(int v2) {
        VolatileAccess.field = v2;
        return VolatileAccess2.field;
    }

    @Test
    public void test04() {
        List<TypePair> accesses = getAccesses("volatileFieldStoreVolatileFieldLoad", stressTestEarlyReads());

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), volatileAccessType);
        Assert.assertEquals(accesses.get(1).getType(), volatileAccess2Type);
        Assert.assertTrue(accesses.get(0).isWrite());
        Assert.assertTrue(accesses.get(1).isRead());
    }

    public static int fieldLoadVolatileFieldStore(int v2) {
        int v1 = RegularAccess.field;
        VolatileAccess2.field = v2;
        return v1;
    }

    @Test
    public void test05() {
        List<TypePair> accesses = getAccesses("fieldLoadVolatileFieldStore");

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), regularAccessField);
        Assert.assertEquals(accesses.get(1).getType(), volatileAccess2Type);
        Assert.assertTrue(accesses.get(0).isRead());
        Assert.assertTrue(accesses.get(1).isWrite());
    }

    public static void volatileFieldStoreVolatileFieldStore(int v1, int v2) {
        VolatileAccess.field = v1;
        VolatileAccess2.field = v2;
    }

    @Test
    public void test06() {
        List<TypePair> accesses = getAccesses("volatileFieldStoreVolatileFieldStore");

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), volatileAccessType);
        Assert.assertEquals(accesses.get(1).getType(), volatileAccess2Type);
        Assert.assertTrue(accesses.get(0).isWrite());
        Assert.assertTrue(accesses.get(1).isWrite());
    }

    public static int volatileFieldLoad() {
        return VolatileAccess.field;
    }

    @Test
    public void test07() {
        verifyMembars("volatileFieldLoad", membarsExpected());
    }

    private boolean membarsExpected() {
        return !(getTarget().arch instanceof AArch64);
    }

    public static void volatileFieldStore(int v) {
        VolatileAccess.field = v;
    }

    @Test
    public void test08() {
        verifyMembars("volatileFieldStore", membarsExpected());
    }

    // Unused field load should be optimized out and leave no barrier behind
    @SuppressWarnings("unused")
    public static void volatileFieldStoreUnusedVolatileFieldLoadVolatileFieldStore(int v2) {
        VolatileAccess2.field = v2;
        int v1 = VolatileAccess.field;
        VolatileAccess2.field = v2;
    }

    @Test
    public void test09() {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("volatileFieldStoreUnusedVolatileFieldLoadVolatileFieldStore"));
        List<TypePair> accesses = getAccesses(graph);

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), volatileAccess2Type);
        Assert.assertEquals(accesses.get(1).getType(), volatileAccess2Type);
        Assert.assertTrue(accesses.get(0).isWrite());
        Assert.assertTrue(accesses.get(1).isWrite());
        Assert.assertEquals(membarsExpected() ? 4 : 0, getMembars(graph).size());
    }

    // Unused field load should be optimized out and leave no barrier behind
    @SuppressWarnings("unused")
    public static void unusedVolatileFieldLoadVolatileFieldStore(int v2) {
        int v1 = VolatileAccess.field;
        VolatileAccess2.field = v2;
    }

    @Test
    public void test10() {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("unusedVolatileFieldLoadVolatileFieldStore"));
        List<TypePair> accesses = getAccesses(graph);

        Assert.assertEquals(accesses.size(), 1);
        Assert.assertEquals(accesses.get(0).getType(), volatileAccess2Type);
        Assert.assertTrue(accesses.get(0).isWrite());
        Assert.assertEquals(membarsExpected() ? 2 : 0, getMembars(graph).size());
    }

    public static int unsafeVolatileFieldLoad(Object o, long offset) {
        return UNSAFE.getIntVolatile(o, offset);
    }

    @Test
    public void test11() {
        verifyMembars("unsafeVolatileFieldLoad", membarsExpected());
    }

    public static void unsafeVolatileFieldStore(Object o, long offset, int v) {
        UNSAFE.putIntVolatile(o, offset, v);
    }

    @Test
    public void test12() {
        verifyMembars("unsafeVolatileFieldStore", membarsExpected());
    }

    private void verifyMembars(String method, boolean expectsMembar) {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod(method));
        StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
        ControlFlowGraph cfg = schedule.getCFG();
        Block[] blocks = cfg.getBlocks();
        Assert.assertEquals(blocks.length, 1);
        Block block = blocks[0];
        List<Node> nodes = schedule.nodesFor(block);
        Node preBarrier = null;
        Node postBarrier = null;
        Node mem = null;
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node instanceof MembarNode) {
                if (preBarrier == null) {
                    Assert.assertNull(mem);
                    preBarrier = node;
                } else {
                    Assert.assertNull(postBarrier);
                    Assert.assertNotNull(mem);
                    postBarrier = node;
                }
            } else if (node instanceof MemoryAccess) {
                Assert.assertEquals(preBarrier != null, expectsMembar);
                Assert.assertNull(postBarrier);
                Assert.assertNull(mem);
                mem = node;
            }
        }
        Assert.assertEquals(preBarrier != null, expectsMembar);
        Assert.assertEquals(postBarrier != null, expectsMembar);
        Assert.assertNotNull(mem);
    }

    private static OptionValues stressTestEarlyReads() {
        EconomicMap<OptionKey<?>, Object> overrides = OptionValues.newOptionMap();
        overrides.put(StressTestEarlyReads, true);
        return new OptionValues(getInitialOptions(), overrides);
    }

    static class TypePair {
        private boolean isRead;
        private ResolvedJavaType type;

        TypePair(boolean isRead, ResolvedJavaType type) {
            this.isRead = isRead;
            this.type = type;
        }

        public boolean isRead() {
            return isRead;
        }

        public boolean isWrite() {
            return !isRead;
        }

        public ResolvedJavaType getType() {
            return type;
        }
    }

    private List<TypePair> getAccesses(String test, OptionValues options) {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod(test), options);
        return getAccesses(graph);
    }

    private List<TypePair> getAccesses(StructuredGraph graph) {
        StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
        ControlFlowGraph cfg = schedule.getCFG();
        Block[] blocks = cfg.getBlocks();

        return Arrays.stream(blocks).flatMap(b -> schedule.nodesFor(b).stream()).filter(n -> n instanceof MemoryAccess).map(
                        n -> new TypePair(n instanceof ReadNode, classForAccess((FixedAccessNode) n))).collect(Collectors.toList());
    }

    private List<TypePair> getAccesses(String test) {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod(test));
        return getAccesses(graph);
    }

    private ResolvedJavaType classForAccess(FixedAccessNode n) {
        AddressNode address = n.getAddress();
        ValueNode base = address.getBase();
        Stamp stamp = base.stamp(NodeView.DEFAULT);
        MetaAccessProvider metaAccess = getMetaAccess();
        ResolvedJavaType javaType = stamp.javaType(metaAccess);
        if (javaType == metaAccess.lookupJavaType(Class.class) && base instanceof ConstantNode) {
            ConstantReflectionProvider constantReflection = getConstantReflection();
            javaType = constantReflection.asJavaType(base.asConstant());
        }
        return javaType;
    }

    private static List<Node> getMembars(StructuredGraph graph) {
        StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
        ControlFlowGraph cfg = schedule.getCFG();
        Block[] blocks = cfg.getBlocks();

        return Arrays.stream(blocks).flatMap(b -> schedule.nodesFor(b).stream()).filter(n -> n instanceof MembarNode).collect(Collectors.toList());
    }
}
