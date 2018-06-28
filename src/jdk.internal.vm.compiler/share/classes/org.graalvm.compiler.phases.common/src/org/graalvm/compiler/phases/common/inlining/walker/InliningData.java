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


package org.graalvm.compiler.phases.common.inlining.walker;

import static org.graalvm.compiler.core.common.GraalOptions.Intrinsify;
import static org.graalvm.compiler.core.common.GraalOptions.MaximumRecursiveInlining;
import static org.graalvm.compiler.core.common.GraalOptions.MegamorphicInliningMinMethodProbability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.common.inlining.info.AssumptionInlineInfo;
import org.graalvm.compiler.phases.common.inlining.info.ExactInlineInfo;
import org.graalvm.compiler.phases.common.inlining.info.InlineInfo;
import org.graalvm.compiler.phases.common.inlining.info.MultiTypeGuardInlineInfo;
import org.graalvm.compiler.phases.common.inlining.info.TypeGuardInlineInfo;
import org.graalvm.compiler.phases.common.inlining.info.elem.Inlineable;
import org.graalvm.compiler.phases.common.inlining.info.elem.InlineableGraph;
import org.graalvm.compiler.phases.common.inlining.policy.InliningPolicy;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * <p>
 * The space of inlining decisions is explored depth-first with the help of a stack realized by
 * {@link InliningData}. At any point in time, the topmost element of that stack consists of:
 * <ul>
 * <li>the callsite under consideration is tracked as a {@link MethodInvocation}.</li>
 * <li>one or more {@link CallsiteHolder}s, all of them associated to the callsite above. Why more
 * than one? Depending on the type-profile for the receiver more than one concrete method may be
 * feasible target.</li>
 * </ul>
 * </p>
 *
 * <p>
 * The bottom element in the stack consists of:
 * <ul>
 * <li>a single {@link MethodInvocation} (the
 * {@link org.graalvm.compiler.phases.common.inlining.walker.MethodInvocation#isRoot root} one, ie
 * the unknown caller of the root graph)</li>
 * <li>a single {@link CallsiteHolder} (the root one, for the method on which inlining was called)
 * </li>
 * </ul>
 * </p>
 *
 * @see #moveForward()
 */
public class InliningData {

    // Counters
    private static final CounterKey counterInliningPerformed = DebugContext.counter("InliningPerformed");
    private static final CounterKey counterInliningRuns = DebugContext.counter("InliningRuns");
    private static final CounterKey counterInliningConsidered = DebugContext.counter("InliningConsidered");

    /**
     * Call hierarchy from outer most call (i.e., compilation unit) to inner most callee.
     */
    private final ArrayDeque<CallsiteHolder> graphQueue = new ArrayDeque<>();
    private final ArrayDeque<MethodInvocation> invocationQueue = new ArrayDeque<>();

    private final HighTierContext context;
    private final int maxMethodPerInlining;
    private final CanonicalizerPhase canonicalizer;
    private final InliningPolicy inliningPolicy;
    private final StructuredGraph rootGraph;
    private final DebugContext debug;

    private int maxGraphs;

    public InliningData(StructuredGraph rootGraph, HighTierContext context, int maxMethodPerInlining, CanonicalizerPhase canonicalizer, InliningPolicy inliningPolicy, LinkedList<Invoke> rootInvokes) {
        assert rootGraph != null;
        this.context = context;
        this.maxMethodPerInlining = maxMethodPerInlining;
        this.canonicalizer = canonicalizer;
        this.inliningPolicy = inliningPolicy;
        this.maxGraphs = 1;
        this.rootGraph = rootGraph;
        this.debug = rootGraph.getDebug();

        invocationQueue.push(new MethodInvocation(null, 1.0, 1.0, null));
        graphQueue.push(new CallsiteHolderExplorable(rootGraph, 1.0, 1.0, null, rootInvokes));
    }

    public static boolean isFreshInstantiation(ValueNode arg) {
        return (arg instanceof AbstractNewObjectNode) || (arg instanceof AllocatedObjectNode) || (arg instanceof VirtualObjectNode);
    }

    private String checkTargetConditionsHelper(ResolvedJavaMethod method, int invokeBci) {
        OptionValues options = rootGraph.getOptions();
        if (method == null) {
            return "the method is not resolved";
        } else if (method.isNative() && (!Intrinsify.getValue(options) || !InliningUtil.canIntrinsify(context.getReplacements(), method, invokeBci))) {
            return "it is a non-intrinsic native method";
        } else if (method.isAbstract()) {
            return "it is an abstract method";
        } else if (!method.getDeclaringClass().isInitialized()) {
            return "the method's class is not initialized";
        } else if (!method.canBeInlined()) {
            return "it is marked non-inlinable";
        } else if (countRecursiveInlining(method) > MaximumRecursiveInlining.getValue(options)) {
            return "it exceeds the maximum recursive inlining depth";
        } else {
            if (new OptimisticOptimizations(rootGraph.getProfilingInfo(method), options).lessOptimisticThan(context.getOptimisticOptimizations())) {
                return "the callee uses less optimistic optimizations than caller";
            } else {
                return null;
            }
        }
    }

    private boolean checkTargetConditions(Invoke invoke, ResolvedJavaMethod method) {
        final String failureMessage = checkTargetConditionsHelper(method, invoke.bci());
        if (failureMessage == null) {
            return true;
        } else {
            InliningUtil.traceNotInlinedMethod(invoke, inliningDepth(), method, failureMessage);
            invoke.asNode().graph().getInliningLog().addDecision(invoke, false, "InliningPhase", null, null, failureMessage);
            return false;
        }
    }

    /**
     * Determines if inlining is possible at the given invoke node.
     *
     * @param invoke the invoke that should be inlined
     * @return an instance of InlineInfo, or null if no inlining is possible at the given invoke
     */
    private InlineInfo getInlineInfo(Invoke invoke) {
        final String failureMessage = InliningUtil.checkInvokeConditions(invoke);
        if (failureMessage != null) {
            InliningUtil.logNotInlinedMethod(invoke, failureMessage);
            return null;
        }
        MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
        ResolvedJavaMethod targetMethod = callTarget.targetMethod();

        if (callTarget.invokeKind() == CallTargetNode.InvokeKind.Special || targetMethod.canBeStaticallyBound()) {
            return getExactInlineInfo(invoke, targetMethod);
        }

        assert callTarget.invokeKind().isIndirect();

        ResolvedJavaType holder = targetMethod.getDeclaringClass();
        if (!(callTarget.receiver().stamp(NodeView.DEFAULT) instanceof ObjectStamp)) {
            return null;
        }
        ObjectStamp receiverStamp = (ObjectStamp) callTarget.receiver().stamp(NodeView.DEFAULT);
        if (receiverStamp.alwaysNull()) {
            // Don't inline if receiver is known to be null
            return null;
        }
        ResolvedJavaType contextType = invoke.getContextType();
        if (receiverStamp.type() != null) {
            // the invoke target might be more specific than the holder (happens after inlining:
            // parameters lose their declared type...)
            ResolvedJavaType receiverType = receiverStamp.type();
            if (receiverType != null && holder.isAssignableFrom(receiverType)) {
                holder = receiverType;
                if (receiverStamp.isExactType()) {
                    assert targetMethod.getDeclaringClass().isAssignableFrom(holder) : holder + " subtype of " + targetMethod.getDeclaringClass() + " for " + targetMethod;
                    ResolvedJavaMethod resolvedMethod = holder.resolveConcreteMethod(targetMethod, contextType);
                    if (resolvedMethod != null) {
                        return getExactInlineInfo(invoke, resolvedMethod);
                    }
                }
            }
        }

        if (holder.isArray()) {
            // arrays can be treated as Objects
            ResolvedJavaMethod resolvedMethod = holder.resolveConcreteMethod(targetMethod, contextType);
            if (resolvedMethod != null) {
                return getExactInlineInfo(invoke, resolvedMethod);
            }
        }

        AssumptionResult<ResolvedJavaType> leafConcreteSubtype = holder.findLeafConcreteSubtype();
        if (leafConcreteSubtype != null) {
            ResolvedJavaMethod resolvedMethod = leafConcreteSubtype.getResult().resolveConcreteMethod(targetMethod, contextType);
            if (resolvedMethod != null) {
                if (leafConcreteSubtype.canRecordTo(callTarget.graph().getAssumptions())) {
                    return getAssumptionInlineInfo(invoke, resolvedMethod, leafConcreteSubtype);
                } else {
                    return getTypeCheckedAssumptionInfo(invoke, resolvedMethod, leafConcreteSubtype.getResult());
                }
            }
        }

        AssumptionResult<ResolvedJavaMethod> concrete = holder.findUniqueConcreteMethod(targetMethod);
        if (concrete != null && concrete.canRecordTo(callTarget.graph().getAssumptions())) {
            return getAssumptionInlineInfo(invoke, concrete.getResult(), concrete);
        }

        // type check based inlining
        return getTypeCheckedInlineInfo(invoke, targetMethod);
    }

    private InlineInfo getTypeCheckedAssumptionInfo(Invoke invoke, ResolvedJavaMethod method, ResolvedJavaType type) {
        if (!checkTargetConditions(invoke, method)) {
            return null;
        }
        return new TypeGuardInlineInfo(invoke, method, type);
    }

    private InlineInfo getTypeCheckedInlineInfo(Invoke invoke, ResolvedJavaMethod targetMethod) {
        JavaTypeProfile typeProfile = ((MethodCallTargetNode) invoke.callTarget()).getProfile();
        if (typeProfile == null) {
            InliningUtil.traceNotInlinedMethod(invoke, inliningDepth(), targetMethod, "no type profile exists");
            invoke.asNode().graph().getInliningLog().addDecision(invoke, false, "InliningPhase", null, null, "no type profile exists");
            return null;
        }

        JavaTypeProfile.ProfiledType[] ptypes = typeProfile.getTypes();
        if (ptypes == null || ptypes.length <= 0) {
            InliningUtil.traceNotInlinedMethod(invoke, inliningDepth(), targetMethod, "no types in profile");
            invoke.asNode().graph().getInliningLog().addDecision(invoke, false, "InliningPhase", null, null, "no types in profile");
            return null;
        }
        ResolvedJavaType contextType = invoke.getContextType();
        double notRecordedTypeProbability = typeProfile.getNotRecordedProbability();
        final OptimisticOptimizations optimisticOpts = context.getOptimisticOptimizations();
        OptionValues options = invoke.asNode().getOptions();
        if (ptypes.length == 1 && notRecordedTypeProbability == 0) {
            if (!optimisticOpts.inlineMonomorphicCalls(options)) {
                InliningUtil.traceNotInlinedMethod(invoke, inliningDepth(), targetMethod, "inlining monomorphic calls is disabled");
                invoke.asNode().graph().getInliningLog().addDecision(invoke, false, "InliningPhase", null, null, "inlining monomorphic calls is disabled");
                return null;
            }

            ResolvedJavaType type = ptypes[0].getType();
            assert type.isArray() || type.isConcrete();
            ResolvedJavaMethod concrete = type.resolveConcreteMethod(targetMethod, contextType);
            if (!checkTargetConditions(invoke, concrete)) {
                return null;
            }
            return new TypeGuardInlineInfo(invoke, concrete, type);
        } else {
            invoke.setPolymorphic(true);

            if (!optimisticOpts.inlinePolymorphicCalls(options) && notRecordedTypeProbability == 0) {
                InliningUtil.traceNotInlinedMethod(invoke, inliningDepth(), targetMethod, "inlining polymorphic calls is disabled (%d types)", ptypes.length);
                invoke.asNode().graph().getInliningLog().addDecision(invoke, false, "InliningPhase", null, null, "inlining polymorphic calls is disabled (%d types)", ptypes.length);
                return null;
            }
            if (!optimisticOpts.inlineMegamorphicCalls(options) && notRecordedTypeProbability > 0) {
                // due to filtering impossible types, notRecordedTypeProbability can be > 0 although
                // the number of types is lower than what can be recorded in a type profile
                InliningUtil.traceNotInlinedMethod(invoke, inliningDepth(), targetMethod, "inlining megamorphic calls is disabled (%d types, %f %% not recorded types)", ptypes.length,
                                notRecordedTypeProbability * 100);
                invoke.asNode().graph().getInliningLog().addDecision(invoke, false, "InliningPhase", null, null,
                                "inlining megamorphic calls is disabled (%d types, %f %% not recorded types)", ptypes.length, notRecordedTypeProbability);
                return null;
            }

            // Find unique methods and their probabilities.
            ArrayList<ResolvedJavaMethod> concreteMethods = new ArrayList<>();
            ArrayList<Double> concreteMethodsProbabilities = new ArrayList<>();
            for (int i = 0; i < ptypes.length; i++) {
                ResolvedJavaMethod concrete = ptypes[i].getType().resolveConcreteMethod(targetMethod, contextType);
                if (concrete == null) {
                    InliningUtil.traceNotInlinedMethod(invoke, inliningDepth(), targetMethod, "could not resolve method");
                    invoke.asNode().graph().getInliningLog().addDecision(invoke, false, "InliningPhase", null, null, "could not resolve method");
                    return null;
                }
                int index = concreteMethods.indexOf(concrete);
                double curProbability = ptypes[i].getProbability();
                if (index < 0) {
                    index = concreteMethods.size();
                    concreteMethods.add(concrete);
                    concreteMethodsProbabilities.add(curProbability);
                } else {
                    concreteMethodsProbabilities.set(index, concreteMethodsProbabilities.get(index) + curProbability);
                }
            }

            // Clear methods that fall below the threshold.
            if (notRecordedTypeProbability > 0) {
                ArrayList<ResolvedJavaMethod> newConcreteMethods = new ArrayList<>();
                ArrayList<Double> newConcreteMethodsProbabilities = new ArrayList<>();
                for (int i = 0; i < concreteMethods.size(); ++i) {
                    if (concreteMethodsProbabilities.get(i) >= MegamorphicInliningMinMethodProbability.getValue(options)) {
                        newConcreteMethods.add(concreteMethods.get(i));
                        newConcreteMethodsProbabilities.add(concreteMethodsProbabilities.get(i));
                    }
                }

                if (newConcreteMethods.isEmpty()) {
                    // No method left that is worth inlining.
                    InliningUtil.traceNotInlinedMethod(invoke, inliningDepth(), targetMethod, "no methods remaining after filtering less frequent methods (%d methods previously)",
                                    concreteMethods.size());
                    invoke.asNode().graph().getInliningLog().addDecision(invoke, false, "InliningPhase", null, null,
                                    "no methods remaining after filtering less frequent methods (%d methods previously)", concreteMethods.size());
                    return null;
                }

                concreteMethods = newConcreteMethods;
                concreteMethodsProbabilities = newConcreteMethodsProbabilities;
            }

            if (concreteMethods.size() > maxMethodPerInlining) {
                InliningUtil.traceNotInlinedMethod(invoke, inliningDepth(), targetMethod, "polymorphic call with more than %d target methods", maxMethodPerInlining);
                invoke.asNode().graph().getInliningLog().addDecision(invoke, false, "InliningPhase", null, null, "polymorphic call with more than %d target methods", maxMethodPerInlining);
                return null;
            }

            // Clean out types whose methods are no longer available.
            ArrayList<JavaTypeProfile.ProfiledType> usedTypes = new ArrayList<>();
            ArrayList<Integer> typesToConcretes = new ArrayList<>();
            for (JavaTypeProfile.ProfiledType type : ptypes) {
                ResolvedJavaMethod concrete = type.getType().resolveConcreteMethod(targetMethod, contextType);
                int index = concreteMethods.indexOf(concrete);
                if (index == -1) {
                    notRecordedTypeProbability += type.getProbability();
                } else {
                    assert type.getType().isArray() || !type.getType().isAbstract() : type + " " + concrete;
                    usedTypes.add(type);
                    typesToConcretes.add(index);
                }
            }

            if (usedTypes.isEmpty()) {
                // No type left that is worth checking for.
                InliningUtil.traceNotInlinedMethod(invoke, inliningDepth(), targetMethod, "no types remaining after filtering less frequent types (%d types previously)", ptypes.length);
                invoke.asNode().graph().getInliningLog().addDecision(invoke, false, "InliningPhase", null, null, "no types remaining after filtering less frequent types (%d types previously)",
                                ptypes.length);
                return null;
            }

            for (ResolvedJavaMethod concrete : concreteMethods) {
                if (!checkTargetConditions(invoke, concrete)) {
                    InliningUtil.traceNotInlinedMethod(invoke, inliningDepth(), targetMethod, "it is a polymorphic method call and at least one invoked method cannot be inlined");
                    invoke.asNode().graph().getInliningLog().addDecision(invoke, false, "InliningPhase", null, null,
                                    "it is a polymorphic method call and at least one invoked method cannot be inlined");
                    return null;
                }
            }
            return new MultiTypeGuardInlineInfo(invoke, concreteMethods, usedTypes, typesToConcretes, notRecordedTypeProbability);
        }
    }

    private InlineInfo getAssumptionInlineInfo(Invoke invoke, ResolvedJavaMethod concrete, AssumptionResult<?> takenAssumption) {
        assert concrete.isConcrete();
        if (checkTargetConditions(invoke, concrete)) {
            return new AssumptionInlineInfo(invoke, concrete, takenAssumption);
        }
        return null;
    }

    private InlineInfo getExactInlineInfo(Invoke invoke, ResolvedJavaMethod targetMethod) {
        assert targetMethod.isConcrete();
        if (checkTargetConditions(invoke, targetMethod)) {
            return new ExactInlineInfo(invoke, targetMethod);
        }
        return null;
    }

    @SuppressWarnings("try")
    private void doInline(CallsiteHolderExplorable callerCallsiteHolder, MethodInvocation calleeInvocation, String reason) {
        StructuredGraph callerGraph = callerCallsiteHolder.graph();
        InlineInfo calleeInfo = calleeInvocation.callee();
        try {
            try (DebugContext.Scope scope = debug.scope("doInline", callerGraph)) {
                EconomicSet<Node> canonicalizedNodes = EconomicSet.create(Equivalence.IDENTITY);
                canonicalizedNodes.addAll(calleeInfo.invoke().asNode().usages());
                EconomicSet<Node> parameterUsages = calleeInfo.inline(new Providers(context), reason);
                canonicalizedNodes.addAll(parameterUsages);
                counterInliningRuns.increment(debug);
                debug.dump(DebugContext.DETAILED_LEVEL, callerGraph, "after %s", calleeInfo);

                Graph.Mark markBeforeCanonicalization = callerGraph.getMark();

                canonicalizer.applyIncremental(callerGraph, context, canonicalizedNodes);

                // process invokes that are possibly created during canonicalization
                for (Node newNode : callerGraph.getNewNodes(markBeforeCanonicalization)) {
                    if (newNode instanceof Invoke) {
                        callerCallsiteHolder.pushInvoke((Invoke) newNode);
                    }
                }

                callerCallsiteHolder.computeProbabilities();

                counterInliningPerformed.increment(debug);
            }
        } catch (BailoutException bailout) {
            throw bailout;
        } catch (AssertionError | RuntimeException e) {
            throw new GraalError(e).addContext(calleeInfo.toString());
        } catch (GraalError e) {
            throw e.addContext(calleeInfo.toString());
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    /**
     *
     * This method attempts:
     * <ol>
     * <li>to inline at the callsite given by <code>calleeInvocation</code>, where that callsite
     * belongs to the {@link CallsiteHolderExplorable} at the top of the {@link #graphQueue}
     * maintained in this class.</li>
     * <li>otherwise, to devirtualize the callsite in question.</li>
     * </ol>
     *
     * @return true iff inlining was actually performed
     */
    private boolean tryToInline(MethodInvocation calleeInvocation, int inliningDepth) {
        CallsiteHolderExplorable callerCallsiteHolder = (CallsiteHolderExplorable) currentGraph();
        InlineInfo calleeInfo = calleeInvocation.callee();
        assert callerCallsiteHolder.containsInvoke(calleeInfo.invoke());
        counterInliningConsidered.increment(debug);

        InliningPolicy.Decision decision = inliningPolicy.isWorthInlining(context.getReplacements(), calleeInvocation, inliningDepth, true);
        if (decision.shouldInline()) {
            doInline(callerCallsiteHolder, calleeInvocation, decision.getReason());
            return true;
        }

        if (context.getOptimisticOptimizations().devirtualizeInvokes(calleeInfo.graph().getOptions())) {
            calleeInfo.tryToDevirtualizeInvoke(new Providers(context));
        }

        return false;
    }

    /**
     * This method picks one of the callsites belonging to the current
     * {@link CallsiteHolderExplorable}. Provided the callsite qualifies to be analyzed for
     * inlining, this method prepares a new stack top in {@link InliningData} for such callsite,
     * which comprises:
     * <ul>
     * <li>preparing a summary of feasible targets, ie preparing an {@link InlineInfo}</li>
     * <li>based on it, preparing the stack top proper which consists of:</li>
     * <ul>
     * <li>one {@link MethodInvocation}</li>
     * <li>a {@link CallsiteHolder} for each feasible target</li>
     * </ul>
     * </ul>
     *
     * <p>
     * The thus prepared "stack top" is needed by {@link #moveForward()} to explore the space of
     * inlining decisions (each decision one of: backtracking, delving, inlining).
     * </p>
     *
     * <p>
     * The {@link InlineInfo} used to get things rolling is kept around in the
     * {@link MethodInvocation}, it will be needed in case of inlining, see
     * {@link InlineInfo#inline(Providers, String)}
     * </p>
     */
    private void processNextInvoke() {
        CallsiteHolderExplorable callsiteHolder = (CallsiteHolderExplorable) currentGraph();
        Invoke invoke = callsiteHolder.popInvoke();
        InlineInfo info = getInlineInfo(invoke);

        if (info != null) {
            info.populateInlinableElements(context, currentGraph().graph(), canonicalizer, rootGraph.getOptions());
            double invokeProbability = callsiteHolder.invokeProbability(invoke);
            double invokeRelevance = callsiteHolder.invokeRelevance(invoke);
            MethodInvocation methodInvocation = new MethodInvocation(info, invokeProbability, invokeRelevance, freshlyInstantiatedArguments(invoke, callsiteHolder.getFixedParams()));
            pushInvocationAndGraphs(methodInvocation);
        }
    }

    /**
     * Gets the freshly instantiated arguments.
     * <p>
     * A freshly instantiated argument is either:
     * <uL>
     * <li>an {@link InliningData#isFreshInstantiation(org.graalvm.compiler.nodes.ValueNode)}</li>
     * <li>a fixed-param, ie a {@link ParameterNode} receiving a freshly instantiated argument</li>
     * </uL>
     * </p>
     *
     * @return the positions of freshly instantiated arguments in the argument list of the
     *         <code>invoke</code>, or null if no such positions exist.
     */
    public static BitSet freshlyInstantiatedArguments(Invoke invoke, EconomicSet<ParameterNode> fixedParams) {
        assert fixedParams != null;
        assert paramsAndInvokeAreInSameGraph(invoke, fixedParams);
        BitSet result = null;
        int argIdx = 0;
        for (ValueNode arg : invoke.callTarget().arguments()) {
            assert arg != null;
            if (isFreshInstantiation(arg) || (arg instanceof ParameterNode && fixedParams.contains((ParameterNode) arg))) {
                if (result == null) {
                    result = new BitSet();
                }
                result.set(argIdx);
            }
            argIdx++;
        }
        return result;
    }

    private static boolean paramsAndInvokeAreInSameGraph(Invoke invoke, EconomicSet<ParameterNode> fixedParams) {
        if (fixedParams.isEmpty()) {
            return true;
        }
        for (ParameterNode p : fixedParams) {
            if (p.graph() != invoke.asNode().graph()) {
                return false;
            }
        }
        return true;
    }

    public int graphCount() {
        return graphQueue.size();
    }

    public boolean hasUnprocessedGraphs() {
        return !graphQueue.isEmpty();
    }

    private CallsiteHolder currentGraph() {
        return graphQueue.peek();
    }

    private void popGraph() {
        graphQueue.pop();
        assert graphQueue.size() <= maxGraphs;
    }

    private void popGraphs(int count) {
        assert count >= 0;
        for (int i = 0; i < count; i++) {
            graphQueue.pop();
        }
    }

    private static final Object[] NO_CONTEXT = {};

    /**
     * Gets the call hierarchy of this inlining from outer most call to inner most callee.
     */
    private Object[] inliningContext() {
        if (!debug.isDumpEnabled(DebugContext.INFO_LEVEL)) {
            return NO_CONTEXT;
        }
        Object[] result = new Object[graphQueue.size()];
        int i = 0;
        for (CallsiteHolder g : graphQueue) {
            result[i++] = g.method();
        }
        return result;
    }

    private MethodInvocation currentInvocation() {
        return invocationQueue.peekFirst();
    }

    private void pushInvocationAndGraphs(MethodInvocation methodInvocation) {
        invocationQueue.addFirst(methodInvocation);
        InlineInfo info = methodInvocation.callee();
        maxGraphs += info.numberOfMethods();
        assert graphQueue.size() <= maxGraphs;
        for (int i = 0; i < info.numberOfMethods(); i++) {
            CallsiteHolder ch = methodInvocation.buildCallsiteHolderForElement(i);
            assert !contains(ch.graph());
            graphQueue.push(ch);
            assert graphQueue.size() <= maxGraphs;
        }
    }

    private void popInvocation() {
        maxGraphs -= invocationQueue.peekFirst().callee().numberOfMethods();
        assert graphQueue.size() <= maxGraphs;
        invocationQueue.removeFirst();
    }

    public int countRecursiveInlining(ResolvedJavaMethod method) {
        int count = 0;
        for (CallsiteHolder callsiteHolder : graphQueue) {
            if (method.equals(callsiteHolder.method())) {
                count++;
            }
        }
        return count;
    }

    public int inliningDepth() {
        assert invocationQueue.size() > 0;
        return invocationQueue.size() - 1;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("Invocations: ");

        for (MethodInvocation invocation : invocationQueue) {
            if (invocation.callee() != null) {
                result.append(invocation.callee().numberOfMethods());
                result.append("x ");
                result.append(invocation.callee().invoke());
                result.append("; ");
            }
        }

        result.append("\nGraphs: ");
        for (CallsiteHolder graph : graphQueue) {
            result.append(graph.graph());
            result.append("; ");
        }

        return result.toString();
    }

    /**
     * Gets a stack trace representing the current inlining stack represented by this object.
     */
    public Collection<StackTraceElement> getInvocationStackTrace() {
        List<StackTraceElement> result = new ArrayList<>();
        for (CallsiteHolder graph : graphQueue) {
            result.add(graph.method().asStackTraceElement(0));
        }

        return result;
    }

    private boolean contains(StructuredGraph graph) {
        assert graph != null;
        for (CallsiteHolder info : graphQueue) {
            if (info.graph() == graph) {
                return true;
            }
        }
        return false;
    }

    /**
     * <p>
     * The stack realized by {@link InliningData} grows and shrinks as choices are made among the
     * alternatives below:
     * <ol>
     * <li>not worth inlining: pop stack top, which comprises:
     * <ul>
     * <li>pop any remaining graphs not yet delved into</li>
     * <li>pop the current invocation</li>
     * </ul>
     * </li>
     * <li>{@link #processNextInvoke() delve} into one of the callsites hosted in the current graph,
     * such callsite is explored next by {@link #moveForward()}</li>
     * <li>{@link #tryToInline(MethodInvocation, int) try to inline}: move past the current graph
     * (remove it from the topmost element).
     * <ul>
     * <li>If that was the last one then {@link #tryToInline(MethodInvocation, int) try to inline}
     * the callsite under consideration (ie, the "current invocation").</li>
     * <li>Whether inlining occurs or not, that callsite is removed from the top of
     * {@link InliningData} .</li>
     * </ul>
     * </li>
     * </ol>
     * </p>
     *
     * <p>
     * Some facts about the alternatives above:
     * <ul>
     * <li>the first step amounts to backtracking, the 2nd one to depth-search, and the 3rd one also
     * involves backtracking (however possibly after inlining).</li>
     * <li>the choice of abandon-and-backtrack or delve-into depends on
     * {@link InliningPolicy#isWorthInlining} and {@link InliningPolicy#continueInlining}.</li>
     * <li>the 3rd choice is picked whenever none of the previous choices are made</li>
     * </ul>
     * </p>
     *
     * @return true iff inlining was actually performed
     */
    @SuppressWarnings("try")
    public boolean moveForward() {

        final MethodInvocation currentInvocation = currentInvocation();

        final boolean backtrack = (!currentInvocation.isRoot() && !inliningPolicy.isWorthInlining(context.getReplacements(), currentInvocation, inliningDepth(), false).shouldInline());
        if (backtrack) {
            int remainingGraphs = currentInvocation.totalGraphs() - currentInvocation.processedGraphs();
            assert remainingGraphs > 0;
            popGraphs(remainingGraphs);
            popInvocation();
            return false;
        }

        final boolean delve = currentGraph().hasRemainingInvokes() && inliningPolicy.continueInlining(currentGraph().graph());
        if (delve) {
            processNextInvoke();
            return false;
        }

        popGraph();
        if (currentInvocation.isRoot()) {
            return false;
        }

        // try to inline
        assert currentInvocation.callee().invoke().asNode().isAlive();
        currentInvocation.incrementProcessedGraphs();
        if (currentInvocation.processedGraphs() == currentInvocation.totalGraphs()) {
            /*
             * "all of currentInvocation's graphs processed" amounts to
             * "all concrete methods that come into question already had the callees they contain analyzed for inlining"
             */
            popInvocation();
            try (DebugContext.Scope s = debug.scope("Inlining", inliningContext())) {
                if (tryToInline(currentInvocation, inliningDepth() + 1)) {
                    // Report real progress only if we inline into the root graph
                    return currentGraph().graph() == rootGraph;
                }
                return false;
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        }

        return false;
    }

    /**
     * Checks an invariant that {@link #moveForward()} must maintain: "the top invocation records
     * how many concrete target methods (for it) remain on the {@link #graphQueue}; those targets
     * 'belong' to the current invocation in question.
     */
    private boolean topGraphsForTopInvocation() {
        if (invocationQueue.isEmpty()) {
            assert graphQueue.isEmpty();
            return true;
        }
        if (currentInvocation().isRoot()) {
            if (!graphQueue.isEmpty()) {
                assert graphQueue.size() == 1;
            }
            return true;
        }
        final int remainingGraphs = currentInvocation().totalGraphs() - currentInvocation().processedGraphs();
        final Iterator<CallsiteHolder> iter = graphQueue.iterator();
        for (int i = (remainingGraphs - 1); i >= 0; i--) {
            if (!iter.hasNext()) {
                assert false;
                return false;
            }
            CallsiteHolder queuedTargetCH = iter.next();
            Inlineable targetIE = currentInvocation().callee().inlineableElementAt(i);
            InlineableGraph targetIG = (InlineableGraph) targetIE;
            assert queuedTargetCH.method().equals(targetIG.getGraph().method());
        }
        return true;
    }

    /**
     * This method checks invariants for this class. Named after shorthand for "internal
     * representation is ok".
     */
    public boolean repOK() {
        assert topGraphsForTopInvocation();
        return true;
    }
}
