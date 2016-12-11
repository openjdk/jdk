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
package org.graalvm.compiler.hotspot.meta;

import static org.graalvm.compiler.core.common.GraalOptions.AlwaysInlineVTableStubs;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.core.common.GraalOptions.InlineVTableStubs;
import static org.graalvm.compiler.core.common.GraalOptions.OmitHotExceptionStacktrace;
import static org.graalvm.compiler.core.common.LocationIdentity.any;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.OSR_MIGRATION_END;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.CLASS_KLASS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.CLASS_MIRROR_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.COMPRESSED_HUB_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.HUB_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.HUB_WRITE_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_LAYOUT_HELPER_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.OBJ_ARRAY_KLASS_ELEMENT_KLASS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.NewObjectSnippets.INIT_LOCATION;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayBaseOffset;

import java.lang.ref.Reference;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.nodes.CompressionNode;
import org.graalvm.compiler.hotspot.nodes.CompressionNode.CompressionOp;
import org.graalvm.compiler.hotspot.nodes.aot.InitializeKlassNode;
import org.graalvm.compiler.hotspot.nodes.aot.ResolveConstantNode;
import org.graalvm.compiler.hotspot.nodes.aot.ResolveMethodAndLoadCountersNode;
import org.graalvm.compiler.hotspot.nodes.profiling.ProfileNode;
import org.graalvm.compiler.hotspot.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.hotspot.nodes.G1ArrayRangePostWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.G1ArrayRangePreWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.G1PostWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.G1PreWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.G1ReferentFieldReadBarrier;
import org.graalvm.compiler.hotspot.nodes.GetObjectAddressNode;
import org.graalvm.compiler.hotspot.nodes.HotSpotDirectCallTargetNode;
import org.graalvm.compiler.hotspot.nodes.HotSpotIndirectCallTargetNode;
import org.graalvm.compiler.hotspot.nodes.SerialArrayRangeWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.SerialWriteBarrier;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.hotspot.nodes.type.MethodPointerStamp;
import org.graalvm.compiler.hotspot.nodes.type.NarrowOopStamp;
import org.graalvm.compiler.hotspot.replacements.AssertionSnippets;
import org.graalvm.compiler.hotspot.replacements.ClassGetHubNode;
import org.graalvm.compiler.hotspot.replacements.HashCodeSnippets;
import org.graalvm.compiler.hotspot.replacements.HubGetClassNode;
import org.graalvm.compiler.hotspot.replacements.IdentityHashCodeNode;
import org.graalvm.compiler.hotspot.replacements.InstanceOfSnippets;
import org.graalvm.compiler.hotspot.replacements.KlassLayoutHelperNode;
import org.graalvm.compiler.hotspot.replacements.LoadExceptionObjectSnippets;
import org.graalvm.compiler.hotspot.replacements.MonitorSnippets;
import org.graalvm.compiler.hotspot.replacements.NewObjectSnippets;
import org.graalvm.compiler.hotspot.replacements.StringToBytesSnippets;
import org.graalvm.compiler.hotspot.replacements.UnsafeLoadSnippets;
import org.graalvm.compiler.hotspot.replacements.WriteBarrierSnippets;
import org.graalvm.compiler.hotspot.replacements.aot.ResolveConstantSnippets;
import org.graalvm.compiler.hotspot.replacements.arraycopy.ArrayCopyNode;
import org.graalvm.compiler.hotspot.replacements.arraycopy.ArrayCopySlowPathNode;
import org.graalvm.compiler.hotspot.replacements.arraycopy.ArrayCopySnippets;
import org.graalvm.compiler.hotspot.replacements.arraycopy.ArrayCopyUnrollNode;
import org.graalvm.compiler.hotspot.replacements.arraycopy.UnsafeArrayCopySnippets;
import org.graalvm.compiler.hotspot.replacements.profiling.ProfileSnippets;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractDeoptimizeNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoweredCallTargetNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.RemNode;
import org.graalvm.compiler.nodes.debug.StringToBytesNode;
import org.graalvm.compiler.nodes.debug.VerifyHeapNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.extended.GuardedUnsafeLoadNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.extended.LoadMethodNode;
import org.graalvm.compiler.nodes.extended.OSRLocalNode;
import org.graalvm.compiler.nodes.extended.OSRStartNode;
import org.graalvm.compiler.nodes.extended.StoreHubNode;
import org.graalvm.compiler.nodes.extended.UnsafeLoadNode;
import org.graalvm.compiler.nodes.java.ClassIsAssignableFromNode;
import org.graalvm.compiler.nodes.java.DynamicNewArrayNode;
import org.graalvm.compiler.nodes.java.DynamicNewInstanceNode;
import org.graalvm.compiler.nodes.java.InstanceOfDynamicNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadExceptionObjectNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.MonitorExitNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.nodes.java.RawMonitorEnterNode;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.replacements.DefaultJavaLoweringProvider;
import org.graalvm.compiler.replacements.nodes.AssertionNode;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * HotSpot implementation of {@link LoweringProvider}.
 */
