/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.graphbuilderconf;

import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.NullCheckException;
import static org.graalvm.compiler.core.common.type.StampFactory.objectNonNull;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DynamicPiNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.java.InstanceOfDynamicNode;
import org.graalvm.compiler.nodes.type.StampTool;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Used by a {@link GraphBuilderPlugin} to interface with an object that parses the bytecode of a
 * single {@linkplain #getMethod() method} as part of building a {@linkplain #getGraph() graph} .
 */
public interface GraphBuilderContext extends GraphBuilderTool {

    /**
     * Pushes a given value to the frame state stack using an explicit kind. This should be used
     * when {@code value.getJavaKind()} is different from the kind that the bytecode instruction
     * currently being parsed pushes to the stack.
     *
     * @param kind the kind to use when type checking this operation
     * @param value the value to push to the stack. The value must already have been
     *            {@linkplain #append(ValueNode) appended}.
     */
    void push(JavaKind kind, ValueNode value);

    /**
     * Pops a value from the frame state stack using an explicit kind.
     *
     * @param slotKind the kind to use when type checking this operation
     * @return the value on the top of the stack
     */
    default ValueNode pop(JavaKind slotKind) {
        throw GraalError.unimplemented();
    }

    /**
     * Adds a node and all its inputs to the graph. If the node is in the graph, returns
     * immediately. If the node is a {@link StateSplit} with a null
     * {@linkplain StateSplit#stateAfter() frame state} , the frame state is initialized.
     *
     * @param value the value to add to the graph and push to the stack. The
     *            {@code value.getJavaKind()} kind is used when type checking this operation.
     * @return a node equivalent to {@code value} in the graph
     */
    default <T extends ValueNode> T add(T value) {
        if (value.graph() != null) {
            assert !(value instanceof StateSplit) || ((StateSplit) value).stateAfter() != null;
            return value;
        }
        return GraphBuilderContextUtil.setStateAfterIfNecessary(this, append(value));
    }

    default ValueNode addNonNullCast(ValueNode value) {
        AbstractPointerStamp valueStamp = (AbstractPointerStamp) value.stamp(NodeView.DEFAULT);
        if (valueStamp.nonNull()) {
            return value;
        } else {
            LogicNode isNull = add(IsNullNode.create(value));
            FixedGuardNode fixedGuard = add(new FixedGuardNode(isNull, DeoptimizationReason.NullCheckException, DeoptimizationAction.None, true));
            Stamp newStamp = valueStamp.improveWith(StampFactory.objectNonNull());
            return add(PiNode.create(value, newStamp, fixedGuard));
        }
    }

    /**
     * Adds a node with a non-void kind to the graph, pushes it to the stack. If the returned node
     * is a {@link StateSplit} with a null {@linkplain StateSplit#stateAfter() frame state}, the
     * frame state is initialized.
     *
     * @param kind the kind to use when type checking this operation
     * @param value the value to add to the graph and push to the stack
     * @return a node equivalent to {@code value} in the graph
     */
    default <T extends ValueNode> T addPush(JavaKind kind, T value) {
        T equivalentValue = value.graph() != null ? value : append(value);
        push(kind, equivalentValue);
        return GraphBuilderContextUtil.setStateAfterIfNecessary(this, equivalentValue);
    }

    /**
     * Handles an invocation that a plugin determines can replace the original invocation (i.e., the
     * one for which the plugin was applied). This applies all standard graph builder processing to
     * the replaced invocation including applying any relevant plugins.
     *
     * @param invokeKind the kind of the replacement invocation
     * @param targetMethod the target of the replacement invocation
     * @param args the arguments to the replacement invocation
     * @param forceInlineEverything specifies if all invocations encountered in the scope of
     *            handling the replaced invoke are to be force inlined
     */
    Invoke handleReplacedInvoke(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, boolean forceInlineEverything);

    void handleReplacedInvoke(CallTargetNode callTarget, JavaKind resultType);

    /**
     * Intrinsifies an invocation of a given method by inlining the bytecodes of a given
     * substitution method.
     *
     * @param bytecodeProvider used to get the bytecodes to parse for the substitution method
     * @param targetMethod the method being intrinsified
     * @param substitute the intrinsic implementation
     * @param receiver the receiver, or null for static methods
     * @param argsIncludingReceiver the arguments with which to inline the invocation
     *
     * @return whether the intrinsification was successful
     */
    boolean intrinsify(BytecodeProvider bytecodeProvider, ResolvedJavaMethod targetMethod, ResolvedJavaMethod substitute, InvocationPlugin.Receiver receiver, ValueNode[] argsIncludingReceiver);

    /**
     * Intrinsifies an invocation of a given method by inlining the graph of a given substitution
     * method.
     *
     * @param targetMethod the method being intrinsified
     * @param substituteGraph the intrinsic implementation
     * @param receiver the receiver, or null for static methods
     * @param argsIncludingReceiver the arguments with which to inline the invocation
     *
     * @return whether the intrinsification was successful
     */
    boolean intrinsify(ResolvedJavaMethod targetMethod, StructuredGraph substituteGraph, InvocationPlugin.Receiver receiver, ValueNode[] argsIncludingReceiver);

    /**
     * Creates a snap shot of the current frame state with the BCI of the instruction after the one
     * currently being parsed and assigns it to a given {@linkplain StateSplit#hasSideEffect() side
     * effect} node.
     *
     * @param sideEffect a side effect node just appended to the graph
     */
    void setStateAfter(StateSplit sideEffect);

    /**
     * Gets the parsing context for the method that inlines the method being parsed by this context.
     */
    GraphBuilderContext getParent();

    /**
     * Gets the first ancestor parsing context that is not parsing a {@linkplain #parsingIntrinsic()
     * intrinsic}.
     */
    default GraphBuilderContext getNonIntrinsicAncestor() {
        GraphBuilderContext ancestor = getParent();
        while (ancestor != null && ancestor.parsingIntrinsic()) {
            ancestor = ancestor.getParent();
        }
        return ancestor;
    }

    /**
     * Gets the code being parsed.
     */
    Bytecode getCode();

    /**
     * Gets the method being parsed by this context.
     */
    ResolvedJavaMethod getMethod();

    /**
     * Gets the index of the bytecode instruction currently being parsed.
     */
    int bci();

    /**
     * Gets the kind of invocation currently being parsed.
     */
    InvokeKind getInvokeKind();

    /**
     * Gets the return type of the invocation currently being parsed.
     */
    JavaType getInvokeReturnType();

    default StampPair getInvokeReturnStamp(Assumptions assumptions) {
        JavaType returnType = getInvokeReturnType();
        return StampFactory.forDeclaredType(assumptions, returnType, false);
    }

    /**
     * Gets the inline depth of this context. A return value of 0 implies that this is the context
     * for the parse root.
     */
    default int getDepth() {
        GraphBuilderContext parent = getParent();
        int result = 0;
        while (parent != null) {
            result++;
            parent = parent.getParent();
        }
        return result;
    }

    /**
     * Determines if this parsing context is within the bytecode of an intrinsic or a method inlined
     * by an intrinsic.
     */
    @Override
    default boolean parsingIntrinsic() {
        return getIntrinsic() != null;
    }

    /**
     * Determines if a graph builder plugin is enabled under current context.
     */
    default boolean isPluginEnabled(GraphBuilderPlugin plugin) {
        return parsingIntrinsic() || !(plugin instanceof GeneratedInvocationPlugin && ((GeneratedInvocationPlugin) plugin).isGeneratedFromFoldOrNodeIntrinsic());
    }

    /**
     * Gets the intrinsic of the current parsing context or {@code null} if not
     * {@link #parsingIntrinsic() parsing an intrinsic}.
     */
    IntrinsicContext getIntrinsic();

    BailoutException bailout(String string);

    default ValueNode nullCheckedValue(ValueNode value) {
        return nullCheckedValue(value, InvalidateReprofile);
    }

    /**
     * Gets a version of a given value that has a {@linkplain StampTool#isPointerNonNull(ValueNode)
     * non-null} stamp.
     */
    default ValueNode nullCheckedValue(ValueNode value, DeoptimizationAction action) {
        if (!StampTool.isPointerNonNull(value)) {
            LogicNode condition = getGraph().unique(IsNullNode.create(value));
            GuardingNode guardingNode;
            if (needsExplicitException()) {
                AbstractBeginNode exceptionPath = genExplicitExceptionEdge(BytecodeExceptionKind.NULL_POINTER);
                IfNode ifNode = append(new IfNode(condition, exceptionPath, null, 0.01));
                BeginNode nonNullPath = append(new BeginNode());
                ifNode.setFalseSuccessor(nonNullPath);
                guardingNode = nonNullPath;
            } else {
                guardingNode = append(new FixedGuardNode(condition, NullCheckException, action, true));
            }
            ValueNode nonNullReceiver = getGraph().addOrUniqueWithInputs(PiNode.create(value, objectNonNull(), guardingNode.asNode()));
            // TODO: Propogating the non-null into the frame state would
            // remove subsequent null-checks on the same value. However,
            // it currently causes an assertion failure when merging states.
            //
            // frameState.replace(value, nonNullReceiver);
            return nonNullReceiver;
        }
        return value;
    }

    default void genCheckcastDynamic(ValueNode object, ValueNode javaClass) {
        LogicNode condition = InstanceOfDynamicNode.create(getAssumptions(), getConstantReflection(), javaClass, object, true);
        if (condition.isTautology()) {
            addPush(JavaKind.Object, object);
        } else {
            append(condition);
            FixedGuardNode fixedGuard = add(new FixedGuardNode(condition, DeoptimizationReason.ClassCastException, DeoptimizationAction.InvalidateReprofile, false));
            addPush(JavaKind.Object, DynamicPiNode.create(getAssumptions(), getConstantReflection(), object, fixedGuard, javaClass));
        }
    }

    @SuppressWarnings("unused")
    default void notifyReplacedCall(ResolvedJavaMethod targetMethod, ConstantNode node) {

    }

    /**
     * Interface whose instances hold inlining information about the current context, in a wider
     * sense. The wider sense in this case concerns graph building approaches that don't necessarily
     * keep a chain of {@link GraphBuilderContext} instances normally available through
     * {@linkplain #getParent()}. Examples of such approaches are partial evaluation and incremental
     * inlining.
     */
    interface ExternalInliningContext {
        int getInlinedDepth();
    }

    default ExternalInliningContext getExternalInliningContext() {
        return null;
    }

    /**
     * Adds masking to a given subword value according to a given {@Link JavaKind}, such that the
     * masked value falls in the range of the given kind. In the cases where the given kind is not a
     * subword kind, the input value is returned immediately.
     *
     * @param value the value to be masked
     * @param kind the kind that specifies the range of the masked value
     * @return the masked value
     */
    default ValueNode maskSubWordValue(ValueNode value, JavaKind kind) {
        if (kind == kind.getStackKind()) {
            return value;
        }
        // Subword value
        ValueNode narrow = append(NarrowNode.create(value, kind.getBitCount(), NodeView.DEFAULT));
        if (kind.isUnsigned()) {
            return append(ZeroExtendNode.create(narrow, 32, NodeView.DEFAULT));
        } else {
            return append(SignExtendNode.create(narrow, 32, NodeView.DEFAULT));
        }
    }

    /**
     * @return true if an explicit exception check should be emitted.
     */
    default boolean needsExplicitException() {
        return false;
    }

    /**
     * Generates an exception edge for the current bytecode. When {@link #needsExplicitException()}
     * returns true, this method should return non-null begin nodes.
     *
     * @param exceptionKind the type of exception to be created.
     * @return a begin node that precedes the actual exception instantiation code.
     */
    default AbstractBeginNode genExplicitExceptionEdge(@SuppressWarnings("ununsed") BytecodeExceptionKind exceptionKind) {
        return null;
    }
}

class GraphBuilderContextUtil {
    static <T extends ValueNode> T setStateAfterIfNecessary(GraphBuilderContext b, T value) {
        if (value instanceof StateSplit) {
            StateSplit stateSplit = (StateSplit) value;
            if (stateSplit.stateAfter() == null && (stateSplit.hasSideEffect() || stateSplit instanceof AbstractMergeNode)) {
                b.setStateAfter(stateSplit);
            }
        }
        return value;
    }
}
