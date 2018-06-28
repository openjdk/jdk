/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.lang.invoke.MethodHandle;
import java.util.Arrays;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.AnchoringNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

/**
 * Node for invocation methods defined on the class {@link MethodHandle}.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
public final class MethodHandleNode extends MacroStateSplitNode implements Simplifiable {
    public static final NodeClass<MethodHandleNode> TYPE = NodeClass.create(MethodHandleNode.class);

    protected final IntrinsicMethod intrinsicMethod;

    public MethodHandleNode(IntrinsicMethod intrinsicMethod, InvokeKind invokeKind, ResolvedJavaMethod targetMethod, int bci, StampPair returnStamp, ValueNode... arguments) {
        super(TYPE, invokeKind, targetMethod, bci, returnStamp, arguments);
        this.intrinsicMethod = intrinsicMethod;
    }

    /**
     * Attempts to transform application of an intrinsifiable {@link MethodHandle} method into an
     * invocation on another method with possibly transformed arguments.
     *
     * @param methodHandleAccess objects for accessing the implementation internals of a
     *            {@link MethodHandle}
     * @param intrinsicMethod denotes the intrinsifiable {@link MethodHandle} method being processed
     * @param bci the BCI of the original {@link MethodHandle} call
     * @param returnStamp return stamp of the original {@link MethodHandle} call
     * @param arguments arguments to the original {@link MethodHandle} call
     * @return a more direct invocation derived from the {@link MethodHandle} call or null
     */
    public static InvokeNode tryResolveTargetInvoke(GraphAdder adder, MethodHandleAccessProvider methodHandleAccess, IntrinsicMethod intrinsicMethod,
                    ResolvedJavaMethod original, int bci,
                    StampPair returnStamp, ValueNode... arguments) {
        switch (intrinsicMethod) {
            case INVOKE_BASIC:
                return getInvokeBasicTarget(adder, intrinsicMethod, methodHandleAccess, original, bci, returnStamp, arguments);
            case LINK_TO_STATIC:
            case LINK_TO_SPECIAL:
            case LINK_TO_VIRTUAL:
            case LINK_TO_INTERFACE:
                return getLinkToTarget(adder, intrinsicMethod, methodHandleAccess, original, bci, returnStamp, arguments);
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    /**
     * A simple utility class for adding nodes to the graph when building a MethodHandle invoke.
     */
    public abstract static class GraphAdder {
        private final StructuredGraph graph;

        public GraphAdder(StructuredGraph graph) {
            this.graph = graph;
        }

        /**
         * Call {@link StructuredGraph#addOrUnique(org.graalvm.compiler.graph.Node)} on {@code node}
         * and link any {@link FixedWithNextNode}s into the current control flow.
         *
         * @param node
         * @return the newly added node
         */
        public abstract <T extends ValueNode> T add(T node);

        /**
         * @return an {@link AnchoringNode} if floating guards should be created, otherwise
         *         {@link FixedGuardNode}s will be used.
         */
        public AnchoringNode getGuardAnchor() {
            return null;
        }

        public Assumptions getAssumptions() {
            return graph.getAssumptions();
        }
    }

    @Override
    public void simplify(SimplifierTool tool) {
        MethodHandleAccessProvider methodHandleAccess = tool.getConstantReflection().getMethodHandleAccess();
        ValueNode[] argumentsArray = arguments.toArray(new ValueNode[arguments.size()]);

        final FixedNode before = this;
        GraphAdder adder = new GraphAdder(graph()) {
            @Override
            public <T extends ValueNode> T add(T node) {
                T added = graph().addOrUnique(node);
                if (added instanceof FixedWithNextNode) {
                    graph().addBeforeFixed(before, (FixedWithNextNode) added);
                }
                return added;
            }
        };
        InvokeNode invoke = tryResolveTargetInvoke(adder, methodHandleAccess, intrinsicMethod, targetMethod, bci, returnStamp, argumentsArray);
        if (invoke != null) {
            assert invoke.graph() == null;
            invoke = graph().addOrUniqueWithInputs(invoke);
            invoke.setStateAfter(stateAfter());
            FixedNode currentNext = next();
            replaceAtUsages(invoke);
            GraphUtil.removeFixedWithUnusedInputs(this);
            graph().addBeforeFixed(currentNext, invoke);
        }
    }

    /**
     * Get the receiver of a MethodHandle.invokeBasic call.
     *
     * @return the receiver argument node
     */
    private static ValueNode getReceiver(ValueNode[] arguments) {
        return arguments[0];
    }

    /**
     * Get the MemberName argument of a MethodHandle.linkTo* call.
     *
     * @return the MemberName argument node (which is the last argument)
     */
    private static ValueNode getMemberName(ValueNode[] arguments) {
        return arguments[arguments.length - 1];
    }

    /**
     * Used for the MethodHandle.invokeBasic method (the {@link IntrinsicMethod#INVOKE_BASIC }
     * method) to get the target {@link InvokeNode} if the method handle receiver is constant.
     *
     * @param adder
     *
     * @return invoke node for the {@link java.lang.invoke.MethodHandle} target
     */
    private static InvokeNode getInvokeBasicTarget(GraphAdder adder, IntrinsicMethod intrinsicMethod, MethodHandleAccessProvider methodHandleAccess,
                    ResolvedJavaMethod original,
                    int bci,
                    StampPair returnStamp, ValueNode[] arguments) {
        ValueNode methodHandleNode = getReceiver(arguments);
        if (methodHandleNode.isConstant()) {
            return getTargetInvokeNode(adder, intrinsicMethod, bci, returnStamp, arguments, methodHandleAccess.resolveInvokeBasicTarget(methodHandleNode.asJavaConstant(), true), original);
        }
        return null;
    }

    /**
     * Used for the MethodHandle.linkTo* methods (the {@link IntrinsicMethod#LINK_TO_STATIC},
     * {@link IntrinsicMethod#LINK_TO_SPECIAL}, {@link IntrinsicMethod#LINK_TO_VIRTUAL}, and
     * {@link IntrinsicMethod#LINK_TO_INTERFACE} methods) to get the target {@link InvokeNode} if
     * the member name argument is constant.
     *
     * @param adder
     *
     * @return invoke node for the member name target
     */
    private static InvokeNode getLinkToTarget(GraphAdder adder, IntrinsicMethod intrinsicMethod, MethodHandleAccessProvider methodHandleAccess,
                    ResolvedJavaMethod original,
                    int bci,
                    StampPair returnStamp, ValueNode[] arguments) {
        ValueNode memberNameNode = getMemberName(arguments);
        if (memberNameNode.isConstant()) {
            return getTargetInvokeNode(adder, intrinsicMethod, bci, returnStamp, arguments, methodHandleAccess.resolveLinkToTarget(memberNameNode.asJavaConstant()), original);
        }
        return null;
    }

    /**
     * Helper function to get the {@link InvokeNode} for the targetMethod of a
     * java.lang.invoke.MemberName.
     *
     * @param adder
     * @param target the target, already loaded from the member name node
     *
     * @return invoke node for the member name target
     */
    private static InvokeNode getTargetInvokeNode(GraphAdder adder, IntrinsicMethod intrinsicMethod, int bci, StampPair returnStamp, ValueNode[] originalArguments, ResolvedJavaMethod target,
                    ResolvedJavaMethod original) {
        if (target == null) {
            return null;
        }

        // In lambda forms we erase signature types to avoid resolving issues
        // involving class loaders. When we optimize a method handle invoke
        // to a direct call we must cast the receiver and arguments to its
        // actual types.
        Signature signature = target.getSignature();
        final boolean isStatic = target.isStatic();
        final int receiverSkip = isStatic ? 0 : 1;

        Assumptions assumptions = adder.getAssumptions();
        ResolvedJavaMethod realTarget = null;
        if (target.canBeStaticallyBound()) {
            realTarget = target;
        } else {
            ResolvedJavaType targetType = target.getDeclaringClass();
            // Try to bind based on the declaredType
            AssumptionResult<ResolvedJavaMethod> concreteMethod = targetType.findUniqueConcreteMethod(target);
            if (concreteMethod == null) {
                // Try to get the most accurate receiver type
                if (intrinsicMethod == IntrinsicMethod.LINK_TO_VIRTUAL || intrinsicMethod == IntrinsicMethod.LINK_TO_INTERFACE) {
                    ValueNode receiver = getReceiver(originalArguments);
                    TypeReference receiverType = StampTool.typeReferenceOrNull(receiver.stamp(NodeView.DEFAULT));
                    if (receiverType != null) {
                        concreteMethod = receiverType.getType().findUniqueConcreteMethod(target);
                    }
                }

            }
            if (concreteMethod != null && concreteMethod.canRecordTo(assumptions)) {
                concreteMethod.recordTo(assumptions);
                realTarget = concreteMethod.getResult();
            }
        }

        if (realTarget != null) {
            // Don't mutate the passed in arguments
            ValueNode[] arguments = originalArguments.clone();

            // Cast receiver to its type.
            if (!isStatic) {
                JavaType receiverType = target.getDeclaringClass();
                maybeCastArgument(adder, arguments, 0, receiverType);
            }

            // Cast reference arguments to its type.
            for (int index = 0; index < signature.getParameterCount(false); index++) {
                JavaType parameterType = signature.getParameterType(index, target.getDeclaringClass());
                maybeCastArgument(adder, arguments, receiverSkip + index, parameterType);
            }
            InvokeNode invoke = createTargetInvokeNode(assumptions, intrinsicMethod, realTarget, original, bci, returnStamp, arguments);
            assert invoke != null : "graph has been modified so this must result an invoke";
            return invoke;
        }
        return null;
    }

    /**
     * Inserts a node to cast the argument at index to the given type if the given type is more
     * concrete than the argument type.
     *
     * @param adder
     * @param index of the argument to be cast
     * @param type the type the argument should be cast to
     */
    private static void maybeCastArgument(GraphAdder adder, ValueNode[] arguments, int index, JavaType type) {
        ValueNode argument = arguments[index];
        if (type instanceof ResolvedJavaType && !((ResolvedJavaType) type).isJavaLangObject()) {
            Assumptions assumptions = adder.getAssumptions();
            TypeReference targetType = TypeReference.create(assumptions, (ResolvedJavaType) type);
            /*
             * When an argument is a Word type, we can have a mismatch of primitive/object types
             * here. Not inserting a PiNode is a safe fallback, and Word types need no additional
             * type information anyway.
             */
            if (targetType != null && !targetType.getType().isPrimitive() && !argument.getStackKind().isPrimitive()) {
                ResolvedJavaType argumentType = StampTool.typeOrNull(argument.stamp(NodeView.DEFAULT));
                if (argumentType == null || (argumentType.isAssignableFrom(targetType.getType()) && !argumentType.equals(targetType.getType()))) {
                    LogicNode inst = InstanceOfNode.createAllowNull(targetType, argument, null, null);
                    assert !inst.isAlive();
                    if (!inst.isTautology()) {
                        inst = adder.add(inst);
                        AnchoringNode guardAnchor = adder.getGuardAnchor();
                        DeoptimizationReason reason = DeoptimizationReason.ClassCastException;
                        DeoptimizationAction action = DeoptimizationAction.InvalidateRecompile;
                        Speculation speculation = SpeculationLog.NO_SPECULATION;
                        GuardingNode guard;
                        if (guardAnchor == null) {
                            FixedGuardNode fixedGuard = adder.add(new FixedGuardNode(inst, reason, action, speculation, false));
                            guard = fixedGuard;
                        } else {
                            GuardNode newGuard = adder.add(new GuardNode(inst, guardAnchor, reason, action, false, speculation, null));
                            adder.add(new ValueAnchorNode(newGuard));
                            guard = newGuard;
                        }
                        ValueNode valueNode = adder.add(PiNode.create(argument, StampFactory.object(targetType), guard.asNode()));
                        arguments[index] = valueNode;
                    }
                }
            }
        }
    }

    /**
     * Creates an {@link InvokeNode} for the given target method. The {@link CallTargetNode} passed
     * to the InvokeNode is in fact a {@link ResolvedMethodHandleCallTargetNode}.
     *
     * @return invoke node for the member name target
     */
    private static InvokeNode createTargetInvokeNode(Assumptions assumptions, IntrinsicMethod intrinsicMethod, ResolvedJavaMethod target, ResolvedJavaMethod original, int bci, StampPair returnStamp,
                    ValueNode[] arguments) {
        InvokeKind targetInvokeKind = target.isStatic() ? InvokeKind.Static : InvokeKind.Special;
        JavaType targetReturnType = target.getSignature().getReturnType(null);

        // MethodHandleLinkTo* nodes have a trailing MemberName argument which
        // needs to be popped.
        ValueNode[] targetArguments;
        switch (intrinsicMethod) {
            case INVOKE_BASIC:
                targetArguments = arguments;
                break;
            case LINK_TO_STATIC:
            case LINK_TO_SPECIAL:
            case LINK_TO_VIRTUAL:
            case LINK_TO_INTERFACE:
                targetArguments = Arrays.copyOfRange(arguments, 0, arguments.length - 1);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        StampPair targetReturnStamp = StampFactory.forDeclaredType(assumptions, targetReturnType, false);

        MethodCallTargetNode callTarget = ResolvedMethodHandleCallTargetNode.create(targetInvokeKind, target, targetArguments, targetReturnStamp, original, arguments, returnStamp);

        // The call target can have a different return type than the invoker,
        // e.g. the target returns an Object but the invoker void. In this case
        // we need to use the stamp of the invoker. Note: always using the
        // invoker's stamp would be wrong because it's a less concrete type
        // (usually java.lang.Object).
        if (returnStamp.getTrustedStamp().getStackKind() == JavaKind.Void) {
            return new InvokeNode(callTarget, bci, StampFactory.forVoid());
        } else {
            return new InvokeNode(callTarget, bci);
        }
    }
}
