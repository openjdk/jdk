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


package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.debug.GraalError.unimplemented;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.cfg.CFGVerifier;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.SourceLanguagePosition;
import org.graalvm.compiler.graph.SourceLanguagePositionProvider;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.SimplifyingGraphDecoder;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.InvocationPluginReceiver;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin.LoopExplosionKind;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A graph decoder that performs partial evaluation, i.e., that performs method inlining and
 * canonicalization/simplification of nodes during decoding.
 *
 * Inlining and loop explosion are configured via the plugin mechanism also used by the
 * {@link GraphBuilderPhase}. However, not all callback methods defined in
 * {@link GraphBuilderContext} are available since decoding is more limited than graph building.
 *
 * The standard {@link Canonicalizable#canonical node canonicalization} interface is used to
 * canonicalize nodes during decoding. Additionally, {@link IfNode branches} and
 * {@link IntegerSwitchNode switches} with constant conditions are simplified.
 */
public abstract class PEGraphDecoder extends SimplifyingGraphDecoder {

    private static final Object CACHED_NULL_VALUE = new Object();

    public static class Options {
        @Option(help = "Maximum inlining depth during partial evaluation before reporting an infinite recursion")//
        public static final OptionKey<Integer> InliningDepthError = new OptionKey<>(1000);

        @Option(help = "Max number of loop explosions per method.", type = OptionType.Debug)//
        public static final OptionKey<Integer> MaximumLoopExplosionCount = new OptionKey<>(10000);

        @Option(help = "Do not bail out but throw an exception on failed loop explosion.", type = OptionType.Debug)//
        public static final OptionKey<Boolean> FailedLoopExplosionIsFatal = new OptionKey<>(false);
    }

    protected class PEMethodScope extends MethodScope {
        /** The state of the caller method. Only non-null during method inlining. */
        protected final PEMethodScope caller;
        protected final ResolvedJavaMethod method;
        protected final InvokeData invokeData;
        protected final int inliningDepth;

        protected final ValueNode[] arguments;
        private SourceLanguagePosition sourceLanguagePosition = UnresolvedSourceLanguagePosition.INSTANCE;

        protected FrameState outerState;
        protected FrameState exceptionState;
        protected ExceptionPlaceholderNode exceptionPlaceholderNode;
        protected NodeSourcePosition callerBytecodePosition;

        protected PEMethodScope(StructuredGraph targetGraph, PEMethodScope caller, LoopScope callerLoopScope, EncodedGraph encodedGraph, ResolvedJavaMethod method, InvokeData invokeData,
                        int inliningDepth, LoopExplosionPlugin loopExplosionPlugin, ValueNode[] arguments) {
            super(callerLoopScope, targetGraph, encodedGraph, loopExplosionKind(method, loopExplosionPlugin));

            this.caller = caller;
            this.method = method;
            this.invokeData = invokeData;
            this.inliningDepth = inliningDepth;
            this.arguments = arguments;
        }

        @Override
        public boolean isInlinedMethod() {
            return caller != null;
        }

        @Override
        public NodeSourcePosition getCallerBytecodePosition(NodeSourcePosition position) {
            if (caller == null) {
                return position;
            }
            if (callerBytecodePosition == null) {
                NodeSourcePosition invokePosition = invokeData.invoke.asNode().getNodeSourcePosition();
                if (invokePosition == null) {
                    assert position == null : "should only happen when tracking is disabled";
                    return null;
                }
                callerBytecodePosition = invokePosition;
            }
            if (position != null) {
                return position.addCaller(caller.resolveSourceLanguagePosition(), callerBytecodePosition);
            }
            final SourceLanguagePosition pos = caller.resolveSourceLanguagePosition();
            if (pos != null && callerBytecodePosition != null) {
                return new NodeSourcePosition(pos, callerBytecodePosition.getCaller(), callerBytecodePosition.getMethod(), callerBytecodePosition.getBCI());
            }
            return callerBytecodePosition;
        }

        private SourceLanguagePosition resolveSourceLanguagePosition() {
            SourceLanguagePosition res = sourceLanguagePosition;
            if (res == UnresolvedSourceLanguagePosition.INSTANCE) {
                res = null;
                if (arguments != null && method.hasReceiver() && arguments.length > 0 && arguments[0].isJavaConstant()) {
                    JavaConstant constantArgument = arguments[0].asJavaConstant();
                    res = sourceLanguagePositionProvider.getPosition(constantArgument);
                }
                sourceLanguagePosition = res;
            }
            return res;
        }
    }

    private static final class UnresolvedSourceLanguagePosition implements SourceLanguagePosition {
        static final SourceLanguagePosition INSTANCE = new UnresolvedSourceLanguagePosition();

        @Override
        public String toShortString() {
            throw new IllegalStateException(getClass().getSimpleName() + " should not be reachable.");
        }

        @Override
        public int getOffsetEnd() {
            throw new IllegalStateException(getClass().getSimpleName() + " should not be reachable.");
        }

        @Override
        public int getOffsetStart() {
            throw new IllegalStateException(getClass().getSimpleName() + " should not be reachable.");
        }

        @Override
        public int getLineNumber() {
            throw new IllegalStateException(getClass().getSimpleName() + " should not be reachable.");
        }

        @Override
        public URI getURI() {
            throw new IllegalStateException(getClass().getSimpleName() + " should not be reachable.");
        }

        @Override
        public String getLanguage() {
            throw new IllegalStateException(getClass().getSimpleName() + " should not be reachable.");
        }
    }

    protected class PENonAppendGraphBuilderContext implements GraphBuilderContext {
        protected final PEMethodScope methodScope;
        protected final Invoke invoke;

        @Override
        public ExternalInliningContext getExternalInliningContext() {
            return new ExternalInliningContext() {
                @Override
                public int getInlinedDepth() {
                    int count = 0;
                    PEGraphDecoder.PEMethodScope scope = methodScope;
                    while (scope != null) {
                        if (scope.method.equals(callInlinedMethod)) {
                            count++;
                        }
                        scope = scope.caller;
                    }
                    return count;
                }
            };
        }

        public PENonAppendGraphBuilderContext(PEMethodScope methodScope, Invoke invoke) {
            this.methodScope = methodScope;
            this.invoke = invoke;
        }

        /**
         * {@link Fold} and {@link NodeIntrinsic} can be deferred during parsing/decoding. Only by
         * the end of {@linkplain SnippetTemplate#instantiate Snippet instantiation} do they need to
         * have been processed.
         *
         * This is how SVM handles snippets. They are parsed with plugins disabled and then encoded
         * and stored in the image. When the snippet is needed at runtime the graph is decoded and
         * the plugins are run during the decoding process. If they aren't handled at this point
         * then they will never be handled.
         */
        @Override
        public boolean canDeferPlugin(GeneratedInvocationPlugin plugin) {
            return plugin.getSource().equals(Fold.class) || plugin.getSource().equals(Node.NodeIntrinsic.class);
        }

        @Override
        public BailoutException bailout(String string) {
            BailoutException bailout = new PermanentBailoutException(string);
            throw GraphUtil.createBailoutException(string, bailout, GraphUtil.approxSourceStackTraceElement(methodScope.getCallerBytecodePosition()));
        }

        @Override
        public StampProvider getStampProvider() {
            return stampProvider;
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
        public StructuredGraph getGraph() {
            return graph;
        }

        @Override
        public int getDepth() {
            return methodScope.inliningDepth;
        }

        @Override
        public IntrinsicContext getIntrinsic() {
            return null;
        }

        @Override
        public <T extends ValueNode> T append(T value) {
            throw unimplemented();
        }

        @Override
        public void push(JavaKind kind, ValueNode value) {
            throw unimplemented();
        }

        @Override
        public void handleReplacedInvoke(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, boolean inlineEverything) {
            throw unimplemented();
        }

        @Override
        public void handleReplacedInvoke(CallTargetNode callTarget, JavaKind resultType) {
            throw unimplemented();
        }

        @Override
        public boolean intrinsify(BytecodeProvider bytecodeProvider, ResolvedJavaMethod targetMethod, ResolvedJavaMethod substitute, InvocationPlugin.Receiver receiver, ValueNode[] args) {
            return false;
        }

        @Override
        public void setStateAfter(StateSplit stateSplit) {
            throw unimplemented();
        }

        @Override
        public GraphBuilderContext getParent() {
            throw unimplemented();
        }

        @Override
        public Bytecode getCode() {
            throw unimplemented();
        }

        @Override
        public ResolvedJavaMethod getMethod() {
            throw unimplemented();
        }

        @Override
        public int bci() {
            return invoke.bci();
        }

        @Override
        public InvokeKind getInvokeKind() {
            throw unimplemented();
        }

        @Override
        public JavaType getInvokeReturnType() {
            throw unimplemented();
        }

        @Override
        public String toString() {
            Formatter fmt = new Formatter();
            PEMethodScope scope = this.methodScope;
            fmt.format("%s", new ResolvedJavaMethodBytecode(scope.method).asStackTraceElement(invoke.bci()));
            NodeSourcePosition callers = scope.getCallerBytecodePosition();
            if (callers != null) {
                fmt.format("%n%s", callers);
            }
            return fmt.toString();
        }
    }

    protected class PEAppendGraphBuilderContext extends PENonAppendGraphBuilderContext {
        protected FixedWithNextNode lastInstr;
        protected ValueNode pushedNode;
        protected boolean invokeConsumed;
        protected final InvokeKind invokeKind;
        protected final JavaType invokeReturnType;

        public PEAppendGraphBuilderContext(PEMethodScope inlineScope, FixedWithNextNode lastInstr) {
            this(inlineScope, lastInstr, null, null);
        }

        public PEAppendGraphBuilderContext(PEMethodScope inlineScope, FixedWithNextNode lastInstr, InvokeKind invokeKind, JavaType invokeReturnType) {
            super(inlineScope, inlineScope.invokeData != null ? inlineScope.invokeData.invoke : null);
            this.lastInstr = lastInstr;
            this.invokeKind = invokeKind;
            this.invokeReturnType = invokeReturnType;
        }

        @Override
        public void push(JavaKind kind, ValueNode value) {
            if (pushedNode != null) {
                throw unimplemented("Only one push is supported");
            }
            pushedNode = value;
        }

        @Override
        public void setStateAfter(StateSplit stateSplit) {
            Node stateAfter = decodeFloatingNode(methodScope.caller, methodScope.callerLoopScope, methodScope.invokeData.stateAfterOrderId);
            getGraph().add(stateAfter);
            FrameState fs = (FrameState) handleFloatingNodeAfterAdd(methodScope.caller, methodScope.callerLoopScope, stateAfter);
            stateSplit.setStateAfter(fs);
        }

        @SuppressWarnings("try")
        @Override
        public <T extends ValueNode> T append(T v) {
            if (v.graph() != null) {
                return v;
            }
            try (DebugCloseable position = withNodeSoucePosition()) {
                T added = getGraph().addOrUniqueWithInputs(v);
                if (added == v) {
                    updateLastInstruction(v);
                }
                return added;
            }
        }

        private DebugCloseable withNodeSoucePosition() {
            if (getGraph().trackNodeSourcePosition()) {
                NodeSourcePosition callerBytecodePosition = methodScope.getCallerBytecodePosition();
                if (callerBytecodePosition != null) {
                    return getGraph().withNodeSourcePosition(callerBytecodePosition);
                }
            }
            return null;
        }

        private <T extends ValueNode> void updateLastInstruction(T v) {
            if (v instanceof FixedNode) {
                FixedNode fixedNode = (FixedNode) v;
                if (lastInstr != null) {
                    lastInstr.setNext(fixedNode);
                }
                if (fixedNode instanceof FixedWithNextNode) {
                    FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) fixedNode;
                    assert fixedWithNextNode.next() == null : "cannot append instruction to instruction which isn't end";
                    lastInstr = fixedWithNextNode;
                } else {
                    lastInstr = null;
                }
            }
        }

        @Override
        public InvokeKind getInvokeKind() {
            if (invokeKind != null) {
                return invokeKind;
            }
            return super.getInvokeKind();
        }

        @Override
        public JavaType getInvokeReturnType() {
            if (invokeReturnType != null) {
                return invokeReturnType;
            }
            return super.getInvokeReturnType();
        }

        @Override
        public void handleReplacedInvoke(CallTargetNode callTarget, JavaKind resultType) {
            if (invokeConsumed) {
                throw unimplemented("handleReplacedInvoke can be called only once");
            }
            invokeConsumed = true;

            appendInvoke(methodScope.caller, methodScope.callerLoopScope, methodScope.invokeData, callTarget);
            updateLastInstruction(invoke.asNode());
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static class ExceptionPlaceholderNode extends ValueNode {
        public static final NodeClass<ExceptionPlaceholderNode> TYPE = NodeClass.create(ExceptionPlaceholderNode.class);

        protected ExceptionPlaceholderNode() {
            super(TYPE, StampFactory.object());
        }
    }

    protected static class SpecialCallTargetCacheKey {
        private final InvokeKind invokeKind;
        private final ResolvedJavaMethod targetMethod;
        private final ResolvedJavaType contextType;
        private final Stamp receiverStamp;

        public SpecialCallTargetCacheKey(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ResolvedJavaType contextType, Stamp receiverStamp) {
            this.invokeKind = invokeKind;
            this.targetMethod = targetMethod;
            this.contextType = contextType;
            this.receiverStamp = receiverStamp;
        }

        @Override
        public int hashCode() {
            return invokeKind.hashCode() ^ targetMethod.hashCode() ^ contextType.hashCode() ^ receiverStamp.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SpecialCallTargetCacheKey) {
                SpecialCallTargetCacheKey key = (SpecialCallTargetCacheKey) obj;
                return key.invokeKind.equals(this.invokeKind) && key.targetMethod.equals(this.targetMethod) && key.contextType.equals(this.contextType) && key.receiverStamp.equals(this.receiverStamp);
            }
            return false;
        }
    }

    private final LoopExplosionPlugin loopExplosionPlugin;
    private final InvocationPlugins invocationPlugins;
    private final InlineInvokePlugin[] inlineInvokePlugins;
    private final ParameterPlugin parameterPlugin;
    private final NodePlugin[] nodePlugins;
    private final EconomicMap<SpecialCallTargetCacheKey, Object> specialCallTargetCache;
    private final EconomicMap<ResolvedJavaMethod, Object> invocationPluginCache;
    private final ResolvedJavaMethod callInlinedMethod;
    protected final SourceLanguagePositionProvider sourceLanguagePositionProvider;

    public PEGraphDecoder(Architecture architecture, StructuredGraph graph, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider,
                    StampProvider stampProvider, LoopExplosionPlugin loopExplosionPlugin, InvocationPlugins invocationPlugins, InlineInvokePlugin[] inlineInvokePlugins,
                    ParameterPlugin parameterPlugin,
                    NodePlugin[] nodePlugins, ResolvedJavaMethod callInlinedMethod, SourceLanguagePositionProvider sourceLanguagePositionProvider) {
        super(architecture, graph, metaAccess, constantReflection, constantFieldProvider, stampProvider, true);
        this.loopExplosionPlugin = loopExplosionPlugin;
        this.invocationPlugins = invocationPlugins;
        this.inlineInvokePlugins = inlineInvokePlugins;
        this.parameterPlugin = parameterPlugin;
        this.nodePlugins = nodePlugins;
        this.specialCallTargetCache = EconomicMap.create(Equivalence.DEFAULT);
        this.invocationPluginCache = EconomicMap.create(Equivalence.DEFAULT);
        this.callInlinedMethod = callInlinedMethod;
        this.sourceLanguagePositionProvider = sourceLanguagePositionProvider;
    }

    protected static LoopExplosionKind loopExplosionKind(ResolvedJavaMethod method, LoopExplosionPlugin loopExplosionPlugin) {
        if (loopExplosionPlugin == null) {
            return LoopExplosionKind.NONE;
        } else {
            return loopExplosionPlugin.loopExplosionKind(method);
        }
    }

    public void decode(ResolvedJavaMethod method, boolean isSubstitution, boolean trackNodeSourcePosition) {
        PEMethodScope methodScope = new PEMethodScope(graph, null, null, lookupEncodedGraph(method, null, null, isSubstitution, trackNodeSourcePosition), method, null, 0, loopExplosionPlugin, null);
        decode(createInitialLoopScope(methodScope, null));
        cleanupGraph(methodScope);

        debug.dump(DebugContext.VERBOSE_LEVEL, graph, "After graph cleanup");
        assert graph.verify();

        try {
            /* Check that the control flow graph can be computed, to catch problems early. */
            assert CFGVerifier.verify(ControlFlowGraph.compute(graph, true, true, true, true));
        } catch (Throwable ex) {
            throw GraalError.shouldNotReachHere("Control flow graph not valid after partial evaluation");
        }
    }

    @Override
    protected void cleanupGraph(MethodScope methodScope) {
        super.cleanupGraph(methodScope);

        for (FrameState frameState : graph.getNodes(FrameState.TYPE)) {
            if (frameState.bci == BytecodeFrame.UNWIND_BCI) {
                /*
                 * handleMissingAfterExceptionFrameState is called during graph decoding from
                 * InliningUtil.processFrameState - but during graph decoding it does not do
                 * anything because the usages of the frameState are not available yet. So we need
                 * to call it again.
                 */
                PEMethodScope peMethodScope = (PEMethodScope) methodScope;
                Invoke invoke = peMethodScope.invokeData != null ? peMethodScope.invokeData.invoke : null;
                InliningUtil.handleMissingAfterExceptionFrameState(frameState, invoke, null, true);

                /*
                 * The frameState must be gone now, because it is not a valid deoptimization point.
                 */
                assert frameState.isDeleted();
            }
        }
    }

    @Override
    protected void checkLoopExplosionIteration(MethodScope s, LoopScope loopScope) {
        PEMethodScope methodScope = (PEMethodScope) s;

        if (loopScope.loopIteration > Options.MaximumLoopExplosionCount.getValue(options)) {
            throw tooManyLoopExplosionIterations(methodScope, options);
        }
    }

    private static RuntimeException tooManyLoopExplosionIterations(PEMethodScope methodScope, OptionValues options) {
        String message = "too many loop explosion iterations - does the explosion not terminate for method " + methodScope.method + "?";
        RuntimeException bailout = Options.FailedLoopExplosionIsFatal.getValue(options) ? new RuntimeException(message) : new PermanentBailoutException(message);
        throw GraphUtil.createBailoutException(message, bailout, GraphUtil.approxSourceStackTraceElement(methodScope.getCallerBytecodePosition()));
    }

    @Override
    protected LoopScope handleInvoke(MethodScope s, LoopScope loopScope, InvokeData invokeData) {
        PEMethodScope methodScope = (PEMethodScope) s;

        /*
         * Decode the call target, but do not add it to the graph yet. This avoids adding usages for
         * all the arguments, which are expensive to remove again when we can inline the method.
         */
        assert invokeData.invoke.callTarget() == null : "callTarget edge is ignored during decoding of Invoke";
        CallTargetNode callTarget = (CallTargetNode) decodeFloatingNode(methodScope, loopScope, invokeData.callTargetOrderId);
        if (callTarget instanceof MethodCallTargetNode) {
            MethodCallTargetNode methodCall = (MethodCallTargetNode) callTarget;
            if (methodCall.invokeKind().hasReceiver()) {
                invokeData.constantReceiver = methodCall.arguments().get(0).asJavaConstant();
                NodeSourcePosition invokePosition = invokeData.invoke.asNode().getNodeSourcePosition();
                if (invokeData.constantReceiver != null && invokePosition != null) {
                    // new NodeSourcePosition(invokeData.constantReceiver,
                    // invokePosition.getCaller(), invokePosition.getMethod(),
                    // invokePosition.getBCI());
                }
            }
            LoopScope inlineLoopScope = trySimplifyInvoke(methodScope, loopScope, invokeData, (MethodCallTargetNode) callTarget);
            if (inlineLoopScope != null) {
                return inlineLoopScope;
            }
        }

        /* We know that we need an invoke, so now we can add the call target to the graph. */
        graph.add(callTarget);
        registerNode(loopScope, invokeData.callTargetOrderId, callTarget, false, false);
        return super.handleInvoke(methodScope, loopScope, invokeData);
    }

    protected LoopScope trySimplifyInvoke(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, MethodCallTargetNode callTarget) {
        // attempt to devirtualize the call
        ResolvedJavaMethod specialCallTarget = getSpecialCallTarget(invokeData, callTarget);
        if (specialCallTarget != null) {
            callTarget.setTargetMethod(specialCallTarget);
            callTarget.setInvokeKind(InvokeKind.Special);
        }

        if (tryInvocationPlugin(methodScope, loopScope, invokeData, callTarget)) {
            /*
             * The invocation plugin handled the call, so decoding continues in the calling method.
             */
            return loopScope;
        }
        LoopScope inlineLoopScope = tryInline(methodScope, loopScope, invokeData, callTarget);
        if (inlineLoopScope != null) {
            /*
             * We can inline the call, so decoding continues in the inlined method.
             */
            return inlineLoopScope;
        }

        for (InlineInvokePlugin plugin : inlineInvokePlugins) {
            plugin.notifyNotInlined(new PENonAppendGraphBuilderContext(methodScope, invokeData.invoke), callTarget.targetMethod(), invokeData.invoke);
        }
        return null;
    }

    private ResolvedJavaMethod getSpecialCallTarget(InvokeData invokeData, MethodCallTargetNode callTarget) {
        if (callTarget.invokeKind().isDirect()) {
            return null;
        }

        // check for trivial cases (e.g. final methods, nonvirtual methods)
        if (callTarget.targetMethod().canBeStaticallyBound()) {
            return callTarget.targetMethod();
        }

        SpecialCallTargetCacheKey key = new SpecialCallTargetCacheKey(callTarget.invokeKind(), callTarget.targetMethod(), invokeData.contextType, callTarget.receiver().stamp(NodeView.DEFAULT));
        Object specialCallTarget = specialCallTargetCache.get(key);
        if (specialCallTarget == null) {
            specialCallTarget = MethodCallTargetNode.devirtualizeCall(key.invokeKind, key.targetMethod, key.contextType, graph.getAssumptions(),
                            key.receiverStamp);
            if (specialCallTarget == null) {
                specialCallTarget = CACHED_NULL_VALUE;
            }
            specialCallTargetCache.put(key, specialCallTarget);
        }

        return specialCallTarget == CACHED_NULL_VALUE ? null : (ResolvedJavaMethod) specialCallTarget;
    }

    protected boolean tryInvocationPlugin(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, MethodCallTargetNode callTarget) {
        if (invocationPlugins == null || invocationPlugins.isEmpty()) {
            return false;
        }

        Invoke invoke = invokeData.invoke;

        ResolvedJavaMethod targetMethod = callTarget.targetMethod();
        InvocationPlugin invocationPlugin = getInvocationPlugin(targetMethod);
        if (invocationPlugin == null) {
            return false;
        }

        ValueNode[] arguments = callTarget.arguments().toArray(new ValueNode[0]);
        FixedWithNextNode invokePredecessor = (FixedWithNextNode) invoke.asNode().predecessor();

        /*
         * Remove invoke from graph so that invocation plugin can append nodes to the predecessor.
         */
        invoke.asNode().replaceAtPredecessor(null);

        PEMethodScope inlineScope = new PEMethodScope(graph, methodScope, loopScope, null, targetMethod, invokeData, methodScope.inliningDepth + 1, loopExplosionPlugin, arguments);

        JavaType returnType = targetMethod.getSignature().getReturnType(methodScope.method.getDeclaringClass());
        PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(inlineScope, invokePredecessor, callTarget.invokeKind(), returnType);
        InvocationPluginReceiver invocationPluginReceiver = new InvocationPluginReceiver(graphBuilderContext);

        if (invocationPlugin.execute(graphBuilderContext, targetMethod, invocationPluginReceiver.init(targetMethod, arguments), arguments)) {

            if (graphBuilderContext.invokeConsumed) {
                /* Nothing to do. */
            } else if (graphBuilderContext.lastInstr != null) {
                registerNode(loopScope, invokeData.invokeOrderId, graphBuilderContext.pushedNode, true, true);
                invoke.asNode().replaceAtUsages(graphBuilderContext.pushedNode);
                graphBuilderContext.lastInstr.setNext(nodeAfterInvoke(methodScope, loopScope, invokeData, AbstractBeginNode.prevBegin(graphBuilderContext.lastInstr)));
                deleteInvoke(invoke);
            } else {
                assert graphBuilderContext.pushedNode == null : "Why push a node when the invoke does not return anyway?";
                invoke.asNode().replaceAtUsages(null);
                deleteInvoke(invoke);
            }
            return true;

        } else {
            /* Intrinsification failed, restore original state: invoke is in Graph. */
            invokePredecessor.setNext(invoke.asNode());
            return false;
        }
    }

    private InvocationPlugin getInvocationPlugin(ResolvedJavaMethod targetMethod) {
        Object invocationPlugin = invocationPluginCache.get(targetMethod);
        if (invocationPlugin == null) {
            invocationPlugin = invocationPlugins.lookupInvocation(targetMethod);
            if (invocationPlugin == null) {
                invocationPlugin = CACHED_NULL_VALUE;
            }
            invocationPluginCache.put(targetMethod, invocationPlugin);
        }

        return invocationPlugin == CACHED_NULL_VALUE ? null : (InvocationPlugin) invocationPlugin;
    }

    protected LoopScope tryInline(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, MethodCallTargetNode callTarget) {
        if (!callTarget.invokeKind().isDirect()) {
            return null;
        }

        ResolvedJavaMethod targetMethod = callTarget.targetMethod();
        if (targetMethod.hasNeverInlineDirective()) {
            return null;
        }

        ValueNode[] arguments = callTarget.arguments().toArray(new ValueNode[0]);
        GraphBuilderContext graphBuilderContext = new PENonAppendGraphBuilderContext(methodScope, invokeData.invoke);

        for (InlineInvokePlugin plugin : inlineInvokePlugins) {
            InlineInfo inlineInfo = plugin.shouldInlineInvoke(graphBuilderContext, targetMethod, arguments);
            if (inlineInfo != null) {
                if (inlineInfo.getMethodToInline() == null) {
                    return null;
                } else {
                    return doInline(methodScope, loopScope, invokeData, inlineInfo, arguments);
                }
            }
        }
        return null;
    }

    protected LoopScope doInline(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, InlineInfo inlineInfo, ValueNode[] arguments) {
        if (!invokeData.invoke.useForInlining()) {
            return null;
        }
        ResolvedJavaMethod inlineMethod = inlineInfo.getMethodToInline();
        ResolvedJavaMethod originalMethod = inlineInfo.getOriginalMethod();
        boolean isSubstitution = originalMethod != null && !originalMethod.equals(inlineMethod);
        EncodedGraph graphToInline = lookupEncodedGraph(inlineMethod, originalMethod, inlineInfo.getIntrinsicBytecodeProvider(), isSubstitution, graph.trackNodeSourcePosition());
        if (graphToInline == null) {
            return null;
        }

        assert !graph.trackNodeSourcePosition() || graphToInline.trackNodeSourcePosition() : graph + " " + graphToInline;
        if (methodScope.inliningDepth > Options.InliningDepthError.getValue(options)) {
            throw tooDeepInlining(methodScope);
        }

        for (InlineInvokePlugin plugin : inlineInvokePlugins) {
            plugin.notifyBeforeInline(inlineMethod);
        }

        Invoke invoke = invokeData.invoke;
        FixedNode invokeNode = invoke.asNode();
        FixedWithNextNode predecessor = (FixedWithNextNode) invokeNode.predecessor();
        invokeNode.replaceAtPredecessor(null);

        PEMethodScope inlineScope = new PEMethodScope(graph, methodScope, loopScope, graphToInline, inlineMethod, invokeData, methodScope.inliningDepth + 1,
                        loopExplosionPlugin, arguments);

        if (!inlineMethod.isStatic()) {
            if (StampTool.isPointerAlwaysNull(arguments[0])) {
                /*
                 * The receiver is null, so we can unconditionally throw a NullPointerException
                 * instead of performing any inlining.
                 */
                DeoptimizeNode deoptimizeNode = graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.NullCheckException));
                predecessor.setNext(deoptimizeNode);
                finishInlining(inlineScope);
                /* Continue decoding in the caller. */
                return loopScope;

            } else if (!StampTool.isPointerNonNull(arguments[0])) {
                /* The receiver might be null, so we need to insert a null check. */
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(inlineScope, predecessor);
                arguments[0] = graphBuilderContext.nullCheckedValue(arguments[0]);
                predecessor = graphBuilderContext.lastInstr;
            }
        }

        LoopScope inlineLoopScope = createInitialLoopScope(inlineScope, predecessor);

        /*
         * The GraphEncoder assigns parameters a nodeId immediately after the fixed nodes.
         * Initializing createdNodes here avoid decoding and immediately replacing the
         * ParameterNodes.
         */
        int firstArgumentNodeId = inlineScope.maxFixedNodeOrderId + 1;
        for (int i = 0; i < arguments.length; i++) {
            inlineLoopScope.createdNodes[firstArgumentNodeId + i] = arguments[i];
        }

        // Copy assumptions from inlinee to caller
        Assumptions assumptions = graph.getAssumptions();
        Assumptions inlinedAssumptions = graphToInline.getAssumptions();
        if (assumptions != null) {
            if (inlinedAssumptions != null) {
                assumptions.record(inlinedAssumptions);
            }
        } else {
            assert inlinedAssumptions == null : String.format("cannot inline graph (%s) which makes assumptions into a graph (%s) that doesn't", inlineMethod, graph);
        }

        // Copy inlined methods from inlinee to caller
        List<ResolvedJavaMethod> inlinedMethods = graphToInline.getInlinedMethods();
        if (inlinedMethods != null) {
            for (ResolvedJavaMethod other : inlinedMethods) {
                graph.recordMethod(other);
            }
        }

        if (graphToInline.getFields() != null) {
            for (ResolvedJavaField field : graphToInline.getFields()) {
                graph.recordField(field);
            }
        }
        if (graphToInline.hasUnsafeAccess()) {
            graph.markUnsafeAccess();
        }

        /*
         * Do the actual inlining by returning the initial loop scope for the inlined method scope.
         */
        return inlineLoopScope;
    }

    @Override
    protected void finishInlining(MethodScope is) {
        PEMethodScope inlineScope = (PEMethodScope) is;
        ResolvedJavaMethod inlineMethod = inlineScope.method;
        PEMethodScope methodScope = inlineScope.caller;
        LoopScope loopScope = inlineScope.callerLoopScope;
        InvokeData invokeData = inlineScope.invokeData;
        Invoke invoke = invokeData.invoke;
        FixedNode invokeNode = invoke.asNode();

        ValueNode exceptionValue = null;
        int returnNodeCount = 0;
        int unwindNodeCount = 0;
        List<ControlSinkNode> returnAndUnwindNodes = inlineScope.returnAndUnwindNodes;
        for (int i = 0; i < returnAndUnwindNodes.size(); i++) {
            FixedNode fixedNode = returnAndUnwindNodes.get(i);
            if (fixedNode instanceof ReturnNode) {
                returnNodeCount++;
            } else if (fixedNode.isAlive()) {
                assert fixedNode instanceof UnwindNode;
                unwindNodeCount++;
            }
        }

        if (unwindNodeCount > 0) {
            FixedNode unwindReplacement;
            if (invoke instanceof InvokeWithExceptionNode) {
                /* Decoding continues for the exception handler. */
                unwindReplacement = makeStubNode(methodScope, loopScope, invokeData.exceptionNextOrderId);
            } else {
                /* No exception handler available, so the only thing we can do is deoptimize. */
                unwindReplacement = graph.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.NotCompiledExceptionHandler));
            }

            if (unwindNodeCount == 1) {
                /* Only one UnwindNode, we can use the exception directly. */
                UnwindNode unwindNode = getSingleMatchingNode(returnAndUnwindNodes, returnNodeCount > 0, UnwindNode.class);
                exceptionValue = unwindNode.exception();
                unwindNode.replaceAndDelete(unwindReplacement);

            } else {
                /*
                 * More than one UnwindNode. This can happen with the loop explosion strategy
                 * FULL_EXPLODE_UNTIL_RETURN, where we keep exploding after the loop and therefore
                 * also explode exception paths. Merge the exception in a similar way as multiple
                 * return values.
                 */
                MergeNode unwindMergeNode = graph.add(new MergeNode());
                exceptionValue = InliningUtil.mergeValueProducers(unwindMergeNode, getMatchingNodes(returnAndUnwindNodes, returnNodeCount > 0, UnwindNode.class, unwindNodeCount),
                                null, unwindNode -> unwindNode.exception());
                unwindMergeNode.setNext(unwindReplacement);

                ensureExceptionStateDecoded(inlineScope);
                unwindMergeNode.setStateAfter(inlineScope.exceptionState.duplicateModified(JavaKind.Object, JavaKind.Object, exceptionValue));
            }
        }

        assert invoke.next() == null;
        assert !(invoke instanceof InvokeWithExceptionNode) || ((InvokeWithExceptionNode) invoke).exceptionEdge() == null;

        ValueNode returnValue;
        if (returnNodeCount == 0) {
            returnValue = null;
        } else if (returnNodeCount == 1) {
            ReturnNode returnNode = getSingleMatchingNode(returnAndUnwindNodes, unwindNodeCount > 0, ReturnNode.class);
            returnValue = returnNode.result();
            FixedNode n = nodeAfterInvoke(methodScope, loopScope, invokeData, AbstractBeginNode.prevBegin(returnNode));
            returnNode.replaceAndDelete(n);
        } else {
            AbstractMergeNode merge = graph.add(new MergeNode());
            merge.setStateAfter((FrameState) ensureNodeCreated(methodScope, loopScope, invokeData.stateAfterOrderId));
            returnValue = InliningUtil.mergeReturns(merge, getMatchingNodes(returnAndUnwindNodes, unwindNodeCount > 0, ReturnNode.class, returnNodeCount));
            FixedNode n = nodeAfterInvoke(methodScope, loopScope, invokeData, merge);
            merge.setNext(n);
        }
        invokeNode.replaceAtUsages(returnValue);

        /*
         * Usage the handles that we have on the return value and the exception to update the
         * orderId->Node table.
         */
        registerNode(loopScope, invokeData.invokeOrderId, returnValue, true, true);
        if (invoke instanceof InvokeWithExceptionNode) {
            registerNode(loopScope, invokeData.exceptionOrderId, exceptionValue, true, true);
        }
        if (inlineScope.exceptionPlaceholderNode != null) {
            inlineScope.exceptionPlaceholderNode.replaceAtUsagesAndDelete(exceptionValue);
        }
        deleteInvoke(invoke);

        for (InlineInvokePlugin plugin : inlineInvokePlugins) {
            plugin.notifyAfterInline(inlineMethod);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getSingleMatchingNode(List<ControlSinkNode> returnAndUnwindNodes, boolean hasNonMatchingEntries, Class<T> clazz) {
        if (!hasNonMatchingEntries) {
            assert returnAndUnwindNodes.size() == 1;
            return (T) returnAndUnwindNodes.get(0);
        }

        for (int i = 0; i < returnAndUnwindNodes.size(); i++) {
            ControlSinkNode node = returnAndUnwindNodes.get(i);
            if (clazz.isInstance(node)) {
                return (T) node;
            }
        }
        throw GraalError.shouldNotReachHere();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> getMatchingNodes(List<ControlSinkNode> returnAndUnwindNodes, boolean hasNonMatchingEntries, Class<T> clazz, int resultCount) {
        if (!hasNonMatchingEntries) {
            return (List<T>) returnAndUnwindNodes;
        }

        List<T> result = new ArrayList<>(resultCount);
        for (int i = 0; i < returnAndUnwindNodes.size(); i++) {
            ControlSinkNode node = returnAndUnwindNodes.get(i);
            if (clazz.isInstance(node)) {
                result.add((T) node);
            }
        }
        assert result.size() == resultCount;
        return result;
    }

    private static RuntimeException tooDeepInlining(PEMethodScope methodScope) {
        HashMap<ResolvedJavaMethod, Integer> methodCounts = new HashMap<>();
        for (PEMethodScope cur = methodScope; cur != null; cur = cur.caller) {
            Integer oldCount = methodCounts.get(cur.method);
            methodCounts.put(cur.method, oldCount == null ? 1 : oldCount + 1);
        }

        List<Map.Entry<ResolvedJavaMethod, Integer>> methods = new ArrayList<>(methodCounts.entrySet());
        methods.sort((e1, e2) -> -Integer.compare(e1.getValue(), e2.getValue()));

        StringBuilder msg = new StringBuilder("Too deep inlining, probably caused by recursive inlining.").append(System.lineSeparator()).append("== Inlined methods ordered by inlining frequency:");
        for (Map.Entry<ResolvedJavaMethod, Integer> entry : methods) {
            msg.append(System.lineSeparator()).append(entry.getKey().format("%H.%n(%p) [")).append(entry.getValue()).append("]");
        }
        msg.append(System.lineSeparator()).append("== Complete stack trace of inlined methods:");
        int lastBci = 0;
        for (PEMethodScope cur = methodScope; cur != null; cur = cur.caller) {
            msg.append(System.lineSeparator()).append(cur.method.asStackTraceElement(lastBci));
            if (cur.invokeData != null) {
                lastBci = cur.invokeData.invoke.bci();
            } else {
                lastBci = 0;
            }
        }

        throw new PermanentBailoutException(msg.toString());
    }

    public FixedNode nodeAfterInvoke(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, AbstractBeginNode lastBlock) {
        assert lastBlock.isAlive();
        FixedNode n;
        if (invokeData.invoke instanceof InvokeWithExceptionNode) {
            registerNode(loopScope, invokeData.nextOrderId, lastBlock, false, false);
            n = makeStubNode(methodScope, loopScope, invokeData.nextNextOrderId);
        } else {
            n = makeStubNode(methodScope, loopScope, invokeData.nextOrderId);
        }
        return n;
    }

    private static void deleteInvoke(Invoke invoke) {
        /*
         * Clean up unused nodes. We cannot just call killCFG on the invoke node because that can
         * kill too much: nodes that are decoded later can use values that appear unused by now.
         */
        FrameState frameState = invoke.stateAfter();
        invoke.asNode().safeDelete();
        assert invoke.callTarget() == null : "must not have been added to the graph yet";
        if (frameState != null && frameState.hasNoUsages()) {
            frameState.safeDelete();
        }
    }

    protected abstract EncodedGraph lookupEncodedGraph(ResolvedJavaMethod method, ResolvedJavaMethod originalMethod, BytecodeProvider intrinsicBytecodeProvider, boolean isSubstitution,
                    boolean trackNodeSourcePosition);

    @Override
    protected void handleFixedNode(MethodScope s, LoopScope loopScope, int nodeOrderId, FixedNode node) {
        PEMethodScope methodScope = (PEMethodScope) s;

        if (node instanceof ForeignCallNode) {
            ForeignCallNode foreignCall = (ForeignCallNode) node;
            if (foreignCall.getBci() == BytecodeFrame.UNKNOWN_BCI && methodScope.invokeData != null) {
                foreignCall.setBci(methodScope.invokeData.invoke.bci());
            }
        }

        super.handleFixedNode(methodScope, loopScope, nodeOrderId, node);
    }

    @SuppressWarnings("try")
    @Override
    protected Node canonicalizeFixedNode(MethodScope s, Node node) {
        PEMethodScope methodScope = (PEMethodScope) s;

        Node replacedNode = node;
        if (nodePlugins != null && nodePlugins.length > 0) {
            if (node instanceof LoadFieldNode) {
                LoadFieldNode loadFieldNode = (LoadFieldNode) node;
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(methodScope, loadFieldNode);
                ResolvedJavaField field = loadFieldNode.field();
                if (loadFieldNode.isStatic()) {
                    for (NodePlugin nodePlugin : nodePlugins) {
                        if (nodePlugin.handleLoadStaticField(graphBuilderContext, field)) {
                            replacedNode = graphBuilderContext.pushedNode;
                            break;
                        }
                    }
                } else {
                    ValueNode object = loadFieldNode.object();
                    for (NodePlugin nodePlugin : nodePlugins) {
                        if (nodePlugin.handleLoadField(graphBuilderContext, object, field)) {
                            replacedNode = graphBuilderContext.pushedNode;
                            break;
                        }
                    }
                }
            } else if (node instanceof StoreFieldNode) {
                StoreFieldNode storeFieldNode = (StoreFieldNode) node;
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(methodScope, storeFieldNode);
                ResolvedJavaField field = storeFieldNode.field();
                if (storeFieldNode.isStatic()) {
                    ValueNode value = storeFieldNode.value();
                    for (NodePlugin nodePlugin : nodePlugins) {
                        if (nodePlugin.handleStoreStaticField(graphBuilderContext, field, value)) {
                            replacedNode = graphBuilderContext.pushedNode;
                            break;
                        }
                    }
                } else {
                    ValueNode object = storeFieldNode.object();
                    ValueNode value = storeFieldNode.value();
                    for (NodePlugin nodePlugin : nodePlugins) {
                        if (nodePlugin.handleStoreField(graphBuilderContext, object, field, value)) {
                            replacedNode = graphBuilderContext.pushedNode;
                            break;
                        }
                    }
                }
            } else if (node instanceof LoadIndexedNode) {
                LoadIndexedNode loadIndexedNode = (LoadIndexedNode) node;
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(methodScope, loadIndexedNode);
                ValueNode array = loadIndexedNode.array();
                ValueNode index = loadIndexedNode.index();
                for (NodePlugin nodePlugin : nodePlugins) {
                    if (nodePlugin.handleLoadIndexed(graphBuilderContext, array, index, loadIndexedNode.getBoundsCheck(), loadIndexedNode.elementKind())) {
                        replacedNode = graphBuilderContext.pushedNode;
                        break;
                    }
                }
            } else if (node instanceof StoreIndexedNode) {
                StoreIndexedNode storeIndexedNode = (StoreIndexedNode) node;
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(methodScope, storeIndexedNode);
                ValueNode array = storeIndexedNode.array();
                ValueNode index = storeIndexedNode.index();
                ValueNode value = storeIndexedNode.value();
                for (NodePlugin nodePlugin : nodePlugins) {
                    if (nodePlugin.handleStoreIndexed(graphBuilderContext, array, index, storeIndexedNode.getBoundsCheck(), storeIndexedNode.getStoreCheck(), storeIndexedNode.elementKind(), value)) {
                        replacedNode = graphBuilderContext.pushedNode;
                        break;
                    }
                }
            } else if (node instanceof NewInstanceNode) {
                NewInstanceNode newInstanceNode = (NewInstanceNode) node;
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(methodScope, newInstanceNode);
                ResolvedJavaType type = newInstanceNode.instanceClass();
                for (NodePlugin nodePlugin : nodePlugins) {
                    if (nodePlugin.handleNewInstance(graphBuilderContext, type)) {
                        replacedNode = graphBuilderContext.pushedNode;
                        break;
                    }
                }
            } else if (node instanceof NewArrayNode) {
                NewArrayNode newArrayNode = (NewArrayNode) node;
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(methodScope, newArrayNode);
                ResolvedJavaType elementType = newArrayNode.elementType();
                ValueNode length = newArrayNode.length();
                for (NodePlugin nodePlugin : nodePlugins) {
                    if (nodePlugin.handleNewArray(graphBuilderContext, elementType, length)) {
                        replacedNode = graphBuilderContext.pushedNode;
                        break;
                    }
                }
            } else if (node instanceof NewMultiArrayNode) {
                NewMultiArrayNode newArrayNode = (NewMultiArrayNode) node;
                PEAppendGraphBuilderContext graphBuilderContext = new PEAppendGraphBuilderContext(methodScope, newArrayNode);
                ResolvedJavaType elementType = newArrayNode.type();
                ValueNode[] dimensions = newArrayNode.dimensions().toArray(new ValueNode[0]);
                for (NodePlugin nodePlugin : nodePlugins) {
                    if (nodePlugin.handleNewMultiArray(graphBuilderContext, elementType, dimensions)) {
                        replacedNode = graphBuilderContext.pushedNode;
                        break;
                    }
                }
            }
        }

        return super.canonicalizeFixedNode(methodScope, replacedNode);
    }

    @Override
    protected Node handleFloatingNodeBeforeAdd(MethodScope s, LoopScope loopScope, Node n) {
        PEMethodScope methodScope = (PEMethodScope) s;

        Node node = n;
        if (node instanceof ParameterNode) {
            ParameterNode param = (ParameterNode) node;
            if (methodScope.isInlinedMethod()) {
                throw GraalError.shouldNotReachHere("Parameter nodes are already registered when the inlined scope is created");

            } else if (parameterPlugin != null) {
                assert !methodScope.isInlinedMethod();
                GraphBuilderContext graphBuilderContext = new PENonAppendGraphBuilderContext(methodScope, null);
                Node result = parameterPlugin.interceptParameter(graphBuilderContext, param.index(),
                                StampPair.create(param.stamp(NodeView.DEFAULT), param.uncheckedStamp()));
                if (result != null) {
                    return result;
                }
            }
            node = param.copyWithInputs();
        }

        return super.handleFloatingNodeBeforeAdd(methodScope, loopScope, node);
    }

    protected void ensureOuterStateDecoded(PEMethodScope methodScope) {
        if (methodScope.outerState == null && methodScope.caller != null) {
            FrameState stateAtReturn = methodScope.invokeData.invoke.stateAfter();
            if (stateAtReturn == null) {
                stateAtReturn = (FrameState) decodeFloatingNode(methodScope.caller, methodScope.callerLoopScope, methodScope.invokeData.stateAfterOrderId);
            }

            JavaKind invokeReturnKind = methodScope.invokeData.invoke.asNode().getStackKind();
            FrameState outerState = stateAtReturn.duplicateModified(graph, methodScope.invokeData.invoke.bci(), stateAtReturn.rethrowException(), true, invokeReturnKind, null, null);

            /*
             * When the encoded graph has methods inlining, we can already have a proper caller
             * state. If not, we set the caller state here.
             */
            if (outerState.outerFrameState() == null && methodScope.caller != null) {
                ensureOuterStateDecoded(methodScope.caller);
                outerState.setOuterFrameState(methodScope.caller.outerState);
            }
            methodScope.outerState = outerState;
        }
    }

    protected void ensureStateAfterDecoded(PEMethodScope methodScope) {
        if (methodScope.invokeData.invoke.stateAfter() == null) {
            methodScope.invokeData.invoke.setStateAfter((FrameState) ensureNodeCreated(methodScope.caller, methodScope.callerLoopScope, methodScope.invokeData.stateAfterOrderId));
        }
    }

    protected void ensureExceptionStateDecoded(PEMethodScope methodScope) {
        if (methodScope.exceptionState == null && methodScope.caller != null && methodScope.invokeData.invoke instanceof InvokeWithExceptionNode) {
            ensureStateAfterDecoded(methodScope);

            assert methodScope.exceptionPlaceholderNode == null;
            methodScope.exceptionPlaceholderNode = graph.add(new ExceptionPlaceholderNode());
            registerNode(methodScope.callerLoopScope, methodScope.invokeData.exceptionOrderId, methodScope.exceptionPlaceholderNode, false, false);
            FrameState exceptionState = (FrameState) ensureNodeCreated(methodScope.caller, methodScope.callerLoopScope, methodScope.invokeData.exceptionStateOrderId);

            if (exceptionState.outerFrameState() == null && methodScope.caller != null) {
                ensureOuterStateDecoded(methodScope.caller);
                exceptionState.setOuterFrameState(methodScope.caller.outerState);
            }
            methodScope.exceptionState = exceptionState;
        }
    }

    @Override
    protected Node handleFloatingNodeAfterAdd(MethodScope s, LoopScope loopScope, Node node) {
        PEMethodScope methodScope = (PEMethodScope) s;

        if (methodScope.isInlinedMethod()) {
            if (node instanceof FrameState) {
                FrameState frameState = (FrameState) node;

                ensureOuterStateDecoded(methodScope);
                if (frameState.bci < 0) {
                    ensureExceptionStateDecoded(methodScope);
                }
                List<ValueNode> invokeArgsList = null;
                if (frameState.bci == BytecodeFrame.BEFORE_BCI) {
                    /*
                     * We know that the argument list is only used in this case, so avoid the List
                     * allocation for "normal" bcis.
                     */
                    invokeArgsList = Arrays.asList(methodScope.arguments);
                }
                return InliningUtil.processFrameState(frameState, methodScope.invokeData.invoke, null, methodScope.method, methodScope.exceptionState, methodScope.outerState, true,
                                methodScope.method, invokeArgsList);

            } else if (node instanceof MonitorIdNode) {
                ensureOuterStateDecoded(methodScope);
                InliningUtil.processMonitorId(methodScope.outerState, (MonitorIdNode) node);
                return node;
            }
        }

        return node;
    }
}