public class DefaultHotSpotLoweringProvider extends DefaultJavaLoweringProvider implements HotSpotLoweringProvider {

    protected final HotSpotGraalRuntimeProvider runtime;
    protected final HotSpotRegistersProvider registers;
    protected final HotSpotConstantReflectionProvider constantReflection;

    protected InstanceOfSnippets.Templates instanceofSnippets;
    protected NewObjectSnippets.Templates newObjectSnippets;
    protected MonitorSnippets.Templates monitorSnippets;
    protected WriteBarrierSnippets.Templates writeBarrierSnippets;
    protected LoadExceptionObjectSnippets.Templates exceptionObjectSnippets;
    protected UnsafeLoadSnippets.Templates unsafeLoadSnippets;
    protected AssertionSnippets.Templates assertionSnippets;
    protected ArrayCopySnippets.Templates arraycopySnippets;
    protected StringToBytesSnippets.Templates stringToBytesSnippets;
    protected HashCodeSnippets.Templates hashCodeSnippets;
    protected ResolveConstantSnippets.Templates resolveConstantSnippets;
    protected ProfileSnippets.Templates profileSnippets;

    public DefaultHotSpotLoweringProvider(HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, HotSpotRegistersProvider registers,
                    HotSpotConstantReflectionProvider constantReflection, TargetDescription target) {
        super(metaAccess, foreignCalls, target);
        this.runtime = runtime;
        this.registers = registers;
        this.constantReflection = constantReflection;
    }

