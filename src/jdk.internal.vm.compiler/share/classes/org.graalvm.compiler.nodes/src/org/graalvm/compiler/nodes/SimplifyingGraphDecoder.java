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


package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.Guard;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.util.List;

import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Graph decoder that simplifies nodes during decoding. The standard
 * {@link Canonicalizable#canonical node canonicalization} interface is used to canonicalize nodes
 * during decoding. Additionally, {@link IfNode branches} and {@link IntegerSwitchNode switches}
 * with constant conditions are simplified.
 */
public class SimplifyingGraphDecoder extends GraphDecoder {

    protected final CoreProviders providers;
    protected final boolean canonicalizeReads;
    protected final CanonicalizerTool canonicalizerTool;

    protected class PECanonicalizerTool implements CanonicalizerTool {

        private final Assumptions assumptions;
        private final OptionValues options;

        public PECanonicalizerTool(Assumptions assumptions, OptionValues options) {
            this.assumptions = assumptions;
            this.options = options;
        }

        @Override
        public OptionValues getOptions() {
            return options;
        }

        @Override
        public MetaAccessProvider getMetaAccess() {
            return providers.getMetaAccess();
        }

        @Override
        public ConstantReflectionProvider getConstantReflection() {
            return providers.getConstantReflection();
        }

        @Override
        public ConstantFieldProvider getConstantFieldProvider() {
            return providers.getConstantFieldProvider();
        }

        @Override
        public boolean canonicalizeReads() {
            return canonicalizeReads;
        }

        @Override
        public boolean allUsagesAvailable() {
            return false;
        }

        @Override
        public Assumptions getAssumptions() {
            return assumptions;
        }

        @Override
        public Integer smallestCompareWidth() {
            // to be safe, just report null here
            // there will be more opportunities for this optimization later
            return null;
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED, allowedUsageTypes = {Guard})
    static class CanonicalizeToNullNode extends FloatingNode implements Canonicalizable, GuardingNode {
        public static final NodeClass<CanonicalizeToNullNode> TYPE = NodeClass.create(CanonicalizeToNullNode.class);

        protected CanonicalizeToNullNode(Stamp stamp) {
            super(TYPE, stamp);
        }

        @Override
        public Node canonical(CanonicalizerTool tool) {
            return null;
        }
    }

    public SimplifyingGraphDecoder(Architecture architecture, StructuredGraph graph, CoreProviders providers, boolean canonicalizeReads) {
        super(architecture, graph);
        this.providers = providers;
        this.canonicalizeReads = canonicalizeReads;
        this.canonicalizerTool = new PECanonicalizerTool(graph.getAssumptions(), graph.getOptions());
    }

    @Override
    protected void cleanupGraph(MethodScope methodScope) {
        GraphUtil.normalizeLoops(graph);
        super.cleanupGraph(methodScope);

        for (Node node : graph.getNewNodes(methodScope.methodStartMark)) {
            if (node instanceof MergeNode) {
                MergeNode mergeNode = (MergeNode) node;
                if (mergeNode.forwardEndCount() == 1) {
                    graph.reduceTrivialMerge(mergeNode);
                }
            } else if (node instanceof BeginNode || node instanceof KillingBeginNode) {
                if (!(node.predecessor() instanceof ControlSplitNode) && node.hasNoUsages()) {
                    GraphUtil.unlinkFixedNode((AbstractBeginNode) node);
                    node.safeDelete();
                }
            }
        }

        for (Node node : graph.getNewNodes(methodScope.methodStartMark)) {
            GraphUtil.tryKillUnused(node);
        }
    }

    @Override
    protected boolean allowLazyPhis() {
        /*
         * We do not need to exactly reproduce the encoded graph, so we want to avoid unnecessary
         * phi functions.
         */
        return true;
    }

    @Override
    protected void handleMergeNode(MergeNode merge) {
        /*
         * All inputs of non-loop phi nodes are known by now. We can infer the stamp for the phi, so
         * that parsing continues with more precise type information.
         */
        for (ValuePhiNode phi : merge.valuePhis()) {
            phi.inferStamp();
        }
    }

    @Override
    protected void handleFixedNode(MethodScope methodScope, LoopScope loopScope, int nodeOrderId, FixedNode node) {
        Node canonical = canonicalizeFixedNode(methodScope, node);
        if (canonical != node) {
            handleCanonicalization(loopScope, nodeOrderId, node, canonical);
        }
    }

    /**
     * Canonicalizes the provided node, which was originally a {@link FixedNode} but can already be
     * canonicalized (and therefore be a non-fixed node).
     *
     * @param methodScope The current method.
     * @param node The node to be canonicalized.
     */
    protected Node canonicalizeFixedNode(MethodScope methodScope, Node node) {
        if (node instanceof LoadFieldNode) {
            LoadFieldNode loadFieldNode = (LoadFieldNode) node;
            return loadFieldNode.canonical(canonicalizerTool);
        } else if (node instanceof FixedGuardNode) {
            FixedGuardNode guard = (FixedGuardNode) node;
            if (guard.getCondition() instanceof LogicConstantNode) {
                LogicConstantNode condition = (LogicConstantNode) guard.getCondition();
                if (condition.getValue() == guard.isNegated()) {
                    DeoptimizeNode deopt = new DeoptimizeNode(guard.getAction(), guard.getReason(), guard.getSpeculation());
                    if (guard.stateBefore() != null) {
                        deopt.setStateBefore(guard.stateBefore());
                    }
                    return deopt;
                } else {
                    return null;
                }
            }
            return node;
        } else if (node instanceof IfNode) {
            IfNode ifNode = (IfNode) node;
            if (ifNode.condition() instanceof LogicNegationNode) {
                ifNode.eliminateNegation();
            }
            if (ifNode.condition() instanceof LogicConstantNode) {
                boolean condition = ((LogicConstantNode) ifNode.condition()).getValue();
                AbstractBeginNode survivingSuccessor = ifNode.getSuccessor(condition);
                AbstractBeginNode deadSuccessor = ifNode.getSuccessor(!condition);

                graph.removeSplit(ifNode, survivingSuccessor);
                assert deadSuccessor.next() == null : "must not be parsed yet";
                deadSuccessor.safeDelete();
            }
            return node;
        } else if (node instanceof LoadIndexedNode) {
            LoadIndexedNode loadIndexedNode = (LoadIndexedNode) node;
            return loadIndexedNode.canonical(canonicalizerTool);
        } else if (node instanceof ArrayLengthNode) {
            ArrayLengthNode arrayLengthNode = (ArrayLengthNode) node;
            return arrayLengthNode.canonical(canonicalizerTool);
        } else if (node instanceof IntegerSwitchNode && ((IntegerSwitchNode) node).value().isConstant()) {
            IntegerSwitchNode switchNode = (IntegerSwitchNode) node;
            int value = switchNode.value().asJavaConstant().asInt();
            AbstractBeginNode survivingSuccessor = switchNode.successorAtKey(value);
            List<Node> allSuccessors = switchNode.successors().snapshot();

            graph.removeSplit(switchNode, survivingSuccessor);
            for (Node successor : allSuccessors) {
                if (successor != survivingSuccessor) {
                    assert ((AbstractBeginNode) successor).next() == null : "must not be parsed yet";
                    successor.safeDelete();
                }
            }
            return node;
        } else if (node instanceof Canonicalizable) {
            return ((Canonicalizable) node).canonical(canonicalizerTool);
        } else {
            return node;
        }
    }

    private static Node canonicalizeFixedNodeToNull(FixedNode node) {
        /*
         * When a node is unnecessary, we must not remove it right away because there might be nodes
         * that use it as a guard input. Therefore, we replace it with a more lightweight node
         * (which is floating and has no inputs).
         */
        return new CanonicalizeToNullNode(node.stamp);
    }

    @SuppressWarnings("try")
    private void handleCanonicalization(LoopScope loopScope, int nodeOrderId, FixedNode node, Node c) {
        assert c != node : "unnecessary call";
        try (DebugCloseable position = graph.withNodeSourcePosition(node)) {
            Node canonical = c == null ? canonicalizeFixedNodeToNull(node) : c;
            if (!canonical.isAlive()) {
                assert !canonical.isDeleted();
                canonical = graph.addOrUniqueWithInputs(canonical);
                if (canonical instanceof FixedWithNextNode) {
                    graph.addBeforeFixed(node, (FixedWithNextNode) canonical);
                } else if (canonical instanceof ControlSinkNode) {
                    FixedWithNextNode predecessor = (FixedWithNextNode) node.predecessor();
                    predecessor.setNext((ControlSinkNode) canonical);
                    List<Node> successorSnapshot = node.successors().snapshot();
                    node.safeDelete();
                    for (Node successor : successorSnapshot) {
                        successor.safeDelete();
                    }
                } else {
                    assert !(canonical instanceof FixedNode);
                }
            }
            if (!node.isDeleted()) {
                GraphUtil.unlinkFixedNode((FixedWithNextNode) node);
                node.replaceAtUsagesAndDelete(canonical);
            }
            assert lookupNode(loopScope, nodeOrderId) == node;
            registerNode(loopScope, nodeOrderId, canonical, true, false);
        }
    }

    @Override
    @SuppressWarnings("try")
    protected Node handleFloatingNodeBeforeAdd(MethodScope methodScope, LoopScope loopScope, Node node) {
        if (node instanceof ValueNode) {
            ((ValueNode) node).inferStamp();
        }
        if (node instanceof Canonicalizable) {
            try (DebugCloseable context = graph.withNodeSourcePosition(node)) {
                Node canonical = ((Canonicalizable) node).canonical(canonicalizerTool);
                if (canonical == null) {
                    /*
                     * This is a possible return value of canonicalization. However, we might need
                     * to add additional usages later on for which we need a node. Therefore, we
                     * just do nothing and leave the node in place.
                     */
                } else if (canonical != node) {
                    if (!canonical.isAlive()) {
                        assert !canonical.isDeleted();
                        canonical = graph.addOrUniqueWithInputs(canonical);
                    }
                    assert node.hasNoUsages();
                    return canonical;
                }
            }
        }
        return node;
    }

    @Override
    protected Node addFloatingNode(MethodScope methodScope, Node node) {
        /*
         * In contrast to the base class implementation, we do not need to exactly reproduce the
         * encoded graph. Since we do canonicalization, we also want nodes to be unique.
         */
        return graph.addOrUnique(node);
    }
}
