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
package org.graalvm.compiler.core.test;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import org.junit.Test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.loop.InductionVariable;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CountedLoopTest extends GraalCompilerTest {

    @FunctionalInterface
    private interface IVProperty {
        ValueNode get(InductionVariable iv);
    }

    /**
     * Get a property of an induction variable.
     *
     * @param property
     */
    private static int get(IVProperty property, int iv) {
        return iv;
    }

    private static class Result {
        public int extremum;
        public int exitValue;

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + exitValue;
            result = prime * result + extremum;
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
            ret.extremum = get(InductionVariable::extremumNode, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void increment1() {
        test("incrementSnippet", 0, 256, 1);
    }

    @Test
    public void increment2() {
        test("incrementSnippet", 0, 256, 2);
    }

    @Test
    public void increment3() {
        test("incrementSnippet", 0, 256, 3);
    }

    public static Result incrementEqSnippet(int start, int limit, int step) {
        int i;
        int inc = ((step - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        Result ret = new Result();
        for (i = start; i <= limit; i += inc) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void incrementEq1() {
        test("incrementEqSnippet", 0, 256, 1);
    }

    @Test
    public void incrementEq2() {
        test("incrementEqSnippet", 0, 256, 2);
    }

    @Test
    public void incrementEq3() {
        test("incrementEqSnippet", 0, 256, 3);
    }

    public static Result decrementSnippet(int start, int limit, int step) {
        int i;
        int dec = ((step - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        Result ret = new Result();
        for (i = start; i > limit; i -= dec) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void decrement1() {
        test("decrementSnippet", 256, 0, 1);
    }

    @Test
    public void decrement2() {
        test("decrementSnippet", 256, 0, 2);
    }

    @Test
    public void decrement3() {
        test("decrementSnippet", 256, 0, 3);
    }

    public static Result decrementEqSnippet(int start, int limit, int step) {
        int i;
        int dec = ((step - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        Result ret = new Result();
        for (i = start; i >= limit; i -= dec) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void decrementEq1() {
        test("decrementEqSnippet", 256, 0, 1);
    }

    @Test
    public void decrementEq2() {
        test("decrementEqSnippet", 256, 0, 2);
    }

    @Test
    public void decrementEq3() {
        test("decrementEqSnippet", 256, 0, 3);
    }

    public static Result twoVariablesSnippet() {
        Result ret = new Result();
        int j = 0;
        for (int i = 0; i < 1024; i++) {
            j += 5;
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, j);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, j);
        return ret;
    }

    @Test
    public void testTwoVariables() {
        test("twoVariablesSnippet");
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    private static class IVPropertyNode extends FloatingNode implements LIRLowerable {

        public static final NodeClass<IVPropertyNode> TYPE = NodeClass.create(IVPropertyNode.class);

        private final IVProperty property;
        @Input private ValueNode iv;

        protected IVPropertyNode(IVProperty property, ValueNode iv) {
            super(TYPE, iv.stamp().unrestricted());
            this.property = property;
            this.iv = iv;
        }

        public void rewrite(LoopsData loops) {
            InductionVariable inductionVariable = loops.getInductionVariable(iv);
            assert inductionVariable != null;
            ValueNode node = property.get(inductionVariable);
            replaceAtUsagesAndDelete(node);
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.operand(iv));
        }
    }

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins plugins = super.getDefaultGraphBuilderPlugins();
        Registration r = new Registration(plugins.getInvocationPlugins(), CountedLoopTest.class);

        r.register2("get", IVProperty.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                IVProperty property = null;
                if (arg1.isConstant()) {
                    property = getSnippetReflection().asObject(IVProperty.class, arg1.asJavaConstant());
                }
                if (property != null) {
                    b.addPush(JavaKind.Int, new IVPropertyNode(property, arg2));
                    return true;
                } else {
                    return false;
                }
            }
        });

        return plugins;
    }

    @Override
    protected boolean checkMidTierGraph(StructuredGraph graph) {
        LoopsData loops = new LoopsData(graph);
        loops.detectedCountedLoops();
        for (IVPropertyNode node : graph.getNodes().filter(IVPropertyNode.class)) {
            node.rewrite(loops);
        }
        assert graph.getNodes().filter(IVPropertyNode.class).isEmpty();
        return true;
    }

    public static Result incrementNeqSnippet(int limit) {
        int i;
        int posLimit = ((limit - 1) & 0xFFFF) + 1; // make sure limit is always strictly positive
        Result ret = new Result();
        for (i = 0; i != posLimit; i++) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void decrementNeq() {
        test("decrementNeqSnippet", 256);
    }

    public static Result decrementNeqSnippet(int limit) {
        int i;
        int posLimit = ((limit - 1) & 0xFFFF) + 1; // make sure limit is always strictly positive
        Result ret = new Result();
        for (i = posLimit; i != 0; i--) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void incrementNeq() {
        test("incrementNeqSnippet", 256);
    }
}
