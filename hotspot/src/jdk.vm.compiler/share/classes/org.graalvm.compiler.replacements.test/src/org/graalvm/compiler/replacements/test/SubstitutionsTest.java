/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.InputType.Guard;
import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;
import static org.hamcrest.CoreMatchers.instanceOf;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.StructuralInput.Guard;
import org.graalvm.compiler.nodeinfo.StructuralInput.Memory;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.memory.MemoryNode;

import jdk.vm.ci.meta.JavaKind;

public class SubstitutionsTest extends GraalCompilerTest {

    @NodeInfo(allowedUsageTypes = {Memory}, cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static class TestMemory extends FixedWithNextNode implements MemoryNode {
        private static final NodeClass<TestMemory> TYPE = NodeClass.create(TestMemory.class);

        protected TestMemory() {
            super(TYPE, StampFactory.forVoid());
        }

        @NodeIntrinsic
        public static native Memory memory();
    }

    @NodeInfo(allowedUsageTypes = {Guard}, cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static class TestGuard extends FloatingNode implements GuardingNode {
        private static final NodeClass<TestGuard> TYPE = NodeClass.create(TestGuard.class);

        @Input(Memory) MemoryNode memory;

        protected TestGuard(ValueNode memory) {
            super(TYPE, StampFactory.forVoid());
            this.memory = (MemoryNode) memory;
        }

        @NodeIntrinsic
        public static native Guard guard(Memory memory);
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static class TestValue extends FloatingNode {
        private static final NodeClass<TestValue> TYPE = NodeClass.create(TestValue.class);

        @Input(Guard) GuardingNode guard;

        protected TestValue(ValueNode guard) {
            super(TYPE, StampFactory.forKind(JavaKind.Int));
            this.guard = (GuardingNode) guard;
        }

        @NodeIntrinsic
        public static native int value(Guard guard);
    }

    private static class TestMethod {

        public static int test() {
            return 42;
        }
    }

    @ClassSubstitution(TestMethod.class)
    private static class TestMethodSubstitution {

        @MethodSubstitution
        public static int test() {
            Memory memory = TestMemory.memory();
            Guard guard = TestGuard.guard(memory);
            return TestValue.value(guard);
        }
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        InvocationPlugins invocationPlugins = conf.getPlugins().getInvocationPlugins();
        BytecodeProvider replacementBytecodeProvider = getReplacements().getReplacementBytecodeProvider();
        Registration r = new Registration(invocationPlugins, TestMethod.class, replacementBytecodeProvider);
        r.registerMethodSubstitution(TestMethodSubstitution.class, "test");
        return super.editGraphBuilderConfiguration(conf);
    }

    public static int callTest() {
        return TestMethod.test();
    }

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins ret = super.getDefaultGraphBuilderPlugins();
        // manually register generated factories, jvmci service providers don't work from unit tests
        new PluginFactory_SubstitutionsTest().registerPlugins(ret.getInvocationPlugins(), null);
        return ret;
    }

    @Override
    protected boolean checkHighTierGraph(StructuredGraph graph) {
        // Check that the graph contains the expected test nodes.
        NodeIterable<ReturnNode> retNodes = graph.getNodes().filter(ReturnNode.class);
        Assert.assertTrue("expected exactly one ReturnNode", retNodes.count() == 1);
        ReturnNode ret = retNodes.first();

        Assert.assertThat(ret.result(), instanceOf(TestValue.class));
        TestValue value = (TestValue) ret.result();

        Assert.assertThat(value.guard, instanceOf(TestGuard.class));
        TestGuard guard = (TestGuard) value.guard;

        Assert.assertThat(guard.memory, instanceOf(TestMemory.class));
        TestMemory memory = (TestMemory) guard.memory;

        // Remove the test nodes, replacing them by the constant 42.
        // This implicitly makes sure that the rest of the graph is valid.
        ret.replaceFirstInput(value, graph.unique(ConstantNode.forInt(42)));
        value.safeDelete();
        guard.safeDelete();
        graph.removeFixed(memory);

        return true;
    }

    @Test
    public void snippetTest() {
        test("callTest");
    }
}
