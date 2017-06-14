/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common.inlining;

import static org.graalvm.compiler.core.common.GraalOptions.UseGraalInstrumentation;
import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.NullCheckException;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.core.common.util.Util;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.Fingerprint;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.internal.method.MethodMetricsImpl;
import org.graalvm.compiler.debug.internal.method.MethodMetricsInlineeScopeInfo;
import org.graalvm.compiler.graph.GraalGraphError;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Graph.DuplicationReplacement;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.NodeWorkList;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.GuardedValueNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.KillingBeginNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.debug.instrumentation.InstrumentationNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.MonitorExitNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.common.inlining.info.InlineInfo;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class InliningUtil {

    private static final String inliningDecisionsScopeString = "InliningDecisions";

    /**
     * Print a HotSpot-style inlining message to the console.
     */
    private static void printInlining(final InlineInfo info, final int inliningDepth, final boolean success, final String msg, final Object... args) {
        printInlining(info.methodAt(0), info.invoke(), inliningDepth, success, msg, args);
    }

    private static void printInlining(final ResolvedJavaMethod method, final Invoke invoke, final int inliningDepth, final boolean success, final String msg, final Object... args) {
        Util.printInlining(method, invoke.bci(), inliningDepth, success, msg, args);
    }

    public static void logInlinedMethod(InlineInfo info, int inliningDepth, boolean allowLogging, String msg, Object... args) {
        logInliningDecision(info, inliningDepth, allowLogging, true, msg, args);
    }

    public static void logNotInlinedMethod(InlineInfo info, int inliningDepth, String msg, Object... args) {
        logInliningDecision(info, inliningDepth, true, false, msg, args);
    }

    public static void logInliningDecision(InlineInfo info, int inliningDepth, boolean allowLogging, boolean success, String msg, final Object... args) {
        if (allowLogging) {
            printInlining(info, inliningDepth, success, msg, args);
            if (shouldLogInliningDecision()) {
                logInliningDecision(methodName(info), success, msg, args);
            }
        }
    }

    @SuppressWarnings("try")
    public static void logInliningDecision(final String msg, final Object... args) {
        try (Scope s = Debug.scope(inliningDecisionsScopeString)) {
            // Can't use log here since we are varargs
            if (Debug.isLogEnabled()) {
                Debug.logv(msg, args);
            }
        }
    }

    public static void logNotInlinedMethod(Invoke invoke, String msg) {
        if (shouldLogInliningDecision()) {
            String methodString = invoke.toString();
            if (invoke.callTarget() == null) {
                methodString += " callTarget=null";
            } else {
                String targetName = invoke.callTarget().targetName();
                if (!methodString.endsWith(targetName)) {
                    methodString += " " + targetName;
                }
            }
            logInliningDecision(methodString, false, msg, new Object[0]);
        }
    }

    public static void logNotInlined(Invoke invoke, int inliningDepth, ResolvedJavaMethod method, String msg) {
        logNotInlinedInvoke(invoke, inliningDepth, method, msg, new Object[0]);
    }

    public static void logNotInlinedInvoke(Invoke invoke, int inliningDepth, ResolvedJavaMethod method, String msg, Object... args) {
        printInlining(method, invoke, inliningDepth, false, msg, args);
        if (shouldLogInliningDecision()) {
            String methodString = methodName(method, invoke);
            logInliningDecision(methodString, false, msg, args);
        }
    }

    private static void logInliningDecision(final String methodString, final boolean success, final String msg, final Object... args) {
        String inliningMsg = "inlining " + methodString + ": " + msg;
        if (!success) {
            inliningMsg = "not " + inliningMsg;
        }
        logInliningDecision(inliningMsg, args);
    }

    @SuppressWarnings("try")
    public static boolean shouldLogInliningDecision() {
        try (Scope s = Debug.scope(inliningDecisionsScopeString)) {
            return Debug.isLogEnabled();
        }
    }

    private static String methodName(ResolvedJavaMethod method, Invoke invoke) {
        if (invoke != null && invoke.stateAfter() != null) {
            return methodName(invoke.stateAfter(), invoke.bci()) + ": " + method.format("%H.%n(%p):%r") + " (" + method.getCodeSize() + " bytes)";
        } else {
            return method.format("%H.%n(%p):%r") + " (" + method.getCodeSize() + " bytes)";
        }
    }

    private static String methodName(InlineInfo info) {
        if (info == null) {
            return "null";
        } else if (info.invoke() != null && info.invoke().stateAfter() != null) {
            return methodName(info.invoke().stateAfter(), info.invoke().bci()) + ": " + info.toString();
        } else {
            return info.toString();
        }
    }

    private static String methodName(FrameState frameState, int bci) {
        StringBuilder sb = new StringBuilder();
        if (frameState.outerFrameState() != null) {
            sb.append(methodName(frameState.outerFrameState(), frameState.outerFrameState().bci));
            sb.append("->");
        }
        sb.append(frameState.getMethod().format("%h.%n"));
        sb.append("@").append(bci);
        return sb.toString();
    }

    public static void replaceInvokeCallTarget(Invoke invoke, StructuredGraph graph, InvokeKind invokeKind, ResolvedJavaMethod targetMethod) {
        MethodCallTargetNode oldCallTarget = (MethodCallTargetNode) invoke.callTarget();
        MethodCallTargetNode newCallTarget = graph.add(new MethodCallTargetNode(invokeKind, targetMethod, oldCallTarget.arguments().toArray(new ValueNode[0]), oldCallTarget.returnStamp(),
                        oldCallTarget.getProfile()));
        invoke.asNode().replaceFirstInput(oldCallTarget, newCallTarget);
    }

    public static GuardedValueNode createAnchoredReceiver(StructuredGraph graph, GuardingNode anchor, ResolvedJavaType commonType, ValueNode receiver, boolean exact) {
        return createAnchoredReceiver(graph, anchor, receiver,
                        exact ? StampFactory.objectNonNull(TypeReference.createExactTrusted(commonType)) : StampFactory.objectNonNull(TypeReference.createTrusted(graph.getAssumptions(), commonType)));
    }

    private static GuardedValueNode createAnchoredReceiver(StructuredGraph graph, GuardingNode anchor, ValueNode receiver, Stamp stamp) {
        // to avoid that floating reads on receiver fields float above the type check
        return graph.unique(new GuardedValueNode(receiver, anchor, stamp));
    }

    /**
     * @return null iff the check succeeds, otherwise a (non-null) descriptive message.
     */
    public static String checkInvokeConditions(Invoke invoke) {
        if (invoke.predecessor() == null || !invoke.asNode().isAlive()) {
            return "the invoke is dead code";
        }
        if (!(invoke.callTarget() instanceof MethodCallTargetNode)) {
            return "the invoke has already been lowered, or has been created as a low-level node";
        }
        MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
        if (callTarget.targetMethod() == null) {
            return "target method is null";
        }
        assert invoke.stateAfter() != null : invoke;
        if (!invoke.useForInlining()) {
            return "the invoke is marked to be not used for inlining";
        }
        ValueNode receiver = callTarget.receiver();
        if (receiver != null && receiver.isConstant() && receiver.isNullConstant()) {
            return "receiver is null";
        }
        return null;
    }

    /**
     * Performs an actual inlining, thereby replacing the given invoke with the given inlineGraph.
     *
     * @param invoke the invoke that will be replaced
     * @param inlineGraph the graph that the invoke will be replaced with
     * @param receiverNullCheck true if a null check needs to be generated for non-static inlinings,
     *            false if no such check is required
     * @param canonicalizedNodes if non-null then append to this list any nodes which should be
     *            canonicalized after inlining
     * @param inlineeMethod the actual method being inlined. Maybe be null for snippets.
     */
    @SuppressWarnings("try")
    public static Map<Node, Node> inline(Invoke invoke, StructuredGraph inlineGraph, boolean receiverNullCheck, List<Node> canonicalizedNodes, ResolvedJavaMethod inlineeMethod) {
        MethodMetricsInlineeScopeInfo m = MethodMetricsInlineeScopeInfo.create();
        try (Debug.Scope s = Debug.methodMetricsScope("InlineEnhancement", m, false)) {
            FixedNode invokeNode = invoke.asNode();
            StructuredGraph graph = invokeNode.graph();
            if (Fingerprint.ENABLED) {
                Fingerprint.submit("inlining %s into %s: %s", formatGraph(inlineGraph), formatGraph(invoke.asNode().graph()), inlineGraph.getNodes().snapshot());
            }
            final NodeInputList<ValueNode> parameters = invoke.callTarget().arguments();

            assert inlineGraph.getGuardsStage().ordinal() >= graph.getGuardsStage().ordinal();
            assert !invokeNode.graph().isAfterFloatingReadPhase() : "inline isn't handled correctly after floating reads phase";

            if (receiverNullCheck && !((MethodCallTargetNode) invoke.callTarget()).isStatic()) {
                nonNullReceiver(invoke);
            }

            ArrayList<Node> nodes = new ArrayList<>(inlineGraph.getNodes().count());
            ArrayList<ReturnNode> returnNodes = new ArrayList<>(4);
            UnwindNode unwindNode = null;
            final StartNode entryPointNode = inlineGraph.start();
            FixedNode firstCFGNode = entryPointNode.next();
            if (firstCFGNode == null) {
                throw new IllegalStateException("Inlined graph is in invalid state: " + inlineGraph);
            }
            for (Node node : inlineGraph.getNodes()) {
                if (node == entryPointNode || (node == entryPointNode.stateAfter() && node.usages().count() == 1) || node instanceof ParameterNode) {
                    // Do nothing.
                } else {
                    nodes.add(node);
                    if (node instanceof ReturnNode) {
                        returnNodes.add((ReturnNode) node);
                    } else if (node instanceof UnwindNode) {
                        assert unwindNode == null;
                        unwindNode = (UnwindNode) node;
                    }
                }
            }

            final AbstractBeginNode prevBegin = AbstractBeginNode.prevBegin(invokeNode);
            DuplicationReplacement localReplacement = new DuplicationReplacement() {

                @Override
                public Node replacement(Node node) {
                    if (node instanceof ParameterNode) {
                        return parameters.get(((ParameterNode) node).index());
                    } else if (node == entryPointNode) {
                        return prevBegin;
                    }
                    return node;
                }
            };

            assert invokeNode.successors().first() != null : invoke;
            assert invokeNode.predecessor() != null;

            Map<Node, Node> duplicates = graph.addDuplicates(nodes, inlineGraph, inlineGraph.getNodeCount(), localReplacement);

            FrameState stateAfter = invoke.stateAfter();
            assert stateAfter == null || stateAfter.isAlive();

            FrameState stateAtExceptionEdge = null;
            if (invoke instanceof InvokeWithExceptionNode) {
                InvokeWithExceptionNode invokeWithException = ((InvokeWithExceptionNode) invoke);
                if (unwindNode != null) {
                    ExceptionObjectNode obj = (ExceptionObjectNode) invokeWithException.exceptionEdge();
                    stateAtExceptionEdge = obj.stateAfter();
                }
            }

            updateSourcePositions(invoke, inlineGraph, duplicates);
            if (stateAfter != null) {
                processFrameStates(invoke, inlineGraph, duplicates, stateAtExceptionEdge, returnNodes.size() > 1);
                int callerLockDepth = stateAfter.nestedLockDepth();
                if (callerLockDepth != 0) {
                    for (MonitorIdNode original : inlineGraph.getNodes(MonitorIdNode.TYPE)) {
                        MonitorIdNode monitor = (MonitorIdNode) duplicates.get(original);
                        processMonitorId(invoke.stateAfter(), monitor);
                    }
                }
            } else {
                assert checkContainsOnlyInvalidOrAfterFrameState(duplicates);
            }

            firstCFGNode = (FixedNode) duplicates.get(firstCFGNode);
            for (int i = 0; i < returnNodes.size(); i++) {
                returnNodes.set(i, (ReturnNode) duplicates.get(returnNodes.get(i)));
            }
            if (unwindNode != null) {
                unwindNode = (UnwindNode) duplicates.get(unwindNode);
            }

            if (UseGraalInstrumentation.getValue()) {
                detachInstrumentation(invoke);
            }
            ValueNode returnValue = finishInlining(invoke, graph, firstCFGNode, returnNodes, unwindNode, inlineGraph.getAssumptions(), inlineGraph, canonicalizedNodes);
            if (canonicalizedNodes != null) {
                if (returnValue != null) {
                    for (Node usage : returnValue.usages()) {
                        canonicalizedNodes.add(usage);
                    }
                }
                for (ParameterNode parameter : inlineGraph.getNodes(ParameterNode.TYPE)) {
                    for (Node usage : parameter.usages()) {
                        Node duplicate = duplicates.get(usage);
                        if (duplicate != null && duplicate.isAlive()) {
                            canonicalizedNodes.add(duplicate);
                        }
                    }
                }
            }

            GraphUtil.killCFG(invokeNode);

            if (Debug.isMethodMeterEnabled() && m != null) {
                MethodMetricsImpl.recordInlinee(m.getRootMethod(), invoke.asNode().graph().method(), inlineeMethod);
            }
            return duplicates;
        }
    }

    public static ValueNode finishInlining(Invoke invoke, StructuredGraph graph, FixedNode firstNode, List<ReturnNode> returnNodes, UnwindNode unwindNode, Assumptions inlinedAssumptions,
                    StructuredGraph inlineGraph, List<Node> canonicalizedNodes) {
        FixedNode invokeNode = invoke.asNode();
        FrameState stateAfter = invoke.stateAfter();
        assert stateAfter == null || stateAfter.isAlive();

        invokeNode.replaceAtPredecessor(firstNode);

        if (invoke instanceof InvokeWithExceptionNode) {
            InvokeWithExceptionNode invokeWithException = ((InvokeWithExceptionNode) invoke);
            if (unwindNode != null && unwindNode.isAlive()) {
                assert unwindNode.predecessor() != null;
                assert invokeWithException.exceptionEdge().successors().count() == 1;
                ExceptionObjectNode obj = (ExceptionObjectNode) invokeWithException.exceptionEdge();
                obj.replaceAtUsages(unwindNode.exception());
                Node n = obj.next();
                obj.setNext(null);
                unwindNode.replaceAndDelete(n);

                obj.replaceAtPredecessor(null);
                obj.safeDelete();
            } else {
                invokeWithException.killExceptionEdge();
            }

            // get rid of memory kill
            AbstractBeginNode begin = invokeWithException.next();
            if (begin instanceof KillingBeginNode) {
                AbstractBeginNode newBegin = new BeginNode();
                graph.addAfterFixed(begin, graph.add(newBegin));
                begin.replaceAtUsages(newBegin);
                graph.removeFixed(begin);
            }
        } else {
            if (unwindNode != null && unwindNode.isAlive()) {
                DeoptimizeNode deoptimizeNode = graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.NotCompiledExceptionHandler));
                unwindNode.replaceAndDelete(deoptimizeNode);
            }
        }

        ValueNode returnValue;
        if (!returnNodes.isEmpty()) {
            FixedNode n = invoke.next();
            invoke.setNext(null);
            if (returnNodes.size() == 1) {
                ReturnNode returnNode = returnNodes.get(0);
                returnValue = returnNode.result();
                invokeNode.replaceAtUsages(returnValue);
                returnNode.replaceAndDelete(n);
            } else {
                AbstractMergeNode merge = graph.add(new MergeNode());
                merge.setStateAfter(stateAfter);
                returnValue = mergeReturns(merge, returnNodes, canonicalizedNodes);
                invokeNode.replaceAtUsages(returnValue);
                merge.setNext(n);
            }
        } else {
            returnValue = null;
            invokeNode.replaceAtUsages(null);
            GraphUtil.killCFG(invoke.next());
        }

        // Copy assumptions from inlinee to caller
        Assumptions assumptions = graph.getAssumptions();
        if (assumptions != null) {
            if (inlinedAssumptions != null) {
                assumptions.record(inlinedAssumptions);
            }
        } else {
            assert inlinedAssumptions == null : "cannot inline graph which makes assumptions into a graph that doesn't";
        }

        // Copy inlined methods from inlinee to caller
        graph.updateMethods(inlineGraph);

        // Update the set of accessed fields
        graph.updateFields(inlineGraph);

        if (inlineGraph.hasUnsafeAccess()) {
            graph.markUnsafeAccess();
        }
        assert inlineGraph.getSpeculationLog() == null : "Only the root graph should have a speculation log";

        return returnValue;
    }

    private static String formatGraph(StructuredGraph graph) {
        if (graph.method() == null) {
            return graph.name;
        }
        return graph.method().format("%H.%n(%p)");
    }

    @SuppressWarnings("try")
    private static void updateSourcePositions(Invoke invoke, StructuredGraph inlineGraph, Map<Node, Node> duplicates) {
        if (inlineGraph.mayHaveNodeSourcePosition() && invoke.stateAfter() != null) {
            if (invoke.asNode().getNodeSourcePosition() == null) {
                // Temporarily ignore the assert below.
                return;
            }

            JavaConstant constantReceiver = invoke.getInvokeKind().hasReceiver() ? invoke.getReceiver().asJavaConstant() : null;
            NodeSourcePosition invokePos = invoke.asNode().getNodeSourcePosition();
            assert invokePos != null : "missing source information";

            Map<NodeSourcePosition, NodeSourcePosition> posMap = new HashMap<>();
            for (Entry<Node, Node> entry : duplicates.entrySet()) {
                NodeSourcePosition pos = entry.getKey().getNodeSourcePosition();
                if (pos != null) {
                    NodeSourcePosition callerPos = pos.addCaller(constantReceiver, invokePos);
                    posMap.putIfAbsent(callerPos, callerPos);
                    entry.getValue().setNodeSourcePosition(posMap.get(callerPos));
                }
            }
        }
    }

    public static void processMonitorId(FrameState stateAfter, MonitorIdNode monitorIdNode) {
        if (stateAfter != null) {
            int callerLockDepth = stateAfter.nestedLockDepth();
            monitorIdNode.setLockDepth(monitorIdNode.getLockDepth() + callerLockDepth);
        }
    }

    protected static void processFrameStates(Invoke invoke, StructuredGraph inlineGraph, Map<Node, Node> duplicates, FrameState stateAtExceptionEdge, boolean alwaysDuplicateStateAfter) {
        FrameState stateAtReturn = invoke.stateAfter();
        FrameState outerFrameState = null;
        JavaKind invokeReturnKind = invoke.asNode().getStackKind();
        for (FrameState original : inlineGraph.getNodes(FrameState.TYPE)) {
            FrameState frameState = (FrameState) duplicates.get(original);
            if (frameState != null && frameState.isAlive()) {
                if (outerFrameState == null) {
                    outerFrameState = stateAtReturn.duplicateModifiedDuringCall(invoke.bci(), invokeReturnKind);
                }
                processFrameState(frameState, invoke, inlineGraph.method(), stateAtExceptionEdge, outerFrameState, alwaysDuplicateStateAfter, invoke.callTarget().targetMethod(),
                                invoke.callTarget().arguments());
            }
        }
    }

    public static FrameState processFrameState(FrameState frameState, Invoke invoke, ResolvedJavaMethod inlinedMethod, FrameState stateAtExceptionEdge, FrameState outerFrameState,
                    boolean alwaysDuplicateStateAfter, ResolvedJavaMethod invokeTargetMethod, List<ValueNode> invokeArgsList) {

        assert outerFrameState == null || !outerFrameState.isDeleted() : outerFrameState;
        FrameState stateAtReturn = invoke.stateAfter();
        JavaKind invokeReturnKind = invoke.asNode().getStackKind();

        if (frameState.bci == BytecodeFrame.AFTER_BCI) {
            FrameState stateAfterReturn = stateAtReturn;
            if (frameState.getCode() == null) {
                // This is a frame state for a side effect within an intrinsic
                // that was parsed for post-parse intrinsification
                for (Node usage : frameState.usages()) {
                    if (usage instanceof ForeignCallNode) {
                        // A foreign call inside an intrinsic needs to have
                        // the BCI of the invoke being intrinsified
                        ForeignCallNode foreign = (ForeignCallNode) usage;
                        foreign.setBci(invoke.bci());
                    }
                }
            }

            // pop return kind from invoke's stateAfter and replace with this frameState's return
            // value (top of stack)
            if (frameState.stackSize() > 0 && (alwaysDuplicateStateAfter || stateAfterReturn.stackAt(0) != frameState.stackAt(0))) {
                stateAfterReturn = stateAtReturn.duplicateModified(invokeReturnKind, invokeReturnKind, frameState.stackAt(0));
            }

            // Return value does no longer need to be limited by the monitor exit.
            for (MonitorExitNode n : frameState.usages().filter(MonitorExitNode.class)) {
                n.clearEscapedReturnValue();
            }

            frameState.replaceAndDelete(stateAfterReturn);
            return stateAfterReturn;
        } else if (stateAtExceptionEdge != null && isStateAfterException(frameState)) {
            // pop exception object from invoke's stateAfter and replace with this frameState's
            // exception object (top of stack)
            FrameState stateAfterException = stateAtExceptionEdge;
            if (frameState.stackSize() > 0 && stateAtExceptionEdge.stackAt(0) != frameState.stackAt(0)) {
                stateAfterException = stateAtExceptionEdge.duplicateModified(JavaKind.Object, JavaKind.Object, frameState.stackAt(0));
            }
            frameState.replaceAndDelete(stateAfterException);
            return stateAfterException;
        } else if (frameState.bci == BytecodeFrame.UNWIND_BCI || frameState.bci == BytecodeFrame.AFTER_EXCEPTION_BCI) {
            return handleMissingAfterExceptionFrameState(frameState);
        } else if (frameState.bci == BytecodeFrame.BEFORE_BCI) {
            // This is an intrinsic. Deoptimizing within an intrinsic
            // must re-execute the intrinsified invocation
            assert frameState.outerFrameState() == null;
            ValueNode[] invokeArgs = invokeArgsList.isEmpty() ? NO_ARGS : invokeArgsList.toArray(new ValueNode[invokeArgsList.size()]);
            FrameState stateBeforeCall = stateAtReturn.duplicateModifiedBeforeCall(invoke.bci(), invokeReturnKind, invokeTargetMethod.getSignature().toParameterKinds(!invokeTargetMethod.isStatic()),
                            invokeArgs);
            frameState.replaceAndDelete(stateBeforeCall);
            return stateBeforeCall;
        } else {
            // only handle the outermost frame states
            if (frameState.outerFrameState() == null) {
                assert checkInlineeFrameState(invoke, inlinedMethod, frameState);
                frameState.setOuterFrameState(outerFrameState);
            }
            return frameState;
        }
    }

    static boolean checkInlineeFrameState(Invoke invoke, ResolvedJavaMethod inlinedMethod, FrameState frameState) {
        assert frameState.bci != BytecodeFrame.AFTER_EXCEPTION_BCI : frameState;
        assert frameState.bci != BytecodeFrame.BEFORE_BCI : frameState;
        assert frameState.bci != BytecodeFrame.UNKNOWN_BCI : frameState;
        assert frameState.bci != BytecodeFrame.UNWIND_BCI : frameState;
        if (frameState.bci != BytecodeFrame.INVALID_FRAMESTATE_BCI) {
            ResolvedJavaMethod method = frameState.getMethod();
            if (method.equals(inlinedMethod)) {
                // Normal inlining expects all outermost inlinee frame states to
                // denote the inlinee method
            } else if (method.equals(invoke.callTarget().targetMethod())) {
                // This occurs when an intrinsic calls back to the original
                // method to handle a slow path. During parsing of such a
                // partial intrinsic, these calls are given frame states
                // that exclude the outer frame state denoting a position
                // in the intrinsic code.
                assert inlinedMethod.getAnnotation(
                                MethodSubstitution.class) != null : "expected an intrinsic when inlinee frame state matches method of call target but does not match the method of the inlinee graph: " +
                                                frameState;
            } else if (method.getName().equals(inlinedMethod.getName())) {
                // This can happen for method substitutions.
            } else {
                throw new AssertionError(String.format("inlinedMethod=%s frameState.method=%s frameState=%s invoke.method=%s", inlinedMethod, method, frameState,
                                invoke.callTarget().targetMethod()));
            }
        }
        return true;
    }

    private static final ValueNode[] NO_ARGS = {};

    private static boolean isStateAfterException(FrameState frameState) {
        return frameState.bci == BytecodeFrame.AFTER_EXCEPTION_BCI || (frameState.bci == BytecodeFrame.UNWIND_BCI && !frameState.getMethod().isSynchronized());
    }

    public static FrameState handleMissingAfterExceptionFrameState(FrameState nonReplaceableFrameState) {
        Graph graph = nonReplaceableFrameState.graph();
        NodeWorkList workList = graph.createNodeWorkList();
        workList.add(nonReplaceableFrameState);
        for (Node node : workList) {
            FrameState fs = (FrameState) node;
            for (Node usage : fs.usages().snapshot()) {
                if (!usage.isAlive()) {
                    continue;
                }
                if (usage instanceof FrameState) {
                    workList.add(usage);
                } else {
                    StateSplit stateSplit = (StateSplit) usage;
                    FixedNode fixedStateSplit = stateSplit.asNode();
                    if (fixedStateSplit instanceof AbstractMergeNode) {
                        AbstractMergeNode merge = (AbstractMergeNode) fixedStateSplit;
                        while (merge.isAlive()) {
                            AbstractEndNode end = merge.forwardEnds().first();
                            DeoptimizeNode deoptimizeNode = graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.NotCompiledExceptionHandler));
                            end.replaceAtPredecessor(deoptimizeNode);
                            GraphUtil.killCFG(end);
                        }
                    } else {
                        FixedNode deoptimizeNode = graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.NotCompiledExceptionHandler));
                        if (fixedStateSplit instanceof AbstractBeginNode) {
                            deoptimizeNode = BeginNode.begin(deoptimizeNode);
                        }
                        fixedStateSplit.replaceAtPredecessor(deoptimizeNode);
                        GraphUtil.killCFG(fixedStateSplit);
                    }
                }
            }
        }
        return nonReplaceableFrameState;
    }

    public static ValueNode mergeReturns(AbstractMergeNode merge, List<? extends ReturnNode> returnNodes, List<Node> canonicalizedNodes) {
        return mergeValueProducers(merge, returnNodes, canonicalizedNodes, returnNode -> returnNode.result());
    }

    public static <T extends ControlSinkNode> ValueNode mergeValueProducers(AbstractMergeNode merge, List<? extends T> valueProducers, List<Node> canonicalizedNodes,
                    Function<T, ValueNode> valueFunction) {
        ValueNode singleResult = null;
        PhiNode phiResult = null;
        for (T valueProducer : valueProducers) {
            ValueNode result = valueFunction.apply(valueProducer);
            if (result != null) {
                if (phiResult == null && (singleResult == null || singleResult == result)) {
                    /* Only one result value, so no need yet for a phi node. */
                    singleResult = result;
                } else if (phiResult == null) {
                    /* Found a second result value, so create phi node. */
                    phiResult = merge.graph().addWithoutUnique(new ValuePhiNode(result.stamp().unrestricted(), merge));
                    if (canonicalizedNodes != null) {
                        canonicalizedNodes.add(phiResult);
                    }
                    for (int i = 0; i < merge.forwardEndCount(); i++) {
                        phiResult.addInput(singleResult);
                    }
                    phiResult.addInput(result);

                } else {
                    /* Multiple return values, just add to existing phi node. */
                    phiResult.addInput(result);
                }
            }

            // create and wire up a new EndNode
            EndNode endNode = merge.graph().add(new EndNode());
            merge.addForwardEnd(endNode);
            valueProducer.replaceAndDelete(endNode);
        }

        if (phiResult != null) {
            assert phiResult.verify();
            phiResult.inferStamp();
            return phiResult;
        } else {
            return singleResult;
        }
    }

    private static boolean checkContainsOnlyInvalidOrAfterFrameState(Map<Node, Node> duplicates) {
        for (Node node : duplicates.values()) {
            if (node instanceof FrameState) {
                FrameState frameState = (FrameState) node;
                assert frameState.bci == BytecodeFrame.AFTER_BCI || frameState.bci == BytecodeFrame.INVALID_FRAMESTATE_BCI : node.toString(Verbosity.Debugger);
            }
        }
        return true;
    }

    /**
     * Gets the receiver for an invoke, adding a guard if necessary to ensure it is non-null, and
     * ensuring that the resulting type is compatible with the method being invoked.
     */
    public static ValueNode nonNullReceiver(Invoke invoke) {
        MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
        assert !callTarget.isStatic() : callTarget.targetMethod();
        StructuredGraph graph = callTarget.graph();
        ValueNode firstParam = callTarget.arguments().get(0);
        if (firstParam.getStackKind() == JavaKind.Object) {
            Stamp paramStamp = firstParam.stamp();
            Stamp stamp = paramStamp.join(StampFactory.objectNonNull(TypeReference.create(graph.getAssumptions(), callTarget.targetMethod().getDeclaringClass())));
            if (!StampTool.isPointerNonNull(firstParam)) {
                LogicNode condition = graph.unique(IsNullNode.create(firstParam));
                FixedGuardNode fixedGuard = graph.add(new FixedGuardNode(condition, NullCheckException, InvalidateReprofile, true));
                PiNode nonNullReceiver = graph.unique(new PiNode(firstParam, stamp, fixedGuard));
                graph.addBeforeFixed(invoke.asNode(), fixedGuard);
                callTarget.replaceFirstInput(firstParam, nonNullReceiver);
                return nonNullReceiver;
            }
            if (!stamp.equals(paramStamp)) {
                PiNode cast = graph.unique(new PiNode(firstParam, stamp));
                callTarget.replaceFirstInput(firstParam, cast);
                return cast;
            }
        }
        return firstParam;
    }

    public static boolean canIntrinsify(Replacements replacements, ResolvedJavaMethod target, int invokeBci) {
        return replacements.hasSubstitution(target, invokeBci);
    }

    public static StructuredGraph getIntrinsicGraph(Replacements replacements, ResolvedJavaMethod target, int invokeBci) {
        return replacements.getSubstitution(target, invokeBci);
    }

    public static FixedWithNextNode inlineMacroNode(Invoke invoke, ResolvedJavaMethod concrete, Class<? extends FixedWithNextNode> macroNodeClass) throws GraalError {
        StructuredGraph graph = invoke.asNode().graph();
        if (!concrete.equals(((MethodCallTargetNode) invoke.callTarget()).targetMethod())) {
            assert ((MethodCallTargetNode) invoke.callTarget()).invokeKind().hasReceiver();
            InliningUtil.replaceInvokeCallTarget(invoke, graph, InvokeKind.Special, concrete);
        }

        FixedWithNextNode macroNode = createMacroNodeInstance(macroNodeClass, invoke);

        CallTargetNode callTarget = invoke.callTarget();
        if (invoke instanceof InvokeNode) {
            graph.replaceFixedWithFixed((InvokeNode) invoke, graph.add(macroNode));
        } else {
            InvokeWithExceptionNode invokeWithException = (InvokeWithExceptionNode) invoke;
            invokeWithException.killExceptionEdge();
            graph.replaceSplitWithFixed(invokeWithException, graph.add(macroNode), invokeWithException.next());
        }
        GraphUtil.killWithUnusedFloatingInputs(callTarget);
        return macroNode;
    }

    private static FixedWithNextNode createMacroNodeInstance(Class<? extends FixedWithNextNode> macroNodeClass, Invoke invoke) throws GraalError {
        try {
            Constructor<?> cons = macroNodeClass.getDeclaredConstructor(Invoke.class);
            return (FixedWithNextNode) cons.newInstance(invoke);
        } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException e) {
            throw new GraalGraphError(e).addContext(invoke.asNode()).addContext("macroSubstitution", macroNodeClass);
        }
    }

    /**
     * This method exclude InstrumentationNode from inlining heuristics.
     */
    public static int getNodeCount(StructuredGraph graph) {
        if (UseGraalInstrumentation.getValue()) {
            return graph.getNodeCount() - graph.getNodes().filter(InstrumentationNode.class).count();
        } else {
            return graph.getNodeCount();
        }
    }

    /**
     * This method detach the instrumentation attached to the given Invoke. It is called when the
     * given Invoke is inlined.
     */
    public static void detachInstrumentation(Invoke invoke) {
        FixedNode invokeNode = invoke.asNode();
        for (InstrumentationNode instrumentation : invokeNode.usages().filter(InstrumentationNode.class).snapshot()) {
            if (instrumentation.getTarget() == invoke) {
                instrumentation.replaceFirstInput(instrumentation.getTarget(), null);
            }
        }
    }

}