    @Override
    public void initialize(HotSpotProviders providers, GraalHotSpotVMConfig config) {
        super.initialize(providers, providers.getSnippetReflection());

        assert target == providers.getCodeCache().getTarget();
        instanceofSnippets = new InstanceOfSnippets.Templates(providers, target);
        newObjectSnippets = new NewObjectSnippets.Templates(providers, target, config);
        monitorSnippets = new MonitorSnippets.Templates(providers, target, config.useFastLocking);
        writeBarrierSnippets = new WriteBarrierSnippets.Templates(providers, target, config.useCompressedOops ? config.getOopEncoding() : null);
        exceptionObjectSnippets = new LoadExceptionObjectSnippets.Templates(providers, target);
        unsafeLoadSnippets = new UnsafeLoadSnippets.Templates(providers, target);
        assertionSnippets = new AssertionSnippets.Templates(providers, target);
        arraycopySnippets = new ArrayCopySnippets.Templates(providers, target);
        stringToBytesSnippets = new StringToBytesSnippets.Templates(providers, target);
        hashCodeSnippets = new HashCodeSnippets.Templates(providers, target);
        if (GeneratePIC.getValue()) {
            resolveConstantSnippets = new ResolveConstantSnippets.Templates(providers, target);
            profileSnippets = new ProfileSnippets.Templates(providers, target);
        }
        providers.getReplacements().registerSnippetTemplateCache(new UnsafeArrayCopySnippets.Templates(providers, target));
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        StructuredGraph graph = (StructuredGraph) n.graph();
        if (n instanceof Invoke) {
            lowerInvoke((Invoke) n, tool, graph);
        } else if (n instanceof LoadMethodNode) {
            lowerLoadMethodNode((LoadMethodNode) n);
        } else if (n instanceof GetClassNode) {
            lowerGetClassNode((GetClassNode) n, tool, graph);
        } else if (n instanceof StoreHubNode) {
            lowerStoreHubNode((StoreHubNode) n, graph);
        } else if (n instanceof OSRStartNode) {
            lowerOSRStartNode((OSRStartNode) n);
        } else if (n instanceof BytecodeExceptionNode) {
            lowerBytecodeExceptionNode((BytecodeExceptionNode) n);
        } else if (n instanceof InstanceOfNode) {
            InstanceOfNode instanceOfNode = (InstanceOfNode) n;
            if (graph.getGuardsStage().areDeoptsFixed()) {
                instanceofSnippets.lower(instanceOfNode, tool);
            } else {
                if (instanceOfNode.allowsNull()) {
                    ValueNode object = instanceOfNode.getValue();
                    LogicNode newTypeCheck = graph.addOrUniqueWithInputs(InstanceOfNode.create(instanceOfNode.type(), object, instanceOfNode.profile(), instanceOfNode.getAnchor()));
                    LogicNode newNode = LogicNode.or(graph.unique(IsNullNode.create(object)), newTypeCheck, GraalDirectives.UNLIKELY_PROBABILITY);
                    instanceOfNode.replaceAndDelete(newNode);
                }
            }
        } else if (n instanceof InstanceOfDynamicNode) {
            InstanceOfDynamicNode instanceOfDynamicNode = (InstanceOfDynamicNode) n;
            if (graph.getGuardsStage().areDeoptsFixed()) {
                instanceofSnippets.lower(instanceOfDynamicNode, tool);
            } else {
                ValueNode mirror = instanceOfDynamicNode.getMirrorOrHub();
                if (mirror.stamp().getStackKind() == JavaKind.Object) {
                    ClassGetHubNode classGetHub = graph.unique(new ClassGetHubNode(mirror));
                    instanceOfDynamicNode.setMirror(classGetHub);
                }

                if (instanceOfDynamicNode.allowsNull()) {
                    ValueNode object = instanceOfDynamicNode.getObject();
                    LogicNode newTypeCheck = graph.addOrUniqueWithInputs(
                                    InstanceOfDynamicNode.create(graph.getAssumptions(), tool.getConstantReflection(), instanceOfDynamicNode.getMirrorOrHub(), object, false));
                    LogicNode newNode = LogicNode.or(graph.unique(IsNullNode.create(object)), newTypeCheck, GraalDirectives.UNLIKELY_PROBABILITY);
                    instanceOfDynamicNode.replaceAndDelete(newNode);
                }
            }
        } else if (n instanceof ClassIsAssignableFromNode) {
            if (graph.getGuardsStage().areDeoptsFixed()) {
                instanceofSnippets.lower((ClassIsAssignableFromNode) n, tool);
            }
        } else if (n instanceof NewInstanceNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                newObjectSnippets.lower((NewInstanceNode) n, registers, tool);
            }
        } else if (n instanceof DynamicNewInstanceNode) {
            DynamicNewInstanceNode newInstanceNode = (DynamicNewInstanceNode) n;
            if (newInstanceNode.getClassClass() == null) {
                JavaConstant classClassMirror = constantReflection.forObject(Class.class);
                ConstantNode classClass = ConstantNode.forConstant(classClassMirror, tool.getMetaAccess(), graph);
                newInstanceNode.setClassClass(classClass);
            }
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                newObjectSnippets.lower(newInstanceNode, registers, tool);
            }
        } else if (n instanceof NewArrayNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                newObjectSnippets.lower((NewArrayNode) n, registers, tool);
            }
        } else if (n instanceof DynamicNewArrayNode) {
            DynamicNewArrayNode dynamicNewArrayNode = (DynamicNewArrayNode) n;
            if (dynamicNewArrayNode.getVoidClass() == null) {
                JavaConstant voidClassMirror = constantReflection.forObject(void.class);
                ConstantNode voidClass = ConstantNode.forConstant(voidClassMirror, tool.getMetaAccess(), graph);
                dynamicNewArrayNode.setVoidClass(voidClass);
            }
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                newObjectSnippets.lower(dynamicNewArrayNode, registers, tool);
            }
        } else if (n instanceof VerifyHeapNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                newObjectSnippets.lower((VerifyHeapNode) n, registers, tool);
            }
        } else if (n instanceof RawMonitorEnterNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                monitorSnippets.lower((RawMonitorEnterNode) n, registers, tool);
            }
        } else if (n instanceof MonitorExitNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                monitorSnippets.lower((MonitorExitNode) n, tool);
            }
        } else if (n instanceof ArrayCopyNode) {
            arraycopySnippets.lower((ArrayCopyNode) n, tool);
        } else if (n instanceof ArrayCopySlowPathNode) {
            arraycopySnippets.lower((ArrayCopySlowPathNode) n, tool);
        } else if (n instanceof ArrayCopyUnrollNode) {
            arraycopySnippets.lower((ArrayCopyUnrollNode) n, tool);
        } else if (n instanceof G1PreWriteBarrier) {
            writeBarrierSnippets.lower((G1PreWriteBarrier) n, registers, tool);
        } else if (n instanceof G1PostWriteBarrier) {
            writeBarrierSnippets.lower((G1PostWriteBarrier) n, registers, tool);
        } else if (n instanceof G1ReferentFieldReadBarrier) {
            writeBarrierSnippets.lower((G1ReferentFieldReadBarrier) n, registers, tool);
        } else if (n instanceof SerialWriteBarrier) {
            writeBarrierSnippets.lower((SerialWriteBarrier) n, tool);
        } else if (n instanceof SerialArrayRangeWriteBarrier) {
            writeBarrierSnippets.lower((SerialArrayRangeWriteBarrier) n, tool);
        } else if (n instanceof G1ArrayRangePreWriteBarrier) {
            writeBarrierSnippets.lower((G1ArrayRangePreWriteBarrier) n, registers, tool);
        } else if (n instanceof G1ArrayRangePostWriteBarrier) {
            writeBarrierSnippets.lower((G1ArrayRangePostWriteBarrier) n, registers, tool);
        } else if (n instanceof NewMultiArrayNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                newObjectSnippets.lower((NewMultiArrayNode) n, tool);
            }
        } else if (n instanceof LoadExceptionObjectNode) {
            exceptionObjectSnippets.lower((LoadExceptionObjectNode) n, registers, tool);
        } else if (n instanceof AssertionNode) {
            assertionSnippets.lower((AssertionNode) n, tool);
        } else if (n instanceof StringToBytesNode) {
            if (graph.getGuardsStage().areDeoptsFixed()) {
                stringToBytesSnippets.lower((StringToBytesNode) n, tool);
            }
        } else if (n instanceof IntegerDivRemNode) {
            // Nothing to do for division nodes. The HotSpot signal handler catches divisions by
            // zero and the MIN_VALUE / -1 cases.
        } else if (n instanceof AbstractDeoptimizeNode || n instanceof UnwindNode || n instanceof RemNode || n instanceof SafepointNode) {
            /* No lowering, we generate LIR directly for these nodes. */
        } else if (n instanceof ClassGetHubNode) {
            lowerClassGetHubNode((ClassGetHubNode) n, tool);
        } else if (n instanceof HubGetClassNode) {
            lowerHubGetClassNode((HubGetClassNode) n, tool);
        } else if (n instanceof KlassLayoutHelperNode) {
            lowerKlassLayoutHelperNode((KlassLayoutHelperNode) n, tool);
        } else if (n instanceof ComputeObjectAddressNode) {
            if (graph.getGuardsStage().areFrameStatesAtDeopts()) {
                lowerComputeObjectAddressNode((ComputeObjectAddressNode) n);
            }
        } else if (n instanceof IdentityHashCodeNode) {
            hashCodeSnippets.lower((IdentityHashCodeNode) n, tool);
        } else if (n instanceof ResolveConstantNode) {
            resolveConstantSnippets.lower((ResolveConstantNode) n, tool);
        } else if (n instanceof ResolveMethodAndLoadCountersNode) {
            resolveConstantSnippets.lower((ResolveMethodAndLoadCountersNode) n, tool);
        } else if (n instanceof InitializeKlassNode) {
            resolveConstantSnippets.lower((InitializeKlassNode) n, tool);
        } else if (n instanceof ProfileNode) {
            profileSnippets.lower((ProfileNode) n, tool);
        } else {
            super.lower(n, tool);
        }
    }

    private static void lowerComputeObjectAddressNode(ComputeObjectAddressNode n) {
        /*
         * Lower the node into a ComputeObjectAddress node and an Add but ensure that it's below any
         * potential safepoints and above it's uses.
         */
        for (Node use : n.usages().snapshot()) {
            if (use instanceof FixedNode) {
                FixedNode fixed = (FixedNode) use;
                StructuredGraph graph = n.graph();
                GetObjectAddressNode address = graph.add(new GetObjectAddressNode(n.getObject()));
                graph.addBeforeFixed(fixed, address);
                AddNode add = graph.addOrUnique(new AddNode(address, n.getOffset()));
                use.replaceFirstInput(n, add);
            } else {
                throw GraalError.shouldNotReachHere("Unexpected floating use of ComputeObjectAddressNode " + n);
            }
        }
        GraphUtil.unlinkFixedNode(n);
        n.safeDelete();
    }

    private void lowerKlassLayoutHelperNode(KlassLayoutHelperNode n, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }
        StructuredGraph graph = n.graph();
        assert !n.getHub().isConstant();
        AddressNode address = createOffsetAddress(graph, n.getHub(), runtime.getVMConfig().klassLayoutHelperOffset);
        n.replaceAtUsagesAndDelete(graph.unique(new FloatingReadNode(address, KLASS_LAYOUT_HELPER_LOCATION, null, n.stamp(), n.getGuard(), BarrierType.NONE)));
    }

    private void lowerHubGetClassNode(HubGetClassNode n, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }

        StructuredGraph graph = n.graph();
        assert !n.getHub().isConstant();
        AddressNode address = createOffsetAddress(graph, n.getHub(), runtime.getVMConfig().classMirrorOffset);
        FloatingReadNode read = graph.unique(new FloatingReadNode(address, CLASS_MIRROR_LOCATION, null, n.stamp(), n.getGuard(), BarrierType.NONE));
        n.replaceAtUsagesAndDelete(read);
    }

    private void lowerClassGetHubNode(ClassGetHubNode n, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }

        StructuredGraph graph = n.graph();
        assert !n.getValue().isConstant();
        AddressNode address = createOffsetAddress(graph, n.getValue(), runtime.getVMConfig().klassOffset);
        FloatingReadNode read = graph.unique(new FloatingReadNode(address, CLASS_KLASS_LOCATION, null, n.stamp(), n.getGuard(), BarrierType.NONE));
        n.replaceAtUsagesAndDelete(read);
    }

    private void lowerInvoke(Invoke invoke, LoweringTool tool, StructuredGraph graph) {
        if (invoke.callTarget() instanceof MethodCallTargetNode) {
            MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
            NodeInputList<ValueNode> parameters = callTarget.arguments();
            ValueNode receiver = parameters.size() <= 0 ? null : parameters.get(0);
            if (!callTarget.isStatic() && receiver.stamp() instanceof ObjectStamp && !StampTool.isPointerNonNull(receiver)) {
                GuardingNode receiverNullCheck = createNullCheck(receiver, invoke.asNode(), tool);
                PiNode nonNullReceiver = graph.unique(new PiNode(receiver, ((ObjectStamp) receiver.stamp()).join(StampFactory.objectNonNull()), (ValueNode) receiverNullCheck));
                parameters.set(0, nonNullReceiver);
                receiver = nonNullReceiver;
            }
            JavaType[] signature = callTarget.targetMethod().getSignature().toParameterTypes(callTarget.isStatic() ? null : callTarget.targetMethod().getDeclaringClass());

            LoweredCallTargetNode loweredCallTarget = null;
            if (InlineVTableStubs.getValue() && callTarget.invokeKind().isIndirect() && (AlwaysInlineVTableStubs.getValue() || invoke.isPolymorphic())) {
                HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
                ResolvedJavaType receiverType = invoke.getReceiverType();
                if (hsMethod.isInVirtualMethodTable(receiverType)) {
                    JavaKind wordKind = runtime.getTarget().wordJavaKind;
                    ValueNode hub = createReadHub(graph, receiver, tool);

                    ReadNode metaspaceMethod = createReadVirtualMethod(graph, hub, hsMethod, receiverType);
                    // We use LocationNode.ANY_LOCATION for the reads that access the
                    // compiled code entry as HotSpot does not guarantee they are final
                    // values.
                    int methodCompiledEntryOffset = runtime.getVMConfig().methodCompiledEntryOffset;
                    AddressNode address = createOffsetAddress(graph, metaspaceMethod, methodCompiledEntryOffset);
                    ReadNode compiledEntry = graph.add(new ReadNode(address, any(), StampFactory.forKind(wordKind), BarrierType.NONE));

                    loweredCallTarget = graph.add(new HotSpotIndirectCallTargetNode(metaspaceMethod, compiledEntry, parameters.toArray(new ValueNode[parameters.size()]), callTarget.returnStamp(),
                                    signature, callTarget.targetMethod(),
                                    HotSpotCallingConventionType.JavaCall, callTarget.invokeKind()));

                    graph.addBeforeFixed(invoke.asNode(), metaspaceMethod);
                    graph.addAfterFixed(metaspaceMethod, compiledEntry);
                }
            }

            if (loweredCallTarget == null) {
                loweredCallTarget = graph.add(new HotSpotDirectCallTargetNode(parameters.toArray(new ValueNode[parameters.size()]), callTarget.returnStamp(),
                                signature, callTarget.targetMethod(),
                                HotSpotCallingConventionType.JavaCall,
                                callTarget.invokeKind()));
            }
            callTarget.replaceAndDelete(loweredCallTarget);
        }
    }

    @Override
    protected Stamp loadStamp(Stamp stamp, JavaKind kind, boolean compressible) {
        if (kind == JavaKind.Object && compressible && runtime.getVMConfig().useCompressedOops) {
            return NarrowOopStamp.compressed((ObjectStamp) stamp, runtime.getVMConfig().getOopEncoding());
        }
        return super.loadStamp(stamp, kind, compressible);
    }

    @Override
    protected ValueNode implicitLoadConvert(JavaKind kind, ValueNode value, boolean compressible) {
        if (kind == JavaKind.Object && compressible && runtime.getVMConfig().useCompressedOops) {
            return new CompressionNode(CompressionOp.Uncompress, value, runtime.getVMConfig().getOopEncoding());
        }
        return super.implicitLoadConvert(kind, value, compressible);
    }

    @Override
    public ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField f) {
        HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) f;
        JavaConstant base = constantReflection.asJavaClass(field.getDeclaringClass());
        return ConstantNode.forConstant(base, metaAccess, graph);
    }

    @Override
    protected ValueNode implicitStoreConvert(JavaKind kind, ValueNode value, boolean compressible) {
        if (kind == JavaKind.Object && compressible && runtime.getVMConfig().useCompressedOops) {
            return new CompressionNode(CompressionOp.Compress, value, runtime.getVMConfig().getOopEncoding());
        }
        return super.implicitStoreConvert(kind, value, compressible);
    }

    @Override
    protected ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, FixedNode anchor) {
        /*
         * Anchor the read of the element klass to the cfg, because it is only valid when arrayClass
         * is an object class, which might not be the case in other parts of the compiled method.
         */
        AddressNode address = createOffsetAddress(graph, arrayHub, runtime.getVMConfig().arrayClassElementOffset);
        return graph.unique(new FloatingReadNode(address, OBJ_ARRAY_KLASS_ELEMENT_KLASS_LOCATION, null, KlassPointerStamp.klassNonNull(), AbstractBeginNode.prevBegin(anchor)));
    }

    @Override
    protected void lowerUnsafeLoadNode(UnsafeLoadNode load, LoweringTool tool) {
        StructuredGraph graph = load.graph();
        if (!(load instanceof GuardedUnsafeLoadNode) && !graph.getGuardsStage().allowsFloatingGuards() && addReadBarrier(load)) {
            unsafeLoadSnippets.lower(load, tool);
        } else {
            super.lowerUnsafeLoadNode(load, tool);
        }
    }

    private void lowerLoadMethodNode(LoadMethodNode loadMethodNode) {
        StructuredGraph graph = loadMethodNode.graph();
        HotSpotResolvedJavaMethod method = (HotSpotResolvedJavaMethod) loadMethodNode.getMethod();
        ReadNode metaspaceMethod = createReadVirtualMethod(graph, loadMethodNode.getHub(), method, loadMethodNode.getReceiverType());
        graph.replaceFixed(loadMethodNode, metaspaceMethod);
    }

    private static void lowerGetClassNode(GetClassNode getClass, LoweringTool tool, StructuredGraph graph) {
        StampProvider stampProvider = tool.getStampProvider();
        LoadHubNode hub = graph.unique(new LoadHubNode(stampProvider, getClass.getObject()));
        HubGetClassNode hubGetClass = graph.unique(new HubGetClassNode(tool.getMetaAccess(), hub));
        getClass.replaceAtUsagesAndDelete(hubGetClass);
        hub.lower(tool);
        hubGetClass.lower(tool);
    }

    private void lowerStoreHubNode(StoreHubNode storeHub, StructuredGraph graph) {
        WriteNode hub = createWriteHub(graph, storeHub.getObject(), storeHub.getValue());
        graph.replaceFixed(storeHub, hub);
    }

    @Override
    public BarrierType fieldInitializationBarrier(JavaKind entryKind) {
        return (entryKind == JavaKind.Object && !runtime.getVMConfig().useDeferredInitBarriers) ? BarrierType.IMPRECISE : BarrierType.NONE;
    }

    @Override
    public BarrierType arrayInitializationBarrier(JavaKind entryKind) {
        return (entryKind == JavaKind.Object && !runtime.getVMConfig().useDeferredInitBarriers) ? BarrierType.PRECISE : BarrierType.NONE;
    }

    private void lowerOSRStartNode(OSRStartNode osrStart) {
        StructuredGraph graph = osrStart.graph();
        if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
            StartNode newStart = graph.add(new StartNode());
            ParameterNode buffer = graph.addWithoutUnique(new ParameterNode(0, StampPair.createSingle(StampFactory.forKind(runtime.getTarget().wordJavaKind))));
            ForeignCallNode migrationEnd = graph.add(new ForeignCallNode(foreignCalls, OSR_MIGRATION_END, buffer));
            migrationEnd.setStateAfter(osrStart.stateAfter());

            newStart.setNext(migrationEnd);
            FixedNode next = osrStart.next();
            osrStart.setNext(null);
            migrationEnd.setNext(next);
            graph.setStart(newStart);

            // mirroring the calculations in c1_GraphBuilder.cpp (setup_osr_entry_block)
            int localsOffset = (graph.method().getMaxLocals() - 1) * 8;
            for (OSRLocalNode osrLocal : graph.getNodes(OSRLocalNode.TYPE)) {
                int size = osrLocal.getStackKind().getSlotCount();
                int offset = localsOffset - (osrLocal.index() + size - 1) * 8;
                AddressNode address = createOffsetAddress(graph, buffer, offset);
                ReadNode load = graph.add(new ReadNode(address, any(), osrLocal.stamp(), BarrierType.NONE));
                osrLocal.replaceAndDelete(load);
                graph.addBeforeFixed(migrationEnd, load);
            }
            osrStart.replaceAtUsagesAndDelete(newStart);
        }
    }

    static final class Exceptions {
        protected static final ArrayIndexOutOfBoundsException cachedArrayIndexOutOfBoundsException;
        protected static final NullPointerException cachedNullPointerException;

        static {
            cachedArrayIndexOutOfBoundsException = new ArrayIndexOutOfBoundsException();
            cachedArrayIndexOutOfBoundsException.setStackTrace(new StackTraceElement[0]);
            cachedNullPointerException = new NullPointerException();
            cachedNullPointerException.setStackTrace(new StackTraceElement[0]);
        }
    }

    public static final class RuntimeCalls {
        public static final ForeignCallDescriptor CREATE_ARRAY_STORE_EXCEPTION = new ForeignCallDescriptor("createArrayStoreException", ArrayStoreException.class, Object.class);
        public static final ForeignCallDescriptor CREATE_CLASS_CAST_EXCEPTION = new ForeignCallDescriptor("createClassCastException", ClassCastException.class, Object.class, KlassPointer.class);
        public static final ForeignCallDescriptor CREATE_NULL_POINTER_EXCEPTION = new ForeignCallDescriptor("createNullPointerException", NullPointerException.class);
        public static final ForeignCallDescriptor CREATE_OUT_OF_BOUNDS_EXCEPTION = new ForeignCallDescriptor("createOutOfBoundsException", ArrayIndexOutOfBoundsException.class, int.class);
    }

    private boolean throwCachedException(BytecodeExceptionNode node) {
        Throwable exception;
        if (node.getExceptionClass() == NullPointerException.class) {
            exception = Exceptions.cachedNullPointerException;
        } else if (node.getExceptionClass() == ArrayIndexOutOfBoundsException.class) {
            exception = Exceptions.cachedArrayIndexOutOfBoundsException;
        } else {
            return false;
        }

        StructuredGraph graph = node.graph();
        FloatingNode exceptionNode = ConstantNode.forConstant(constantReflection.forObject(exception), metaAccess, graph);
        graph.replaceFixedWithFloating(node, exceptionNode);
        return true;
    }

    private void lowerBytecodeExceptionNode(BytecodeExceptionNode node) {
        if (OmitHotExceptionStacktrace.getValue()) {
            if (throwCachedException(node)) {
                return;
            }
        }

        ForeignCallDescriptor descriptor;
        if (node.getExceptionClass() == NullPointerException.class) {
            descriptor = RuntimeCalls.CREATE_NULL_POINTER_EXCEPTION;
        } else if (node.getExceptionClass() == ArrayIndexOutOfBoundsException.class) {
            descriptor = RuntimeCalls.CREATE_OUT_OF_BOUNDS_EXCEPTION;
        } else if (node.getExceptionClass() == ArrayStoreException.class) {
            descriptor = RuntimeCalls.CREATE_ARRAY_STORE_EXCEPTION;
        } else if (node.getExceptionClass() == ClassCastException.class) {
            descriptor = RuntimeCalls.CREATE_CLASS_CAST_EXCEPTION;
        } else {
            throw GraalError.shouldNotReachHere();
        }

        StructuredGraph graph = node.graph();
        ForeignCallNode foreignCallNode = graph.add(new ForeignCallNode(foreignCalls, descriptor, node.stamp(), node.getArguments()));
        graph.replaceFixedWithFixed(node, foreignCallNode);
    }

    private boolean addReadBarrier(UnsafeLoadNode load) {
        if (runtime.getVMConfig().useG1GC && load.graph().getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS && load.object().getStackKind() == JavaKind.Object &&
                        load.accessKind() == JavaKind.Object && !StampTool.isPointerAlwaysNull(load.object())) {
            ResolvedJavaType type = StampTool.typeOrNull(load.object());
            if (type != null && !type.isArray()) {
                return true;
            }
        }
        return false;
    }

    private ReadNode createReadVirtualMethod(StructuredGraph graph, ValueNode hub, HotSpotResolvedJavaMethod method, ResolvedJavaType receiverType) {
        return createReadVirtualMethod(graph, hub, method.vtableEntryOffset(receiverType));
    }

    private ReadNode createReadVirtualMethod(StructuredGraph graph, ValueNode hub, int vtableEntryOffset) {
        assert vtableEntryOffset > 0;
        // We use LocationNode.ANY_LOCATION for the reads that access the vtable
        // entry as HotSpot does not guarantee that this is a final value.
        Stamp methodStamp = MethodPointerStamp.method();
        AddressNode address = createOffsetAddress(graph, hub, vtableEntryOffset);
        ReadNode metaspaceMethod = graph.add(new ReadNode(address, any(), methodStamp, BarrierType.NONE));
        return metaspaceMethod;
    }

    @Override
    protected ValueNode createReadHub(StructuredGraph graph, ValueNode object, LoweringTool tool) {
        if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
            return graph.unique(new LoadHubNode(tool.getStampProvider(), object));
        }
        assert !object.isConstant() || object.isNullConstant();

        KlassPointerStamp hubStamp = KlassPointerStamp.klassNonNull();
        if (runtime.getVMConfig().useCompressedClassPointers) {
            hubStamp = hubStamp.compressed(runtime.getVMConfig().getKlassEncoding());
        }

        AddressNode address = createOffsetAddress(graph, object, runtime.getVMConfig().hubOffset);
        LocationIdentity hubLocation = runtime.getVMConfig().useCompressedClassPointers ? COMPRESSED_HUB_LOCATION : HUB_LOCATION;
        FloatingReadNode memoryRead = graph.unique(new FloatingReadNode(address, hubLocation, null, hubStamp, null, BarrierType.NONE));
        if (runtime.getVMConfig().useCompressedClassPointers) {
            return CompressionNode.uncompress(memoryRead, runtime.getVMConfig().getKlassEncoding());
        } else {
            return memoryRead;
        }
    }

    private WriteNode createWriteHub(StructuredGraph graph, ValueNode object, ValueNode value) {
        assert !object.isConstant() || object.asConstant().isDefaultForKind();

        ValueNode writeValue = value;
        if (runtime.getVMConfig().useCompressedClassPointers) {
            writeValue = CompressionNode.compress(value, runtime.getVMConfig().getKlassEncoding());
        }

        AddressNode address = createOffsetAddress(graph, object, runtime.getVMConfig().hubOffset);
        return graph.add(new WriteNode(address, HUB_WRITE_LOCATION, writeValue, BarrierType.NONE));
    }

    @Override
    protected BarrierType fieldLoadBarrierType(ResolvedJavaField f) {
        HotSpotResolvedJavaField loadField = (HotSpotResolvedJavaField) f;
        BarrierType barrierType = BarrierType.NONE;
        if (runtime.getVMConfig().useG1GC && loadField.getJavaKind() == JavaKind.Object && metaAccess.lookupJavaType(Reference.class).equals(loadField.getDeclaringClass()) &&
                        loadField.getName().equals("referent")) {
            barrierType = BarrierType.PRECISE;
        }
        return barrierType;
    }

    @Override
    public int fieldOffset(ResolvedJavaField f) {
        HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) f;
        return field.offset();
    }

    @Override
    public int arrayScalingFactor(JavaKind kind) {
        if (runtime.getVMConfig().useCompressedOops && kind == JavaKind.Object) {
            return super.arrayScalingFactor(JavaKind.Int);
        } else {
            return super.arrayScalingFactor(kind);
        }
    }

    @Override
    public int arrayBaseOffset(JavaKind kind) {
        return getArrayBaseOffset(kind);
    }

    @Override
    public int arrayLengthOffset() {
        return runtime.getVMConfig().arrayOopDescLengthOffset();
    }

    @Override
    public LocationIdentity initLocationIdentity() {
        return INIT_LOCATION;
    }
}
