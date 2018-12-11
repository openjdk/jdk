/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.phases.common.inlining.info;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.common.inlining.info.elem.Inlineable;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Represents an inlining opportunity for which profiling information suggests a monomorphic
 * receiver, but for which the receiver type cannot be proven. A type check guard will be generated
 * if this inlining is performed.
 */
public class TypeGuardInlineInfo extends AbstractInlineInfo {

    private final ResolvedJavaMethod concrete;
    private final ResolvedJavaType type;
    private Inlineable inlineableElement;

    public TypeGuardInlineInfo(Invoke invoke, ResolvedJavaMethod concrete, ResolvedJavaType type) {
        super(invoke);
        this.concrete = concrete;
        this.type = type;
        assert type.isArray() || type.isConcrete() : type;
    }

    @Override
    public int numberOfMethods() {
        return 1;
    }

    @Override
    public ResolvedJavaMethod methodAt(int index) {
        assert index == 0;
        return concrete;
    }

    @Override
    public Inlineable inlineableElementAt(int index) {
        assert index == 0;
        return inlineableElement;
    }

    @Override
    public double probabilityAt(int index) {
        assert index == 0;
        return 1.0;
    }

    @Override
    public double relevanceAt(int index) {
        assert index == 0;
        return 1.0;
    }

    @Override
    public void setInlinableElement(int index, Inlineable inlineableElement) {
        assert index == 0;
        this.inlineableElement = inlineableElement;
    }

    @Override
    public EconomicSet<Node> inline(Providers providers, String reason) {
        createGuard(graph(), providers);
        return inline(invoke, concrete, inlineableElement, false, reason);
    }

    @Override
    public void tryToDevirtualizeInvoke(Providers providers) {
        createGuard(graph(), providers);
        InliningUtil.replaceInvokeCallTarget(invoke, graph(), InvokeKind.Special, concrete);
    }

    @SuppressWarnings("try")
    private void createGuard(StructuredGraph graph, Providers providers) {
        try (DebugCloseable context = invoke.asNode().withNodeSourcePosition()) {
            ValueNode nonNullReceiver = InliningUtil.nonNullReceiver(invoke);
            LoadHubNode receiverHub = graph.unique(new LoadHubNode(providers.getStampProvider(), nonNullReceiver));
            ConstantNode typeHub = ConstantNode.forConstant(receiverHub.stamp(NodeView.DEFAULT), providers.getConstantReflection().asObjectHub(type), providers.getMetaAccess(), graph);

            LogicNode typeCheck = CompareNode.createCompareNode(graph, CanonicalCondition.EQ, receiverHub, typeHub, providers.getConstantReflection(), NodeView.DEFAULT);
            FixedGuardNode guard = graph.add(new FixedGuardNode(typeCheck, DeoptimizationReason.TypeCheckedInliningViolated, DeoptimizationAction.InvalidateReprofile));
            assert invoke.predecessor() != null;

            ValueNode anchoredReceiver = InliningUtil.createAnchoredReceiver(graph, guard, type, nonNullReceiver, true);
            invoke.callTarget().replaceFirstInput(nonNullReceiver, anchoredReceiver);

            graph.addBeforeFixed(invoke.asNode(), guard);
        }
    }

    @Override
    public String toString() {
        return "type-checked with type " + type.getName() + " and method " + concrete.format("%H.%n(%p):%r");
    }

    @Override
    public boolean shouldInline() {
        return concrete.shouldBeInlined();
    }
}
