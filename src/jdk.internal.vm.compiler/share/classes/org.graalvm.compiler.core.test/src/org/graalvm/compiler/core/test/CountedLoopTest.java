/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.loop.InductionVariable;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CountedLoopTest extends GraalCompilerTest {

    @FunctionalInterface
    private interface IVProperty {
        ValueNode get(InductionVariable iv);
    }

    @FunctionalInterface
    private interface StaticIVProperty {
        long get(InductionVariable iv);
    }

    @FunctionalInterface
    private interface IVPredicate {
        boolean test(InductionVariable iv);
    }

    /**
     * Get a property of an induction variable.
     */
    private static int get(@SuppressWarnings("unused") IVProperty property, @SuppressWarnings("unused") StaticIVProperty staticProperty, @SuppressWarnings("unused") IVPredicate constantCheck,
                    int iv) {
        return iv;
    }

    private static int get(@SuppressWarnings("unused") IVProperty property, int iv) {
        return iv;
    }

    private static long get(@SuppressWarnings("unused") IVProperty property, @SuppressWarnings("unused") StaticIVProperty staticProperty, @SuppressWarnings("unused") IVPredicate constantCheck,
                    long iv) {
        return iv;
    }

    private static long get(@SuppressWarnings("unused") IVProperty property, long iv) {
        return iv;
    }

    private static class Result {
        public long extremum;
        public long exitValue;

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Long.hashCode(exitValue);
            result = prime * result + Long.hashCode(extremum);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Result)) {
                return false;
            }
            Result other = (Result) obj;
            return extremum == other.extremum && exitValue == other.exitValue;
        }

        @Override
        public String toString() {
            return String.format("extremum = %d, exitValue = %d", extremum, exitValue);
        }
    }

    public static Result incrementSnippet(int start, int limit, int step) {
        int i;
        int inc = ((step - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        Result ret = new Result();
        for (i = start; i < limit; i += inc) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void increment1() {
        testCounted("incrementSnippet", 0, 256, 1);
    }

    @Test
    public void increment2() {
        testCounted("incrementSnippet", 0, 256, 2);
    }

    @Test
    public void increment3() {
        testCounted("incrementSnippet", 0, 256, 3);
    }

    @Test
    public void increment4() {
        testCounted("incrementSnippet", -10, 1, Integer.MAX_VALUE);
    }

    @Test
    public void increment5() {
        testCounted("incrementSnippet", 256, 256, 1);
    }

    @Test
    public void increment6() {
        testCounted("incrementSnippet", 257, 256, 1);
    }

    @Test
    public void increment7() {
        testCounted("incrementSnippet", -10, Integer.MAX_VALUE, 1);
    }

    @Test
    public void increment8() {
        testCounted("incrementSnippet", -10, Integer.MAX_VALUE - 1, 2);
    }

    public static Result incrementEqSnippet(int start, int limit, int step) {
        int i;
        int inc = ((step - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        Result ret = new Result();
        for (i = start; i <= limit; i += inc) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void incrementEq1() {
        testCounted("incrementEqSnippet", 0, 256, 1);
    }

    @Test
    public void incrementEq2() {
        testCounted("incrementEqSnippet", 0, 256, 2);
    }

    @Test
    public void incrementEq3() {
        testCounted("incrementEqSnippet", 0, 256, 3);
    }

    @Test
    public void incrementEq4() {
        testCounted("incrementEqSnippet", -10, 0, Integer.MAX_VALUE);
    }

    @Test
    public void incrementEq5() {
        testCounted("incrementEqSnippet", 256, 256, 1);
    }

    @Test
    public void incrementEq6() {
        testCounted("incrementEqSnippet", 257, 256, 1);
    }

    @Test
    public void incrementEq7() {
        testCounted("incrementEqSnippet", -10, Integer.MAX_VALUE - 1, 1);
    }

    @Test
    public void incrementEq8() {
        testCounted("incrementEqSnippet", -10, Integer.MAX_VALUE - 2, 2);
    }

    public static Result decrementSnippet(int start, int limit, int step) {
        int i;
        int dec = ((step - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        Result ret = new Result();
        for (i = start; i > limit; i -= dec) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void decrement1() {
        testCounted("decrementSnippet", 256, 0, 1);
    }

    @Test
    public void decrement2() {
        testCounted("decrementSnippet", 256, 0, 2);
    }

    @Test
    public void decrement3() {
        testCounted("decrementSnippet", 256, 0, 3);
    }

    @Test
    public void decrement4() {
        testCounted("decrementSnippet", Integer.MAX_VALUE, -10, 1);
    }

    @Test
    public void decrement5() {
        testCounted("decrementSnippet", Integer.MAX_VALUE, -10, 2);
    }

    public static Result decrementEqSnippet(int start, int limit, int step) {
        int i;
        int dec = ((step - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        Result ret = new Result();
        for (i = start; i >= limit; i -= dec) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void decrementEq1() {
        testCounted("decrementEqSnippet", 256, 0, 1);
    }

    @Test
    public void decrementEq2() {
        testCounted("decrementEqSnippet", 256, 0, 2);
    }

    @Test
    public void decrementEq3() {
        testCounted("decrementEqSnippet", 256, 0, 3);
    }

    @Test
    public void decrementEq4() {
        testCounted("decrementEqSnippet", -10, 0, Integer.MAX_VALUE);
    }

    @Test
    public void decrementEq5() {
        testCounted("decrementEqSnippet", Integer.MAX_VALUE, -10, 1);
    }

    @Test
    public void decrementEq6() {
        testCounted("decrementEqSnippet", Integer.MAX_VALUE, -10, 2);
    }

    public static Result twoVariablesSnippet() {
        Result ret = new Result();
        int j = 0;
        for (int i = 0; i < 1024; i++) {
            j += 5;
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, j);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, j);
        return ret;
    }

    @Test
    public void testTwoVariables() {
        testCounted("twoVariablesSnippet");
    }

    public static Result incrementNeqSnippet(int limit) {
        int i;
        int posLimit = ((limit - 1) & 0xFFFF) + 1; // make sure limit is always strictly positive
        Result ret = new Result();
        for (i = 0; i != posLimit; i++) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void decrementNeq() {
        testCounted("decrementNeqSnippet", 256);
    }

    public static Result decrementNeqSnippet(int limit) {
        int i;
        int posLimit = ((limit - 1) & 0xFFFF) + 1; // make sure limit is always strictly positive
        Result ret = new Result();
        for (i = posLimit; i != 0; i--) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void incrementNeq() {
        testCounted("incrementNeqSnippet", 256);
    }

    public static Result incrementLongSnippet(long start, long limit, long step) {
        long i;
        long inc = ((step - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        Result ret = new Result();
        for (i = start; i < limit; i += inc) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void incrementLong1() {
        testCounted("incrementLongSnippet", 0L, 256L, 1L);
    }

    @Test
    public void incrementLong2() {
        testCounted("incrementLongSnippet", 0L, 256L, 2L);
    }

    @Test
    public void incrementLong3() {
        testCounted("incrementLongSnippet", 0L, 256L, 3L);
    }

    @Test
    public void incrementLong4() {
        testCounted("incrementLongSnippet", -10L, 1L, Long.MAX_VALUE);
    }

    @Test
    public void incrementLong5() {
        testCounted("incrementLongSnippet", 256L, 256L, 1L);
    }

    @Test
    public void incrementLong6() {
        testCounted("incrementLongSnippet", 257L, 256L, 1L);
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    private static class IVPropertyNode extends FloatingNode implements LIRLowerable {

        public static final NodeClass<IVPropertyNode> TYPE = NodeClass.create(IVPropertyNode.class);

        private final IVProperty property;
        private final StaticIVProperty staticProperty;
        private final IVPredicate staticCheck;
        @Input private ValueNode iv;

        protected IVPropertyNode(IVProperty property, StaticIVProperty staticProperty, IVPredicate staticCheck, ValueNode iv) {
            super(TYPE, iv.stamp(NodeView.DEFAULT).unrestricted());
            this.property = property;
            this.staticProperty = staticProperty;
            this.staticCheck = staticCheck;
            this.iv = iv;
        }

        public void rewrite(LoopsData loops) {
            InductionVariable inductionVariable = loops.getInductionVariable(iv);
            assert inductionVariable != null;
            assertTrue(inductionVariable.getLoop().isCounted(), "must be counted");
            ValueNode node = null;
            if (staticCheck != null) {
                assert staticProperty != null;
                if (staticCheck.test(inductionVariable)) {
                    node = ConstantNode.forLong(staticProperty.get(inductionVariable), graph());
                }
            }
            if (node == null) {
                node = property.get(inductionVariable);
            }
            replaceAtUsagesAndDelete(node);
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.operand(iv));
        }
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        Registration r = new Registration(invocationPlugins, CountedLoopTest.class);
        registerPlugins(r, JavaKind.Int);
        registerPlugins(r, JavaKind.Long);
        super.registerInvocationPlugins(invocationPlugins);
    }

    private void registerPlugins(Registration r, JavaKind ivKind) {
        r.register2("get", IVProperty.class, ivKind.toJavaClass(), new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                IVProperty property = null;
                if (arg1.isConstant()) {
                    property = getSnippetReflection().asObject(IVProperty.class, arg1.asJavaConstant());
                }
                if (property != null) {
                    b.addPush(ivKind, new IVPropertyNode(property, null, null, arg2));
                    return true;
                } else {
                    return false;
                }
            }
        });
        r.register4("get", IVProperty.class, StaticIVProperty.class, IVPredicate.class, ivKind.toJavaClass(), new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4) {
                IVProperty property = null;
                StaticIVProperty staticProperty = null;
                IVPredicate staticCheck = null;
                if (arg1.isConstant()) {
                    property = getSnippetReflection().asObject(IVProperty.class, arg1.asJavaConstant());
                }
                if (arg2.isConstant()) {
                    staticProperty = getSnippetReflection().asObject(StaticIVProperty.class, arg2.asJavaConstant());
                }
                if (arg3.isConstant()) {
                    staticCheck = getSnippetReflection().asObject(IVPredicate.class, arg3.asJavaConstant());
                }
                if (property != null && staticProperty != null && staticCheck != null) {
                    b.addPush(ivKind, new IVPropertyNode(property, staticProperty, staticCheck, arg4));
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    @Override
    protected boolean checkHighTierGraph(StructuredGraph graph) {
        LoopsData loops = new LoopsData(graph);
        loops.detectedCountedLoops();
        for (IVPropertyNode node : graph.getNodes().filter(IVPropertyNode.class)) {
            node.rewrite(loops);
        }
        assert graph.getNodes().filter(IVPropertyNode.class).isEmpty();
        return true;
    }

    @Override
    protected HighTierContext getDefaultHighTierContext() {
        // Don't convert unreached paths into Guard
        return new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.NONE);
    }

    private Object[] argsToBind;

    @Override
    protected Object[] getArgumentToBind() {
        return argsToBind;
    }

    public void testCounted(String snippetName, Object... args) {
        test(snippetName, args);
        argsToBind = args;
        test(snippetName, args);
        argsToBind = null;
    }
}
