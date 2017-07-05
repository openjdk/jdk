/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.graph.Graph.Options.VerifyGraalGraphEdges;
import static org.graalvm.compiler.nodes.util.GraphUtil.Options.VerifyKillCFGUnusedNodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.code.SourceStackTraceBailoutException;
import org.graalvm.compiler.core.common.CollectionsFactory;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeWorkList;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
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
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider;
import org.graalvm.compiler.nodes.spi.LimitedValueProxy;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.ValueProxy;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValue;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class GraphUtil {

    public static class Options {
        @Option(help = "Verify that there are no new unused nodes when performing killCFG", type = OptionType.Debug)//
        public static final OptionValue<Boolean> VerifyKillCFGUnusedNodes = new OptionValue<>(false);
    }

    @SuppressWarnings("try")
    public static void killCFG(FixedNode node, SimplifierTool tool) {
        try (Debug.Scope scope = Debug.scope("KillCFG", node)) {
            Set<Node> unusedNodes = null;
            Set<Node> unsafeNodes = null;
            Graph.NodeEventScope nodeEventScope = null;
            if (VerifyGraalGraphEdges.getValue()) {
                unsafeNodes = collectUnsafeNodes(node.graph());
            }
            if (VerifyKillCFGUnusedNodes.getValue()) {
                Set<Node> collectedUnusedNodes = unusedNodes = CollectionsFactory.newSet();
                nodeEventScope = node.graph().trackNodeEvents(new Graph.NodeEventListener() {
                    @Override
                    public void event(Graph.NodeEvent e, Node n) {
                        if (e == Graph.NodeEvent.ZERO_USAGES && isFloatingNode(n)) {
                            collectedUnusedNodes.add(n);
                        }
                    }
                });
            }
            Debug.dump(Debug.VERY_DETAILED_LOG_LEVEL, node.graph(), "Before killCFG %s", node);
            NodeWorkList worklist = killCFG(node, tool, null);
            if (worklist != null) {
                for (Node n : worklist) {
                    killCFG(n, tool, worklist);
                }
            }
            if (VerifyGraalGraphEdges.getValue()) {
                Set<Node> newUnsafeNodes = collectUnsafeNodes(node.graph());
                newUnsafeNodes.removeAll(unsafeNodes);
                assert newUnsafeNodes.isEmpty() : "New unsafe nodes: " + newUnsafeNodes;
            }
            if (VerifyKillCFGUnusedNodes.getValue()) {
                nodeEventScope.close();
                unusedNodes.removeIf(n -> n.isDeleted());
                assert unusedNodes.isEmpty() : "New unused nodes: " + unusedNodes;
            }
        } catch (Throwable t) {
            throw Debug.handle(t);
        }
    }

    /**
     * Collects all node in the graph which have non-optional inputs that are null.
     */
    private static Set<Node> collectUnsafeNodes(Graph graph) {
        Set<Node> unsafeNodes = CollectionsFactory.newSet();
        for (Node n : graph.getNodes()) {
            for (Position pos : n.inputPositions()) {
                Node input = pos.get(n);
                if (input == null) {
                    if (!pos.isInputOptional()) {
                        unsafeNodes.add(n);
                    }
                }
            }
        }
        return unsafeNodes;
    }

    private static NodeWorkList killCFG(Node node, SimplifierTool tool, NodeWorkList worklist) {
        NodeWorkList newWorklist = worklist;
        if (node instanceof FixedNode) {
            newWorklist = killCFGLinear((FixedNode) node, newWorklist, tool);
        } else {
            newWorklist = propagateKill(node, newWorklist);
            Debug.dump(Debug.VERY_DETAILED_LOG_LEVEL, node.graph(), "killCFG (Floating) %s", node);
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
                newWorklist = killEnd(end, newWorklist, tool);
            } else if (current instanceof FixedWithNextNode) {
                // Node guaranteed to have a single successor
                FixedWithNextNode fixedWithNext = (FixedWithNextNode) current;
                assert fixedWithNext.successors().count() == 1 || fixedWithNext.successors().count() == 0;
                assert fixedWithNext.successors().first() == fixedWithNext.next();
                next = fixedWithNext.next();
            } else {
                /*
                 * We do not take a successor snapshot because this iterator supports concurrent
                 * modifications as long as they do not change the size of the successor list. Not
                 * taking a snapshot allows us to see modifications to other branches that may
                 * happen while processing one branch.
                 */
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
            newWorklist = propagateKill(current, newWorklist);
            Debug.dump(Debug.VERY_DETAILED_LOG_LEVEL, current.graph(), "killCFGLinear %s", current);
            current = next;
        }
        Debug.dump(Debug.DETAILED_LOG_LEVEL, in.graph(), "killCFGLinear %s", in);
        return newWorklist;
    }

    public static void killCFG(FixedNode node) {
        killCFG(node, null);
    }

    /**
     * Node type used temporarily while deleting loops.
     *
     * It is used as replacement for the loop {@link PhiNode PhiNodes} in order to break data-flow
     * cycles before deleting the loop. The control-flow of the whole loop is killed before killing
     * the poison node if they are still alive.
     */
    @NodeInfo(allowedUsageTypes = InputType.Unchecked)
    private static final class PoisonNode extends FloatingNode {
        public static final NodeClass<PoisonNode> TYPE = NodeClass.create(PoisonNode.class);

        protected PoisonNode() {
            super(TYPE, StampFactory.forVoid());
        }
    }

    private static NodeWorkList killEnd(AbstractEndNode end, NodeWorkList worklist, SimplifierTool tool) {
        NodeWorkList newWorklist = worklist;
        AbstractMergeNode merge = end.merge();
        if (merge != null) {
            merge.removeEnd(end);
            StructuredGraph graph = end.graph();
            if (merge instanceof LoopBeginNode && merge.forwardEndCount() == 0) {
                // dead loop
                LoopBeginNode begin = (LoopBeginNode) merge;
                // disconnect and delete loop ends & loop exits
                for (LoopEndNode loopend : begin.loopEnds().snapshot()) {
                    loopend.predecessor().replaceFirstSuccessor(loopend, null);
                    loopend.safeDelete();
                }
                // clean unused proxies to avoid creating new unused nodes
                for (LoopExitNode exit : begin.loopExits()) {
                    for (ProxyNode vpn : exit.proxies().snapshot()) {
                        tryKillUnused(vpn);
                    }
                }
                begin.removeExits();
                PoisonNode poison = null;
                if (merge.phis().isNotEmpty()) {
                    poison = graph.unique(new PoisonNode());
                    for (PhiNode phi : merge.phis()) {
                        phi.replaceAtUsages(poison);
                    }
                    for (PhiNode phi : merge.phis().snapshot()) {
                        killWithUnusedFloatingInputs(phi);
                    }
                }
                FixedNode loopBody = begin.next();
                Debug.dump(Debug.VERY_DETAILED_LOG_LEVEL, end.graph(), "killEnd (Loop) %s after initial loop cleanup", end);
                if (loopBody != null) {
                    // for small infinite loops, the body may already be killed while killing the
                    // LoopEnds
                    newWorklist = killCFG(loopBody, tool, worklist);
                }
                FrameState frameState = begin.stateAfter();
                begin.safeDelete();
                if (frameState != null) {
                    tryKillUnused(frameState);
                }
                if (poison != null && poison.isAlive()) {
                    if (newWorklist == null) {
                        newWorklist = graph.createNodeWorkList();
                    }
                    // drain the worklist to finish the loop before adding the poison
                    for (Node n : newWorklist) {
                        killCFG(n, tool, newWorklist);
                    }
                    if (poison.isAlive()) {
                        newWorklist.add(poison);
                    }
                }
            } else if (merge instanceof LoopBeginNode && ((LoopBeginNode) merge).loopEnds().isEmpty()) {
                // not a loop anymore
                if (tool != null) {
                    for (PhiNode phi : merge.phis()) {
                        tool.addToWorkList(phi.usages());
                    }
                }
                graph.reduceDegenerateLoopBegin((LoopBeginNode) merge);
            } else if (merge.phiPredecessorCount() == 1) {
                // not a merge anymore
                if (tool != null) {
                    for (PhiNode phi : merge.phis()) {
                        tool.addToWorkList(phi.usages());
                    }
                }
                graph.reduceTrivialMerge(merge);
            }
        }
        return newWorklist;
    }

    public static boolean isFloatingNode(Node n) {
        return !(n instanceof FixedNode);
    }

    private static NodeWorkList propagateKill(Node node, NodeWorkList workList) {
        NodeWorkList newWorkList = workList;
        if (node != null && node.isAlive()) {
            for (Node usage : node.usages().snapshot()) {
                assert usage.isAlive();
                if (isFloatingNode(usage)) {
                    boolean addUsage = false;
                    if (usage instanceof PhiNode) {
                        PhiNode phi = (PhiNode) usage;
                        assert phi.merge() != null;
                        if (phi.merge() == node) {
                            // we reach the phi directly through he merge, queue it.
                            addUsage = true;
                        } else {
                            // we reach it though a value
                            assert phi.values().contains(node);
                            // let that be handled when we reach the corresponding End node
                        }
                    } else {
                        addUsage = true;
                    }
                    if (addUsage) {
                        if (newWorkList == null) {
                            newWorkList = node.graph().createNodeWorkList();
                        }
                        newWorkList.add(usage);
                    }
                }
                usage.replaceFirstInput(node, null);
            }
            killWithUnusedFloatingInputs(node);
        }
        return newWorkList;
    }

    private static boolean checkKill(Node node) {
        node.assertTrue(node.isAlive(), "must be alive");
        node.assertTrue(node.hasNoUsages(), "cannot kill node %s because of usages: %s", node, node.usages());
        node.assertTrue(node.predecessor() == null, "cannot kill node %s because of predecessor: %s", node, node.predecessor());
        return true;
    }

    public static void killWithUnusedFloatingInputs(Node node) {
        assert checkKill(node);
        node.markDeleted();
        outer: for (Node in : node.inputs()) {
            if (in.isAlive()) {
                in.removeUsage(node);
                if (in.hasNoUsages()) {
                    node.maybeNotifyZeroUsages(in);
                }
                if (isFloatingNode(in)) {
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

    /**
     * Removes all nodes created after the {@code mark}, assuming no "old" nodes point to "new"
     * nodes.
     */
    public static void removeNewNodes(Graph graph, Graph.Mark mark) {
        assert checkNoOldToNewEdges(graph, mark);
        for (Node n : graph.getNewNodes(mark)) {
            n.markDeleted();
            for (Node in : n.inputs()) {
                in.removeUsage(n);
            }
        }
    }

    private static boolean checkNoOldToNewEdges(Graph graph, Graph.Mark mark) {
        for (Node old : graph.getNodes()) {
            if (graph.isNew(mark, old)) {
                break;
            }
            for (Node n : old.successors()) {
                assert !graph.isNew(mark, n) : old + " -> " + n;
            }
            for (Node n : old.inputs()) {
                assert !graph.isNew(mark, n) : old + " -> " + n;
            }
        }
        return true;
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
            FixedNode fixedBranch = (FixedNode) branch;
            fixedBranch.predecessor().replaceFirstSuccessor(fixedBranch, null);
            GraphUtil.killCFG(fixedBranch, this);
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
