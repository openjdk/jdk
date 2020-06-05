/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.BytecodeFrame.isPlaceholderBci;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.Invokable;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.FrameStateAssignmentPhase;
import org.graalvm.compiler.phases.common.GuardLoweringPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Macro nodes can be used to temporarily replace an invoke. They can, for example, be used to
 * implement constant folding for known JDK functions like {@link Class#isInterface()}.<br/>
 * <br/>
 * During lowering, multiple sources are queried in order to look for a replacement:
 * <ul>
 * <li>If {@link #getLoweredSnippetGraph(LoweringTool)} returns a non-null result, this graph is
 * used as a replacement.</li>
 * <li>If a {@link MethodSubstitution} for the target method is found, this substitution is used as
 * a replacement.</li>
 * <li>Otherwise, the macro node is replaced with an {@link InvokeNode}. Note that this is only
 * possible if the macro node is a {@link MacroStateSplitNode}.</li>
 * </ul>
 */
//@formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
          cyclesRationale = "If this node is not optimized away it will be lowered to a call, which we cannot estimate",
          size = SIZE_UNKNOWN,
          sizeRationale = "If this node is not optimized away it will be lowered to a call, which we cannot estimate")
//@formatter:on
public abstract class MacroNode extends FixedWithNextNode implements Lowerable, Invokable {

    public static final NodeClass<MacroNode> TYPE = NodeClass.create(MacroNode.class);
    @Input protected NodeInputList<ValueNode> arguments;

    protected final int bci;
    protected final ResolvedJavaMethod callerMethod;
    protected final ResolvedJavaMethod targetMethod;
    protected final InvokeKind invokeKind;
    protected final StampPair returnStamp;

    /**
     * Encapsulates the parameters for constructing a {@link MacroNode} that are the same for all
     * leaf constructor call sites. Collecting the parameters in an object simplifies passing the
     * parameters through the many chained constructor calls.
     */
    public static class MacroParams {
        public final InvokeKind invokeKind;
        public final ResolvedJavaMethod callerMethod;
        public final ResolvedJavaMethod targetMethod;
        public final int bci;
        public final StampPair returnStamp;
        public final ValueNode[] arguments;

        public MacroParams(InvokeKind invokeKind,
                        ResolvedJavaMethod callerMethod,
                        ResolvedJavaMethod targetMethod,
                        int bci,
                        StampPair returnStamp,
                        ValueNode... arguments) {
            this.invokeKind = invokeKind;
            this.callerMethod = callerMethod;
            this.targetMethod = targetMethod;
            this.bci = bci;
            this.returnStamp = returnStamp;
            this.arguments = arguments;
        }

        public static MacroParams of(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode... arguments) {
            return new MacroParams(b.getInvokeKind(), b.getMethod(), targetMethod, b.bci(), b.getInvokeReturnStamp(b.getAssumptions()), arguments);
        }

        public static MacroParams of(GraphBuilderContext b, InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode... arguments) {
            return new MacroParams(invokeKind, b.getMethod(), targetMethod, b.bci(), b.getInvokeReturnStamp(b.getAssumptions()), arguments);
        }

        public static MacroParams of(InvokeKind invokeKind,
                        ResolvedJavaMethod callerMethod,
                        ResolvedJavaMethod targetMethod,
                        int bci,
                        StampPair returnStamp,
                        ValueNode... arguments) {
            return new MacroParams(invokeKind, callerMethod, targetMethod, bci, returnStamp, arguments);
        }
    }

    protected MacroNode(NodeClass<? extends MacroNode> c, MacroParams p) {
        super(c, p.returnStamp != null ? p.returnStamp.getTrustedStamp() : null);
        assertArgumentCount(p.targetMethod, p.arguments);
        this.arguments = new NodeInputList<>(this, p.arguments);
        this.bci = p.bci;
        this.callerMethod = p.callerMethod;
        this.targetMethod = p.targetMethod;
        this.returnStamp = p.returnStamp;
        this.invokeKind = p.invokeKind;
        assert !isPlaceholderBci(p.bci);
    }

    @Override
    public ResolvedJavaMethod getContextMethod() {
        return callerMethod;
    }

    protected void assertArgumentCount(ResolvedJavaMethod method, ValueNode... args) {
        assert method.getSignature().getParameterCount(!method.isStatic()) == args.length;
    }

    public ValueNode getArgument(int i) {
        return arguments.get(i);
    }

    public int getArgumentCount() {
        return arguments.size();
    }

    public ValueNode[] toArgumentArray() {
        return arguments.toArray(new ValueNode[0]);
    }

    @Override
    public int bci() {
        return bci;
    }

    @Override
    public void setBci(int bci) {
        // nothing to do here, macro nodes get bci during construction
    }

    @Override
    public ResolvedJavaMethod getTargetMethod() {
        return targetMethod;
    }

    public InvokeKind getInvokeKind() {
        return invokeKind;
    }

    protected FrameState stateAfter() {
        return null;
    }

    @Override
    protected void afterClone(Node other) {
        updateInliningLogAfterClone(other);
    }

    @Override
    public FixedNode asFixedNode() {
        return this;
    }

    /**
     * Gets a snippet to be used for lowering this macro node. The returned graph (if non-null) must
     * have been {@linkplain #lowerReplacement(StructuredGraph, LoweringTool) lowered}.
     */
    @SuppressWarnings("unused")
    protected StructuredGraph getLoweredSnippetGraph(LoweringTool tool) {
        return null;
    }

    /**
     * Applies {@linkplain LoweringPhase lowering} to a replacement graph.
     *
     * @param replacementGraph a replacement (i.e., snippet or method substitution) graph
     */
    @SuppressWarnings("try")
    protected StructuredGraph lowerReplacement(final StructuredGraph replacementGraph, LoweringTool tool) {
        final CoreProviders c = tool.getProviders();
        if (!graph().hasValueProxies()) {
            new RemoveValueProxyPhase().apply(replacementGraph);
        }
        GuardsStage guardsStage = graph().getGuardsStage();
        if (!guardsStage.allowsFloatingGuards()) {
            new GuardLoweringPhase().apply(replacementGraph, null);
            if (guardsStage.areFrameStatesAtDeopts()) {
                new FrameStateAssignmentPhase().apply(replacementGraph);
            }
        }
        DebugContext debug = replacementGraph.getDebug();
        try (DebugContext.Scope s = debug.scope("LoweringSnippetTemplate", replacementGraph)) {
            new LoweringPhase(CanonicalizerPhase.create(), tool.getLoweringStage()).apply(replacementGraph, c);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
        return replacementGraph;
    }

    @Override
    public void lower(LoweringTool tool) {
        StructuredGraph replacementGraph = getLoweredSnippetGraph(tool);

        InvokeNode invoke = replaceWithInvoke();
        assert invoke.verify();

        if (replacementGraph != null) {
            // Pull out the receiver null check so that a replaced
            // receiver can be lowered if necessary
            if (!targetMethod.isStatic()) {
                ValueNode nonNullReceiver = InliningUtil.nonNullReceiver(invoke);
                if (nonNullReceiver instanceof Lowerable) {
                    ((Lowerable) nonNullReceiver).lower(tool);
                }
            }
            InliningUtil.inline(invoke, replacementGraph, false, targetMethod, "Replace with graph.", "LoweringPhase");
            replacementGraph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph(), "After inlining replacement %s", replacementGraph);
        } else {
            if (isPlaceholderBci(invoke.bci())) {
                throw new GraalError("%s: cannot lower to invoke with placeholder BCI: %s", graph(), this);
            }

            if (invoke.stateAfter() == null) {
                throw new GraalError("%s: cannot lower to invoke without state: %s", graph(), this);
            }
            invoke.lower(tool);
        }
    }

    @SuppressWarnings("try")
    public InvokeNode replaceWithInvoke() {
        try (DebugCloseable context = withNodeSourcePosition()) {
            InvokeNode invoke = createInvoke();
            graph().replaceFixedWithFixed(this, invoke);
            return invoke;
        }
    }

    public LocationIdentity getLocationIdentity() {
        return LocationIdentity.any();
    }

    protected InvokeNode createInvoke() {
        MethodCallTargetNode callTarget = graph().add(new MethodCallTargetNode(invokeKind, targetMethod, arguments.toArray(new ValueNode[arguments.size()]), returnStamp, null));
        InvokeNode invoke = graph().add(new InvokeNode(callTarget, bci, getLocationIdentity()));
        if (stateAfter() != null) {
            invoke.setStateAfter(stateAfter().duplicate());
            if (getStackKind() != JavaKind.Void) {
                invoke.stateAfter().replaceFirstInput(this, invoke);
            }
        }
        return invoke;
    }
}
