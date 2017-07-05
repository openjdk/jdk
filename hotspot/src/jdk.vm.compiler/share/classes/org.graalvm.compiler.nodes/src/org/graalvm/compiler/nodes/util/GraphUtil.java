/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.code.SourceStackTraceBailoutException;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeWorkList;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider;
import org.graalvm.compiler.nodes.spi.LimitedValueProxy;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.ValueProxy;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class GraphUtil {

    public static void killCFG(Node node, SimplifierTool tool) {
        NodeWorkList worklist = killCFG(node, tool, null);
        if (worklist != null) {
            for (Node successor : worklist) {
                killCFG(successor, tool, worklist);
            }
        }
    }

    private static NodeWorkList killCFG(Node node, SimplifierTool tool, NodeWorkList worklist) {
        NodeWorkList newWorklist = worklist;
        // DebugScope.forceDump(node.graph(), "kill CFG %s", node);
        if (node instanceof FixedNode) {
            newWorklist = killCFGLinear((FixedNode) node, newWorklist, tool);
        } else {
            propagateKill(node);
        }
        return newWorklist;
    }

    private static NodeWorkList killCFGLinear(FixedNode in, NodeWorkList worklist, SimplifierTool tool) {
        NodeWorkList newWorklist = worklist;
        FixedNode current = in;
        while (current != null) {
            FixedNode next = null;
            assert current.isAlive();
            if (current instanceof AbstractEndNode) {
                // We reached a control flow end.
                AbstractEndNode end = (AbstractEndNode) current;
                killEnd(end, tool);
            } else if (current instanceof FixedWithNextNode) {
                next = ((FixedWithNextNode) current).next();
            } else {
                // Normal control flow node.
                /*
                 * We do not take a successor snapshot because this iterator supports concurrent
                 * modifications as long as they do not change the size of the successor list. Not
                 * taking a snapshot allows us to see modifications to other branches that may
                 * happen while processing one branch.
                 */
                // assert node.successors().count() > 1 || node.successors().count() == 0 :
                // node.getClass();
                Iterator<Node> successors = current.successors().iterator();
                if (successors.hasNext()) {
                    Node first = successors.next();
                    if (!successors.hasNext()) {
                        next = (FixedNode) first;
                    } else {
                        if (newWorklist == null) {
                            newWorklist = in.graph().createNodeWorkList();
                        }
                        for (Node successor : current.successors()) {
                            newWorklist.add(successor);
                            if (successor instanceof LoopExitNode) {
                                LoopExitNode exit = (LoopExitNode) successor;
                                exit.replaceFirstInput(exit.loopBegin(), null);
                            }
                        }
                    }
                }
            }
            current.replaceAtPredecessor(null);
            propagateKill(current);
            current = next;
        }
        return newWorklist;
    }

    public static void killCFG(Node node) {
        killCFG(node, null);
    }

    private static void killEnd(AbstractEndNode end, SimplifierTool tool) {
        AbstractMergeNode merge = end.merge();
        if (merge != null) {
            merge.removeEnd(end);
            StructuredGraph graph = end.graph();
            if (merge instanceof LoopBeginNode && merge.forwardEndCount() == 0) {
                // dead loop
                for (PhiNode phi : merge.phis().snapshot()) {
                    propagateKill(phi);
                }
                LoopBeginNode begin = (LoopBeginNode) merge;
                // disconnect and delete loop ends & loop exits
                for (LoopEndNode loopend : begin.loopEnds().snapshot()) {
                    loopend.predecessor().replaceFirstSuccessor(loopend, null);
                    loopend.safeDelete();
                }
                begin.removeExits();
                FixedNode loopBody = begin.next();
                if (loopBody != null) { // for small infinite loops, the body may be killed while
                                        // killing the loop ends
                    killCFG(loopBody);
                }
                begin.safeDelete();
            } else if (merge instanceof LoopBeginNode && ((LoopBeginNode) merge).loopEnds().isEmpty()) {
                // not a loop anymore
                if (tool != null) {
                    merge.phis().forEach(phi -> tool.addToWorkList(phi.usages()));
                }
                graph.reduceDegenerateLoopBegin((LoopBeginNode) merge);
            } else if (merge.phiPredecessorCount() == 1) {
                // not a merge anymore
                if (tool != null) {
                    merge.phis().forEach(phi -> tool.addToWorkList(phi.usages()));
                }
                graph.reduceTrivialMerge(merge);
            }
        }
    }

    public static boolean isFloatingNode(Node n) {
        return !(n instanceof FixedNode);
    }

    private static void propagateKill(Node node) {
        if (node != null && node.isAlive()) {
            node.markDeleted();

            for (Node in : node.inputs()) {
                if (in.isAlive()) {
                    in.removeUsage(node);
                    if (in.hasNoUsages() && !(in instanceof FixedNode)) {
                        killWithUnusedFloatingInputs(in);
                    }
                }
            }

            ArrayList<Node> usageToKill = null;
            for (Node usage : node.usages()) {
                if (usage.isAlive() && !(usage instanceof FixedNode)) {
                    if (usageToKill == null) {
                        usageToKill = new ArrayList<>();
                    }
                    usageToKill.add(usage);
                }
            }
            if (usageToKill != null) {
                for (Node usage : usageToKill) {
                    if (usage.isAlive()) {
                        if (usage instanceof PhiNode) {
                            PhiNode phiNode = (PhiNode) usage;
                            usage.replaceFirstInput(node, null);
                            if (phiNode.merge() == null || !phiNode.hasValidInput()) {
                                propagateKill(usage);
                            }
                        } else {
                            propagateKill(usage);
                        }
                    }
                }
            }
        }
    }

    public static void killWithUnusedFloatingInputs(Node node) {
        node.markDeleted();
        outer: for (Node in : node.inputs()) {
            if (in.isAlive()) {
                in.removeUsage(node);
                if (in.hasNoUsages()) {
                    node.maybeNotifyZeroUsages(in);
                }
                if (!(in instanceof FixedNode)) {
                    if (in.hasNoUsages()) {
                        killWithUnusedFloatingInputs(in);
                    } else if (in instanceof PhiNode) {
                        for (Node use : in.usages()) {
                            if (use != in) {
                                continue outer;
                            }
                        }
                        in.replaceAtUsages(null);
                        killWithUnusedFloatingInputs(in);
                    }
                }
            }
        }
    }

    public static void removeFixedWithUnusedInputs(FixedWithNextNode fixed) {
        if (fixed instanceof StateSplit) {
            FrameState stateAfter = ((StateSplit) fixed).stateAfter();
            if (stateAfter != null) {
                ((StateSplit) fixed).setStateAfter(null);
                if (stateAfter.hasNoUsages()) {
                    killWithUnusedFloatingInputs(stateAfter);
                }
            }
        }
        unlinkFixedNode(fixed);
        killWithUnusedFloatingInputs(fixed);
    }

    public static void unlinkFixedNode(FixedWithNextNode fixed) {
        assert fixed.next() != null && fixed.predecessor() != null && fixed.isAlive() : fixed;
        FixedNode next = fixed.next();
        fixed.setNext(null);
        fixed.replaceAtPredecessor(next);
    }

    public static void checkRedundantPhi(PhiNode phiNode) {
        if (phiNode.isDeleted() || phiNode.valueCount() == 1) {
            return;
        }

        ValueNode singleValue = phiNode.singleValue();
        if (singleValue != PhiNode.MULTIPLE_VALUES) {
            Collection<PhiNode> phiUsages = phiNode.usages().filter(PhiNode.class).snapshot();
            Collection<ProxyNode> proxyUsages = phiNode.usages().filter(ProxyNode.class).snapshot();
            phiNode.replaceAtUsagesAndDelete(singleValue);
            for (PhiNode phi : phiUsages) {
                checkRedundantPhi(phi);
            }
            for (ProxyNode proxy : proxyUsages) {
                checkRedundantProxy(proxy);
            }
        }
    }

    public static void checkRedundantProxy(ProxyNode vpn) {
        if (vpn.isDeleted()) {
            return;
        }
        AbstractBeginNode proxyPoint = vpn.proxyPoint();
        if (proxyPoint instanceof LoopExitNode) {
            LoopExitNode exit = (LoopExitNode) proxyPoint;
            LoopBeginNode loopBegin = exit.loopBegin();
            Node vpnValue = vpn.value();
            for (ValueNode v : loopBegin.stateAfter().values()) {
                ValueNode v2 = v;
                if (loopBegin.isPhiAtMerge(v2)) {
                    v2 = ((PhiNode) v2).valueAt(loopBegin.forwardEnd());
                }
                if (vpnValue == v2) {
                    Collection<PhiNode> phiUsages = vpn.usages().filter(PhiNode.class).snapshot();
                    Collection<ProxyNode> proxyUsages = vpn.usages().filter(ProxyNode.class).snapshot();
                    vpn.replaceAtUsagesAndDelete(vpnValue);
                    for (PhiNode phi : phiUsages) {
                        checkRedundantPhi(phi);
                    }
                    for (ProxyNode proxy : proxyUsages) {
                        checkRedundantProxy(proxy);
                    }
                    return;
                }
            }
        }
    }

    /**
     * Remove loop header without loop ends. This can happen with degenerated loops like this one:
     *
     * <pre>
     * for (;;) {
     *     try {
     *         break;
     *     } catch (UnresolvedException iioe) {
     *     }
     * }
     * </pre>
     */
    public static void normalizeLoops(StructuredGraph graph) {
        boolean loopRemoved = false;
        for (LoopBeginNode begin : graph.getNodes(LoopBeginNode.TYPE)) {
            if (begin.loopEnds().isEmpty()) {
                assert begin.forwardEndCount() == 1;
                graph.reduceDegenerateLoopBegin(begin);
                loopRemoved = true;
            } else {
                normalizeLoopBegin(begin);
            }
        }

        if (loopRemoved) {
            /*
             * Removing a degenerated loop can make non-loop phi functions unnecessary. Therefore,
             * we re-check all phi functions and remove redundant ones.
             */
            for (Node node : graph.getNodes()) {
                if (node instanceof PhiNode) {
                    checkRedundantPhi((PhiNode) node);
                }
            }
        }
    }

    private static void normalizeLoopBegin(LoopBeginNode begin) {
        // Delete unnecessary loop phi functions, i.e., phi functions where all inputs are either
        // the same or the phi itself.
        for (PhiNode phi : begin.phis().snapshot()) {
            GraphUtil.checkRedundantPhi(phi);
        }
        for (LoopExitNode exit : begin.loopExits()) {
            for (ProxyNode vpn : exit.proxies().snapshot()) {
                GraphUtil.checkRedundantProxy(vpn);
            }
        }
    }

    /**
     * Gets an approximate source code location for a node if possible.
     *
     * @return the StackTraceElements if an approximate source location is found, null otherwise
     */
    public static StackTraceElement[] approxSourceStackTraceElement(Node node) {
        ArrayList<StackTraceElement> elements = new ArrayList<>();
        Node n = node;
        while (n != null) {
            if (n instanceof MethodCallTargetNode) {
                elements.add(((MethodCallTargetNode) n).targetMethod().asStackTraceElement(-1));
                n = ((MethodCallTargetNode) n).invoke().asNode();
            }

            if (n instanceof StateSplit) {
                FrameState state = ((StateSplit) n).stateAfter();
                elements.addAll(Arrays.asList(approxSourceStackTraceElement(state)));
                break;
            }
            n = n.predecessor();
        }
        return elements.toArray(new StackTraceElement[elements.size()]);
    }

    /**
     * Gets an approximate source code location for frame state.
     *
     * @return the StackTraceElements if an approximate source location is found, null otherwise
     */
    public static StackTraceElement[] approxSourceStackTraceElement(FrameState frameState) {
        ArrayList<StackTraceElement> elements = new ArrayList<>();
        FrameState state = frameState;
        while (state != null) {
            Bytecode code = state.getCode();
            if (code != null) {
                elements.add(code.asStackTraceElement(state.bci - 1));
            }
            state = state.outerFrameState();
        }
        return elements.toArray(new StackTraceElement[0]);
    }

    /**
     * Gets approximate stack trace elements for a bytecode position.
     */
    public static StackTraceElement[] approxSourceStackTraceElement(BytecodePosition bytecodePosition) {
        ArrayList<StackTraceElement> elements = new ArrayList<>();
        BytecodePosition position = bytecodePosition;
        while (position != null) {
            ResolvedJavaMethod method = position.getMethod();
            if (method != null) {
                elements.add(method.asStackTraceElement(position.getBCI()));
            }
            position = position.getCaller();
        }
        return elements.toArray(new StackTraceElement[0]);
    }

    /**
     * Gets an approximate source code location for a node, encoded as an exception, if possible.
     *
     * @return the exception with the location
     */
    public static RuntimeException approxSourceException(Node node, Throwable cause) {
        final StackTraceElement[] elements = approxSourceStackTraceElement(node);
        return createBailoutException(cause == null ? "" : cause.getMessage(), cause, elements);
    }

    /**
     * Creates a bailout exception with the given stack trace elements and message.
     *
     * @param message the message of the exception
     * @param elements the stack trace elements
     * @return the exception
     */
    public static BailoutException createBailoutException(String message, Throwable cause, StackTraceElement[] elements) {
        return SourceStackTraceBailoutException.create(cause, message, elements);
    }

    /**
     * Gets an approximate source code location for a node if possible.
     *
     * @return a file name and source line number in stack trace format (e.g. "String.java:32") if
     *         an approximate source location is found, null otherwise
     */
    public static String approxSourceLocation(Node node) {
        StackTraceElement[] stackTraceElements = approxSourceStackTraceElement(node);
        if (stackTraceElements != null && stackTraceElements.length > 0) {
            StackTraceElement top = stackTraceElements[0];
            if (top.getFileName() != null && top.getLineNumber() >= 0) {
                return top.getFileName() + ":" + top.getLineNumber();
            }
        }
        return null;
    }

    /**
     * Returns a string representation of the given collection of objects.
     *
     * @param objects The {@link Iterable} that will be used to iterate over the objects.
     * @return A string of the format "[a, b, ...]".
     */
    public static String toString(Iterable<?> objects) {
        StringBuilder str = new StringBuilder();
        str.append("[");
        for (Object o : objects) {
            str.append(o).append(", ");
        }
        if (str.length() > 1) {
            str.setLength(str.length() - 2);
        }
        str.append("]");
        return str.toString();
    }

    /**
     * Gets the original value by iterating through all {@link ValueProxy ValueProxies}.
     *
     * @param value The start value.
     * @return The first non-proxy value encountered.
     */
    public static ValueNode unproxify(ValueNode value) {
        ValueNode result = value;
        while (result instanceof ValueProxy) {
            result = ((ValueProxy) result).getOriginalNode();
        }
        return result;
    }

    /**
     * Looks for an {@link ArrayLengthProvider} while iterating through all {@link ValueProxy
     * ValueProxies}.
     *
     * @param value The start value.
     * @return The array length if one was found, or null otherwise.
     */
    public static ValueNode arrayLength(ValueNode value) {
        ValueNode current = value;
        do {
            if (current instanceof ArrayLengthProvider) {
                ValueNode length = ((ArrayLengthProvider) current).length();
                if (length != null) {
                    return length;
                }
            }
            if (current instanceof ValueProxy) {
                current = ((ValueProxy) current).getOriginalNode();
            } else {
                break;
            }
        } while (true);
        return null;
    }

    /**
     * Tries to find an original value of the given node by traversing through proxies and
     * unambiguous phis. Note that this method will perform an exhaustive search through phis. It is
     * intended to be used during graph building, when phi nodes aren't yet canonicalized.
     *
     * @param proxy The node whose original value should be determined.
     */
    public static ValueNode originalValue(ValueNode proxy) {
        ValueNode v = proxy;
        do {
            if (v instanceof LimitedValueProxy) {
                v = ((LimitedValueProxy) v).getOriginalNode();
            } else if (v instanceof PhiNode) {
                v = ((PhiNode) v).singleValue();
                if (v == PhiNode.MULTIPLE_VALUES) {
                    v = null;
                }
            } else {
                break;
            }
        } while (v != null);

        if (v == null) {
            v = new OriginalValueSearch(proxy).result;
        }
        return v;
    }

    public static boolean tryKillUnused(Node node) {
        if (node.isAlive() && isFloatingNode(node) && node.hasNoUsages()) {
            killWithUnusedFloatingInputs(node);
            return true;
        }
        return false;
    }

    /**
     * Exhaustive search for {@link GraphUtil#originalValue(ValueNode)} when a simple search fails.
     * This can happen in the presence of complicated phi/proxy/phi constructs.
     */
    static class OriginalValueSearch {
        ValueNode result;

        OriginalValueSearch(ValueNode proxy) {
            NodeWorkList worklist = proxy.graph().createNodeWorkList();
            worklist.add(proxy);
            for (Node node : worklist) {
                if (node instanceof LimitedValueProxy) {
                    ValueNode originalValue = ((LimitedValueProxy) node).getOriginalNode();
                    if (!process(originalValue, worklist)) {
                        return;
                    }
                } else if (node instanceof PhiNode) {
                    for (Node value : ((PhiNode) node).values()) {
                        if (!process((ValueNode) value, worklist)) {
                            return;
                        }
                    }
                } else {
                    if (!process((ValueNode) node, null)) {
                        return;
                    }
                }
            }
        }

        /**
         * Process a node as part of this search.
         *
         * @param node the next node encountered in the search
         * @param worklist if non-null, {@code node} will be added to this list. Otherwise,
         *            {@code node} is treated as a candidate result.
         * @return true if the search should continue, false if a definitive {@link #result} has
         *         been found
         */
        private boolean process(ValueNode node, NodeWorkList worklist) {
            if (node.isAlive()) {
                if (worklist == null) {
                    if (result == null) {
                        // Initial candidate result: continue search
                        result = node;
                    } else if (result != node) {
                        // Conflicts with existing candidate: stop search with null result
                        result = null;
                        return false;
                    }
                } else {
                    worklist.add(node);
                }
            }
            return true;
        }
    }

    /**
     * Returns an iterator that will return the given node followed by all its predecessors, up
     * until the point where {@link Node#predecessor()} returns null.
     *
     * @param start the node at which to start iterating
     */
    public static NodeIterable<FixedNode> predecessorIterable(final FixedNode start) {
        return new NodeIterable<FixedNode>() {
            @Override
            public Iterator<FixedNode> iterator() {
                return new Iterator<FixedNode>() {
                    public FixedNode current = start;

                    @Override
                    public boolean hasNext() {
                        return current != null;
                    }

                    @Override
                    public FixedNode next() {
                        try {
                            return current;
                        } finally {
                            current = (FixedNode) current.predecessor();
                        }
                    }
                };
            }
        };
    }

    private static final class DefaultSimplifierTool implements SimplifierTool {
        private final MetaAccessProvider metaAccess;
        private final ConstantReflectionProvider constantReflection;
        private final ConstantFieldProvider constantFieldProvider;
        private final boolean canonicalizeReads;
        private final Assumptions assumptions;
        private final LoweringProvider loweringProvider;

        DefaultSimplifierTool(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider, boolean canonicalizeReads,
                        Assumptions assumptions, LoweringProvider loweringProvider) {
            this.metaAccess = metaAccess;
            this.constantReflection = constantReflection;
            this.constantFieldProvider = constantFieldProvider;
            this.canonicalizeReads = canonicalizeReads;
            this.assumptions = assumptions;
            this.loweringProvider = loweringProvider;
        }

        @Override
        public MetaAccessProvider getMetaAccess() {
            return metaAccess;
        }

        @Override
        public ConstantReflectionProvider getConstantReflection() {
            return constantReflection;
        }

        @Override
        public ConstantFieldProvider getConstantFieldProvider() {
            return constantFieldProvider;
        }

        @Override
        public boolean canonicalizeReads() {
            return canonicalizeReads;
        }

        @Override
        public boolean allUsagesAvailable() {
            return true;
        }

        @Override
        public void deleteBranch(Node branch) {
            branch.predecessor().replaceFirstSuccessor(branch, null);
            GraphUtil.killCFG(branch, this);
        }

        @Override
        public void removeIfUnused(Node node) {
            GraphUtil.tryKillUnused(node);
        }

        @Override
        public void addToWorkList(Node node) {
        }

        @Override
        public void addToWorkList(Iterable<? extends Node> nodes) {
        }

        @Override
        public Assumptions getAssumptions() {
            return assumptions;
        }

        @Override
        public boolean supportSubwordCompare(int bits) {
            if (loweringProvider != null) {
                return loweringProvider.supportSubwordCompare(bits);
            } else {
                return false;
            }
        }
    }

    public static SimplifierTool getDefaultSimplifier(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider,
                    boolean canonicalizeReads, Assumptions assumptions) {
        return getDefaultSimplifier(metaAccess, constantReflection, constantFieldProvider, canonicalizeReads, assumptions, null);
    }

    public static SimplifierTool getDefaultSimplifier(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider,
                    boolean canonicalizeReads, Assumptions assumptions, LoweringProvider loweringProvider) {
        return new DefaultSimplifierTool(metaAccess, constantReflection, constantFieldProvider, canonicalizeReads, assumptions, loweringProvider);
    }

    public static Constant foldIfConstantAndRemove(ValueNode node, ValueNode constant) {
        assert node.inputs().contains(constant);
        if (constant.isConstant()) {
            node.replaceFirstInput(constant, null);
            Constant result = constant.asConstant();
            tryKillUnused(constant);
            return result;
        }
        return null;
    }
}
