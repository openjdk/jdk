/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.java;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.nodes.spi.UncheckedInterfaceProvider;
import org.graalvm.compiler.nodes.type.StampTool;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo
public class MethodCallTargetNode extends CallTargetNode implements IterableNodeType, Simplifiable {
    public static final NodeClass<MethodCallTargetNode> TYPE = NodeClass.create(MethodCallTargetNode.class);
    protected JavaTypeProfile profile;

    public MethodCallTargetNode(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] arguments, StampPair returnStamp, JavaTypeProfile profile) {
        this(TYPE, invokeKind, targetMethod, arguments, returnStamp, profile);
    }

    protected MethodCallTargetNode(NodeClass<? extends MethodCallTargetNode> c, InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] arguments, StampPair returnStamp,
                    JavaTypeProfile profile) {
        super(c, arguments, targetMethod, invokeKind, returnStamp);
        this.profile = profile;
    }

    /**
     * Gets the instruction that produces the receiver object for this invocation, if any.
     *
     * @return the instruction that produces the receiver object for this invocation if any,
     *         {@code null} if this invocation does not take a receiver object
     */
    public ValueNode receiver() {
        return isStatic() ? null : arguments().get(0);
    }

    /**
     * Checks whether this is an invocation of a static method.
     *
     * @return {@code true} if the invocation is a static invocation
     */
    public boolean isStatic() {
        return invokeKind() == InvokeKind.Static;
    }

    public JavaKind returnKind() {
        return targetMethod().getSignature().getReturnKind();
    }

    public Invoke invoke() {
        return (Invoke) this.usages().first();
    }

    @Override
    public boolean verify() {
        assert getUsageCount() <= 1 : "call target may only be used by a single invoke";
        for (Node n : usages()) {
            assertTrue(n instanceof Invoke, "call target can only be used from an invoke (%s)", n);
        }
        if (invokeKind().isDirect()) {
            assertTrue(targetMethod().isConcrete(), "special calls or static calls are only allowed for concrete methods (%s)", targetMethod());
        }
        if (invokeKind() == InvokeKind.Static) {
            assertTrue(targetMethod().isStatic(), "static calls are only allowed for static methods (%s)", targetMethod());
        } else {
            assertFalse(targetMethod().isStatic(), "static calls are only allowed for non-static methods (%s)", targetMethod());
        }
        return super.verify();
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Long) {
            return super.toString(Verbosity.Short) + "(" + targetMethod() + ")";
        } else {
            return super.toString(verbosity);
        }
    }

    public static ResolvedJavaMethod findSpecialCallTarget(InvokeKind invokeKind, ValueNode receiver, ResolvedJavaMethod targetMethod, ResolvedJavaType contextType) {
        if (invokeKind.isDirect()) {
            return null;
        }

        // check for trivial cases (e.g. final methods, nonvirtual methods)
        if (targetMethod.canBeStaticallyBound()) {
            return targetMethod;
        }

        Assumptions assumptions = receiver.graph().getAssumptions();
        TypeReference type = StampTool.typeReferenceOrNull(receiver);
        if (type == null && invokeKind == InvokeKind.Virtual) {
            // For virtual calls, we are guaranteed to receive a correct receiver type.
            type = TypeReference.createTrusted(assumptions, targetMethod.getDeclaringClass());
        }

        if (type != null) {
            /*
             * either the holder class is exact, or the receiver object has an exact type, or it's
             * an array type
             */
            ResolvedJavaMethod resolvedMethod = type.getType().resolveConcreteMethod(targetMethod, contextType);
            if (resolvedMethod != null && (resolvedMethod.canBeStaticallyBound() || type.isExact() || type.getType().isArray())) {
                return resolvedMethod;
            }

            AssumptionResult<ResolvedJavaMethod> uniqueConcreteMethod = type.getType().findUniqueConcreteMethod(targetMethod);
            if (uniqueConcreteMethod != null && uniqueConcreteMethod.canRecordTo(assumptions)) {
                uniqueConcreteMethod.recordTo(assumptions);
                return uniqueConcreteMethod.getResult();
            }
        }

        return null;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        // attempt to devirtualize the call
        if (invoke().getContextMethod() == null) {
            // avoid invokes that have placeholder bcis: they do not have a valid contextType
            assert (invoke().stateAfter() != null && BytecodeFrame.isPlaceholderBci(invoke().stateAfter().bci)) || BytecodeFrame.isPlaceholderBci(invoke().stateDuring().bci);
            return;
        }
        ResolvedJavaType contextType = (invoke().stateAfter() == null && invoke().stateDuring() == null) ? null : invoke().getContextType();
        ResolvedJavaMethod specialCallTarget = findSpecialCallTarget(invokeKind, receiver(), targetMethod, contextType);
        if (specialCallTarget != null) {
            this.setTargetMethod(specialCallTarget);
            setInvokeKind(InvokeKind.Special);
            return;
        }

        Assumptions assumptions = graph().getAssumptions();
        /*
         * Even though we are not registering an assumption (see comment below), the optimization is
         * only valid when speculative optimizations are enabled.
         */
        if (invokeKind().isIndirect() && invokeKind().isInterface() && assumptions != null) {

            // check if the type of the receiver can narrow the result
            ValueNode receiver = receiver();

            // try to turn a interface call into a virtual call
            ResolvedJavaType declaredReceiverType = targetMethod().getDeclaringClass();

            /*
             * We need to check the invoke kind to avoid recursive simplification for virtual
             * interface methods calls.
             */
            if (declaredReceiverType.isInterface()) {
                ResolvedJavaType singleImplementor = declaredReceiverType.getSingleImplementor();
                if (singleImplementor != null && !singleImplementor.equals(declaredReceiverType)) {
                    TypeReference speculatedType = TypeReference.createTrusted(assumptions, singleImplementor);
                    if (tryCheckCastSingleImplementor(receiver, speculatedType)) {
                        return;
                    }
                }
            }

            if (receiver instanceof UncheckedInterfaceProvider) {
                UncheckedInterfaceProvider uncheckedInterfaceProvider = (UncheckedInterfaceProvider) receiver;
                Stamp uncheckedStamp = uncheckedInterfaceProvider.uncheckedStamp();
                if (uncheckedStamp != null) {
                    TypeReference speculatedType = StampTool.typeReferenceOrNull(uncheckedStamp);
                    if (speculatedType != null) {
                        tryCheckCastSingleImplementor(receiver, speculatedType);
                    }
                }
            }
        }
    }

    private boolean tryCheckCastSingleImplementor(ValueNode receiver, TypeReference speculatedType) {
        ResolvedJavaType singleImplementor = speculatedType.getType();
        if (singleImplementor != null) {
            ResolvedJavaMethod singleImplementorMethod = singleImplementor.resolveConcreteMethod(targetMethod(), invoke().getContextType());
            if (singleImplementorMethod != null) {
                /**
                 * We have an invoke on an interface with a single implementor. We can replace this
                 * with an invoke virtual.
                 *
                 * To do so we need to ensure two properties: 1) the receiver must implement the
                 * interface (declaredReceiverType). The verifier does not prove this so we need a
                 * dynamic check. 2) we need to ensure that there is still only one implementor of
                 * this interface, i.e. that we are calling the right method. We could do this with
                 * an assumption but as we need an instanceof check anyway we can verify both
                 * properties by checking of the receiver is an instance of the single implementor.
                 */
                ValueAnchorNode anchor = new ValueAnchorNode(null);
                if (anchor != null) {
                    graph().add(anchor);
                    graph().addBeforeFixed(invoke().asNode(), anchor);
                }
                LogicNode condition = graph().addOrUniqueWithInputs(InstanceOfNode.create(speculatedType, receiver, getProfile(), anchor));
                FixedGuardNode guard = graph().add(new FixedGuardNode(condition, DeoptimizationReason.OptimizedTypeCheckViolated, DeoptimizationAction.InvalidateRecompile, false));
                graph().addBeforeFixed(invoke().asNode(), guard);
                PiNode piNode = graph().unique(new PiNode(receiver, StampFactory.objectNonNull(speculatedType), guard));
                arguments().set(0, piNode);
                if (speculatedType.isExact()) {
                    setInvokeKind(InvokeKind.Special);
                } else {
                    setInvokeKind(InvokeKind.Virtual);
                }
                setTargetMethod(singleImplementorMethod);
                return true;
            }
        }
        return false;
    }

    public JavaTypeProfile getProfile() {
        return profile;
    }

    @Override
    public String targetName() {
        if (targetMethod() == null) {
            return "??Invalid!";
        }
        return targetMethod().format("%h.%n");
    }

    public static MethodCallTargetNode find(StructuredGraph graph, ResolvedJavaMethod method) {
        for (MethodCallTargetNode target : graph.getNodes(MethodCallTargetNode.TYPE)) {
            if (target.targetMethod().equals(method)) {
                return target;
            }
        }
        return null;
    }
}
