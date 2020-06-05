/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.MemoryBarriers.JMM_POST_VOLATILE_READ;
import static jdk.vm.ci.code.MemoryBarriers.JMM_POST_VOLATILE_WRITE;
import static jdk.vm.ci.code.MemoryBarriers.JMM_PRE_VOLATILE_READ;
import static jdk.vm.ci.code.MemoryBarriers.JMM_PRE_VOLATILE_WRITE;
import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.BoundsCheckException;
import static jdk.vm.ci.meta.DeoptimizationReason.NullCheckException;
import static org.graalvm.compiler.core.common.SpeculativeExecutionAttacksMitigations.Options.UseIndexMasking;
import static org.graalvm.compiler.nodes.NamedLocationIdentity.ARRAY_LENGTH_LOCATION;
import static org.graalvm.compiler.nodes.calc.BinaryArithmeticNode.branchlessMax;
import static org.graalvm.compiler.nodes.calc.BinaryArithmeticNode.branchlessMin;
import static org.graalvm.compiler.nodes.java.ArrayLengthNode.readArrayLength;
import static org.graalvm.compiler.nodes.util.GraphUtil.skipPiWhileNonNull;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.CompressionNode.CompressionOp;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnpackEndianHalfNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.debug.VerifyHeapNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.FixedValueAnchorNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.GuardedUnsafeLoadNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.extended.LoadArrayComponentHubNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.extended.LoadHubOrNullNode;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.extended.UnsafeMemoryLoadNode;
import org.graalvm.compiler.nodes.extended.UnsafeMemoryStoreNode;
import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.java.AccessIndexedNode;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.java.FinalFieldBarrierNode;
import org.graalvm.compiler.nodes.java.InstanceOfDynamicNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.LogicCompareAndSwapNode;
import org.graalvm.compiler.nodes.java.LoweredAtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.java.MonitorEnterNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.java.UnsafeCompareAndExchangeNode;
import org.graalvm.compiler.nodes.java.UnsafeCompareAndSwapNode;
import org.graalvm.compiler.nodes.java.ValueCompareAndSwapNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.SideEffectFreeWrite;
import org.graalvm.compiler.nodes.memory.VolatileReadNode;
import org.graalvm.compiler.nodes.memory.VolatileWriteNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.IndexAddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetLowerableMemoryNode.SnippetLowering;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.MemoryBarriers;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * VM-independent lowerings for standard Java nodes. VM-specific methods are abstract and must be
 * implemented by VM-specific subclasses.
 */
public abstract class DefaultJavaLoweringProvider implements LoweringProvider {

    protected final MetaAccessProvider metaAccess;
    protected final ForeignCallsProvider foreignCalls;
    protected final BarrierSet barrierSet;
    protected final MetaAccessExtensionProvider metaAccessExtensionProvider;
    protected final TargetDescription target;
    private final boolean useCompressedOops;
    protected Replacements replacements;

    private BoxingSnippets.Templates boxingSnippets;
    private ConstantStringIndexOfSnippets.Templates indexOfSnippets;

    public DefaultJavaLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider,
                    TargetDescription target, boolean useCompressedOops) {
        this.metaAccess = metaAccess;
        this.foreignCalls = foreignCalls;
        this.barrierSet = platformConfig.getBarrierSet();
        this.metaAccessExtensionProvider = metaAccessExtensionProvider;
        this.target = target;
        this.useCompressedOops = useCompressedOops;
    }

    public void initialize(OptionValues options, Iterable<DebugHandlersFactory> factories, SnippetCounter.Group.Factory factory, Providers providers, SnippetReflectionProvider snippetReflection) {
        boxingSnippets = new BoxingSnippets.Templates(options, factories, factory, providers, snippetReflection, target);
        indexOfSnippets = new ConstantStringIndexOfSnippets.Templates(options, factories, providers, snippetReflection, target);
        replacements = providers.getReplacements();
        providers.getReplacements().registerSnippetTemplateCache(new SnippetCounterNode.SnippetCounterSnippets.Templates(options, factories, providers, snippetReflection, target));
    }

    public final TargetDescription getTarget() {
        return target;
    }

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    public BarrierSet getBarrierSet() {
        return barrierSet;
    }

    public MetaAccessExtensionProvider getMetaAccessExtensionProvider() {
        return metaAccessExtensionProvider;
    }

    public Replacements getReplacements() {
        return replacements;
    }

    @Override
    @SuppressWarnings("try")
    public void lower(Node n, LoweringTool tool) {
        assert n instanceof Lowerable;
        StructuredGraph graph = (StructuredGraph) n.graph();
        try (DebugCloseable context = n.withNodeSourcePosition()) {
            if (n instanceof LoadFieldNode) {
                lowerLoadFieldNode((LoadFieldNode) n, tool);
            } else if (n instanceof StoreFieldNode) {
                lowerStoreFieldNode((StoreFieldNode) n, tool);
            } else if (n instanceof LoadIndexedNode) {
                lowerLoadIndexedNode((LoadIndexedNode) n, tool);
            } else if (n instanceof StoreIndexedNode) {
                lowerStoreIndexedNode((StoreIndexedNode) n, tool);
            } else if (n instanceof IndexAddressNode) {
                lowerIndexAddressNode((IndexAddressNode) n);
            } else if (n instanceof ArrayLengthNode) {
                lowerArrayLengthNode((ArrayLengthNode) n, tool);
            } else if (n instanceof LoadHubNode) {
                lowerLoadHubNode((LoadHubNode) n, tool);
            } else if (n instanceof LoadHubOrNullNode) {
                lowerLoadHubOrNullNode((LoadHubOrNullNode) n, tool);
            } else if (n instanceof LoadArrayComponentHubNode) {
                lowerLoadArrayComponentHubNode((LoadArrayComponentHubNode) n);
            } else if (n instanceof UnsafeCompareAndSwapNode) {
                lowerCompareAndSwapNode((UnsafeCompareAndSwapNode) n);
            } else if (n instanceof UnsafeCompareAndExchangeNode) {
                lowerCompareAndExchangeNode((UnsafeCompareAndExchangeNode) n);
            } else if (n instanceof AtomicReadAndWriteNode) {
                lowerAtomicReadAndWriteNode((AtomicReadAndWriteNode) n);
            } else if (n instanceof RawLoadNode) {
                lowerUnsafeLoadNode((RawLoadNode) n, tool);
            } else if (n instanceof UnsafeMemoryLoadNode) {
                lowerUnsafeMemoryLoadNode((UnsafeMemoryLoadNode) n);
            } else if (n instanceof RawStoreNode) {
                lowerUnsafeStoreNode((RawStoreNode) n);
            } else if (n instanceof UnsafeMemoryStoreNode) {
                lowerUnsafeMemoryStoreNode((UnsafeMemoryStoreNode) n);
            } else if (n instanceof JavaReadNode) {
                lowerJavaReadNode((JavaReadNode) n);
            } else if (n instanceof JavaWriteNode) {
                lowerJavaWriteNode((JavaWriteNode) n);
            } else if (n instanceof CommitAllocationNode) {
                lowerCommitAllocationNode((CommitAllocationNode) n, tool);
            } else if (n instanceof BoxNode) {
                if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
                    /*
                     * We do not perform box canonicalization directly in the node since want
                     * virtualization of box nodes. Creating a boxed constant early on inhibits PEA
                     * so we do it after PEA.
                     */
                    FloatingNode canonical = canonicalizeBoxing((BoxNode) n, metaAccess, tool.getConstantReflection());
                    if (canonical != null) {
                        n.replaceAtUsages((ValueNode) ((BoxNode) n).getLastLocationAccess(), InputType.Memory);
                        graph.replaceFixedWithFloating((FixedWithNextNode) n, canonical);
                    }
                } else if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.MID_TIER) {
                    boxingSnippets.lower((BoxNode) n, tool);
                }
            } else if (n instanceof UnboxNode) {
                if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.MID_TIER) {
                    boxingSnippets.lower((UnboxNode) n, tool);
                }
            } else if (n instanceof VerifyHeapNode) {
                lowerVerifyHeap((VerifyHeapNode) n);
            } else if (n instanceof UnaryMathIntrinsicNode) {
                lowerUnaryMath((UnaryMathIntrinsicNode) n, tool);
            } else if (n instanceof BinaryMathIntrinsicNode) {
                lowerBinaryMath((BinaryMathIntrinsicNode) n, tool);
            } else if (n instanceof StringIndexOfNode) {
                lowerIndexOf((StringIndexOfNode) n);
            } else if (n instanceof StringLatin1IndexOfNode) {
                lowerLatin1IndexOf((StringLatin1IndexOfNode) n);
            } else if (n instanceof StringUTF16IndexOfNode) {
                lowerUTF16IndexOf((StringUTF16IndexOfNode) n);
            } else if (n instanceof UnpackEndianHalfNode) {
                lowerSecondHalf((UnpackEndianHalfNode) n);
            } else if (n instanceof VolatileReadNode) {
                lowerVolatileRead(((VolatileReadNode) n), tool);
            } else if (n instanceof VolatileWriteNode) {
                lowerVolatileWrite(((VolatileWriteNode) n), tool);
            } else {
                throw GraalError.shouldNotReachHere("Node implementing Lowerable not handled: " + n);
            }
        }
    }

    public static FloatingNode canonicalizeBoxing(BoxNode box, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        ValueNode value = box.getValue();
        if (value.isConstant() && !GraalOptions.ImmutableCode.getValue(box.getOptions())) {
            JavaConstant sourceConstant = value.asJavaConstant();
            if (sourceConstant.getJavaKind() != box.getBoxingKind() && sourceConstant.getJavaKind().isNumericInteger()) {
                switch (box.getBoxingKind()) {
                    case Boolean:
                        sourceConstant = JavaConstant.forBoolean(sourceConstant.asLong() != 0L);
                        break;
                    case Byte:
                        sourceConstant = JavaConstant.forByte((byte) sourceConstant.asLong());
                        break;
                    case Char:
                        sourceConstant = JavaConstant.forChar((char) sourceConstant.asLong());
                        break;
                    case Short:
                        sourceConstant = JavaConstant.forShort((short) sourceConstant.asLong());
                        break;
                }
            }
            JavaConstant boxedConstant = constantReflection.boxPrimitive(sourceConstant);
            if (boxedConstant != null && sourceConstant.getJavaKind() == box.getBoxingKind()) {
                return ConstantNode.forConstant(boxedConstant, metaAccess, box.graph());
            }
        }
        return null;
    }

    private void lowerSecondHalf(UnpackEndianHalfNode n) {
        ByteOrder byteOrder = target.arch.getByteOrder();
        n.lower(byteOrder);
    }

    private void lowerIndexOf(StringIndexOfNode n) {
        if (n.getArgument(3).isConstant()) {
            SnippetLowering lowering = new SnippetLowering() {
                @Override
                public void lower(SnippetLowerableMemoryNode node, LoweringTool tool) {
                    if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
                        return;
                    }
                    indexOfSnippets.lower(node, tool);
                }
            };
            SnippetLowerableMemoryNode snippetLower = new SnippetLowerableMemoryNode(lowering, NamedLocationIdentity.getArrayLocation(JavaKind.Char), n.stamp(NodeView.DEFAULT), n.toArgumentArray());
            n.graph().add(snippetLower);
            n.graph().replaceFixedWithFixed(n, snippetLower);
        }
    }

    private void lowerLatin1IndexOf(StringLatin1IndexOfNode n) {
        if (n.getArgument(2).isConstant()) {
            SnippetLowering lowering = new SnippetLowering() {
                @Override
                public void lower(SnippetLowerableMemoryNode node, LoweringTool tool) {
                    if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
                        return;
                    }
                    indexOfSnippets.lowerLatin1(node, tool);
                }
            };
            SnippetLowerableMemoryNode snippetLower = new SnippetLowerableMemoryNode(lowering, NamedLocationIdentity.getArrayLocation(JavaKind.Byte), n.stamp(NodeView.DEFAULT), n.toArgumentArray());
            n.graph().add(snippetLower);
            n.graph().replaceFixedWithFixed(n, snippetLower);
        }
    }

    private void lowerUTF16IndexOf(StringUTF16IndexOfNode n) {
        if (n.getArgument(2).isConstant()) {
            SnippetLowering lowering = new SnippetLowering() {
                @Override
                public void lower(SnippetLowerableMemoryNode node, LoweringTool tool) {
                    if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
                        return;
                    }
                    indexOfSnippets.lowerUTF16(node, tool);
                }
            };
            SnippetLowerableMemoryNode snippetLower = new SnippetLowerableMemoryNode(lowering, NamedLocationIdentity.getArrayLocation(JavaKind.Byte), n.stamp(NodeView.DEFAULT), n.toArgumentArray());
            n.graph().add(snippetLower);
            n.graph().replaceFixedWithFixed(n, snippetLower);
        }
    }

    private void lowerBinaryMath(BinaryMathIntrinsicNode math, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }
        ResolvedJavaMethod method = math.graph().method();
        if (method != null) {
            if (replacements.isSnippet(method)) {
                // In the context of SnippetStub, i.e., Graal-generated stubs, use the LIR
                // lowering to emit the stub assembly code instead of the Node lowering.
                return;
            }
            if (method.getName().equalsIgnoreCase(math.getOperation().name()) && tool.getMetaAccess().lookupJavaType(Math.class).equals(method.getDeclaringClass())) {
                // A root compilation of the intrinsic method should emit the full assembly
                // implementation.
                return;
            }
        }
        StructuredGraph graph = math.graph();
        ForeignCallNode call = graph.add(new ForeignCallNode(foreignCalls, math.getOperation().foreignCallSignature, math.getX(), math.getY()));
        graph.addAfterFixed(tool.lastFixedNode(), call);
        math.replaceAtUsages(call);
    }

    private void lowerUnaryMath(UnaryMathIntrinsicNode math, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }
        ResolvedJavaMethod method = math.graph().method();
        if (method != null) {
            if (method.getName().equalsIgnoreCase(math.getOperation().name()) && tool.getMetaAccess().lookupJavaType(Math.class).equals(method.getDeclaringClass())) {
                // A root compilation of the intrinsic method should emit the full assembly
                // implementation.
                return;
            }
        }
        StructuredGraph graph = math.graph();
        ForeignCallNode call = math.graph().add(new ForeignCallNode(foreignCalls.getDescriptor(math.getOperation().foreignCallSignature), math.getValue()));
        graph.addAfterFixed(tool.lastFixedNode(), call);
        math.replaceAtUsages(call);
    }

    protected void lowerVerifyHeap(VerifyHeapNode n) {
        GraphUtil.removeFixedWithUnusedInputs(n);
    }

    public AddressNode createOffsetAddress(StructuredGraph graph, ValueNode object, long offset) {
        ValueNode o = ConstantNode.forIntegerKind(target.wordJavaKind, offset, graph);
        return graph.unique(new OffsetAddressNode(object, o));
    }

    public AddressNode createFieldAddress(StructuredGraph graph, ValueNode object, ResolvedJavaField field) {
        int offset = fieldOffset(field);
        if (offset >= 0) {
            return createOffsetAddress(graph, object, offset);
        } else {
            return null;
        }
    }

    public final JavaKind getStorageKind(ResolvedJavaField field) {
        return metaAccessExtensionProvider.getStorageKind(field.getType());
    }

    protected void lowerLoadFieldNode(LoadFieldNode loadField, LoweringTool tool) {
        assert loadField.getStackKind() != JavaKind.Illegal;
        StructuredGraph graph = loadField.graph();
        ResolvedJavaField field = loadField.field();
        ValueNode object = loadField.isStatic() ? staticFieldBase(graph, field) : loadField.object();
        object = createNullCheckedValue(object, loadField, tool);
        Stamp loadStamp = loadStamp(loadField.stamp(NodeView.DEFAULT), getStorageKind(field));

        AddressNode address = createFieldAddress(graph, object, field);
        assert address != null : "Field that is loaded must not be eliminated: " + field.getDeclaringClass().toJavaName(true) + "." + field.getName();

        ReadNode memoryRead = null;
        BarrierType barrierType = barrierSet.fieldLoadBarrierType(field, getStorageKind(field));
        if (loadField.isVolatile()) {
            memoryRead = graph.add(new VolatileReadNode(address, fieldLocationIdentity(field), loadStamp, barrierType));
        } else {
            memoryRead = graph.add(new ReadNode(address, fieldLocationIdentity(field), loadStamp, barrierType));
        }
        ValueNode readValue = implicitLoadConvert(graph, getStorageKind(field), memoryRead);
        loadField.replaceAtUsages(readValue);
        graph.replaceFixed(loadField, memoryRead);
    }

    protected void lowerStoreFieldNode(StoreFieldNode storeField, LoweringTool tool) {
        StructuredGraph graph = storeField.graph();
        ResolvedJavaField field = storeField.field();
        ValueNode object = storeField.isStatic() ? staticFieldBase(graph, field) : storeField.object();
        object = createNullCheckedValue(object, storeField, tool);
        ValueNode value = implicitStoreConvert(graph, getStorageKind(storeField.field()), storeField.value());
        AddressNode address = createFieldAddress(graph, object, field);
        assert address != null;

        BarrierType barrierType = barrierSet.fieldStoreBarrierType(field, getStorageKind(field));
        WriteNode memoryWrite = null;
        if (storeField.isVolatile()) {
            memoryWrite = graph.add(new VolatileWriteNode(address, fieldLocationIdentity(field), value, barrierType));
        } else {
            memoryWrite = graph.add(new WriteNode(address, fieldLocationIdentity(field), value, barrierType));
        }
        memoryWrite.setStateAfter(storeField.stateAfter());
        graph.replaceFixedWithFixed(storeField, memoryWrite);
    }

    public static final IntegerStamp POSITIVE_ARRAY_INDEX_STAMP = StampFactory.forInteger(32, 0, Integer.MAX_VALUE - 1);

    /**
     * Create a PiNode on the index proving that the index is positive. On some platforms this is
     * important to allow the index to be used as an int in the address mode.
     */
    public AddressNode createArrayIndexAddress(StructuredGraph graph, ValueNode array, JavaKind elementKind, ValueNode index, GuardingNode boundsCheck) {
        ValueNode positiveIndex = graph.maybeAddOrUnique(PiNode.create(index, POSITIVE_ARRAY_INDEX_STAMP, boundsCheck != null ? boundsCheck.asNode() : null));
        return createArrayAddress(graph, array, elementKind, positiveIndex);
    }

    public AddressNode createArrayAddress(StructuredGraph graph, ValueNode array, JavaKind elementKind, ValueNode index) {
        return createArrayAddress(graph, array, elementKind, elementKind, index);
    }

    public AddressNode createArrayAddress(StructuredGraph graph, ValueNode array, JavaKind arrayKind, JavaKind elementKind, ValueNode index) {
        ValueNode wordIndex;
        if (target.wordSize > 4) {
            wordIndex = graph.unique(new SignExtendNode(index, target.wordSize * 8));
        } else {
            assert target.wordSize == 4 : "unsupported word size";
            wordIndex = index;
        }

        int shift = CodeUtil.log2(metaAccess.getArrayIndexScale(elementKind));
        ValueNode scaledIndex = graph.unique(new LeftShiftNode(wordIndex, ConstantNode.forInt(shift, graph)));

        int base = metaAccess.getArrayBaseOffset(arrayKind);
        ValueNode offset = graph.unique(new AddNode(scaledIndex, ConstantNode.forIntegerKind(target.wordJavaKind, base, graph)));

        return graph.unique(new OffsetAddressNode(array, offset));
    }

    protected void lowerIndexAddressNode(IndexAddressNode indexAddress) {
        AddressNode lowered = createArrayAddress(indexAddress.graph(), indexAddress.getArray(), indexAddress.getArrayKind(), indexAddress.getElementKind(), indexAddress.getIndex());
        indexAddress.replaceAndDelete(lowered);
    }

    protected void lowerLoadIndexedNode(LoadIndexedNode loadIndexed, LoweringTool tool) {
        StructuredGraph graph = loadIndexed.graph();
        ValueNode array = loadIndexed.array();
        array = createNullCheckedValue(array, loadIndexed, tool);
        JavaKind elementKind = loadIndexed.elementKind();
        Stamp loadStamp = loadStamp(loadIndexed.stamp(NodeView.DEFAULT), elementKind);

        GuardingNode boundsCheck = getBoundsCheck(loadIndexed, array, tool);
        ValueNode index = loadIndexed.index();
        if (UseIndexMasking.getValue(graph.getOptions())) {
            index = proxyIndex(loadIndexed, index, array, tool);
        }
        AddressNode address = createArrayIndexAddress(graph, array, elementKind, index, boundsCheck);

        ReadNode memoryRead = graph.add(new ReadNode(address, NamedLocationIdentity.getArrayLocation(elementKind), loadStamp, BarrierType.NONE));
        memoryRead.setGuard(boundsCheck);
        ValueNode readValue = implicitLoadConvert(graph, elementKind, memoryRead);

        loadIndexed.replaceAtUsages(readValue);
        graph.replaceFixed(loadIndexed, memoryRead);
    }

    protected void lowerStoreIndexedNode(StoreIndexedNode storeIndexed, LoweringTool tool) {
        StructuredGraph graph = storeIndexed.graph();

        ValueNode value = storeIndexed.value();
        ValueNode array = storeIndexed.array();

        array = this.createNullCheckedValue(array, storeIndexed, tool);

        GuardingNode boundsCheck = getBoundsCheck(storeIndexed, array, tool);

        JavaKind storageKind = storeIndexed.elementKind();

        LogicNode condition = null;
        if (storeIndexed.getStoreCheck() == null && storageKind == JavaKind.Object && !StampTool.isPointerAlwaysNull(value)) {
            /* Array store check. */
            TypeReference arrayType = StampTool.typeReferenceOrNull(array);
            if (arrayType != null && arrayType.isExact()) {
                ResolvedJavaType elementType = arrayType.getType().getComponentType();
                if (!elementType.isJavaLangObject()) {
                    TypeReference typeReference = TypeReference.createTrusted(storeIndexed.graph().getAssumptions(), elementType);
                    LogicNode typeTest = graph.addOrUniqueWithInputs(InstanceOfNode.create(typeReference, value));
                    condition = LogicNode.or(graph.unique(IsNullNode.create(value)), typeTest, GraalDirectives.UNLIKELY_PROBABILITY);
                }
            } else {
                /*
                 * The guard on the read hub should be the null check of the array that was
                 * introduced earlier.
                 */
                ValueNode arrayClass = createReadHub(graph, array, tool);
                ValueNode componentHub = createReadArrayComponentHub(graph, arrayClass, storeIndexed);
                LogicNode typeTest = graph.unique(InstanceOfDynamicNode.create(graph.getAssumptions(), tool.getConstantReflection(), componentHub, value, false));
                condition = LogicNode.or(graph.unique(IsNullNode.create(value)), typeTest, GraalDirectives.UNLIKELY_PROBABILITY);
            }
        }
        BarrierType barrierType = barrierSet.arrayStoreBarrierType(storageKind);
        AddressNode address = createArrayIndexAddress(graph, array, storageKind, storeIndexed.index(), boundsCheck);
        WriteNode memoryWrite = graph.add(new WriteNode(address, NamedLocationIdentity.getArrayLocation(storageKind), implicitStoreConvert(graph, storageKind, value),
                        barrierType));
        memoryWrite.setGuard(boundsCheck);
        if (condition != null) {
            tool.createGuard(storeIndexed, condition, DeoptimizationReason.ArrayStoreException, DeoptimizationAction.InvalidateReprofile);
        }
        memoryWrite.setStateAfter(storeIndexed.stateAfter());
        graph.replaceFixedWithFixed(storeIndexed, memoryWrite);
    }

    protected void lowerArrayLengthNode(ArrayLengthNode arrayLengthNode, LoweringTool tool) {
        arrayLengthNode.replaceAtUsages(createReadArrayLength(arrayLengthNode.array(), arrayLengthNode, tool));
        StructuredGraph graph = arrayLengthNode.graph();
        graph.removeFixed(arrayLengthNode);
    }

    /**
     * Creates a read node that read the array length and is guarded by a null-check.
     *
     * The created node is placed before {@code before} in the CFG.
     */
    protected ReadNode createReadArrayLength(ValueNode array, FixedNode before, LoweringTool tool) {
        StructuredGraph graph = array.graph();
        ValueNode canonicalArray = this.createNullCheckedValue(skipPiWhileNonNull(array), before, tool);
        AddressNode address = createOffsetAddress(graph, canonicalArray, arrayLengthOffset());
        ReadNode readArrayLength = graph.add(new ReadNode(address, ARRAY_LENGTH_LOCATION, StampFactory.positiveInt(), BarrierType.NONE));
        graph.addBeforeFixed(before, readArrayLength);
        return readArrayLength;
    }

    protected void lowerLoadHubNode(LoadHubNode loadHub, LoweringTool tool) {
        StructuredGraph graph = loadHub.graph();
        if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
            return;
        }
        if (graph.getGuardsStage().allowsFloatingGuards()) {
            return;
        }
        ValueNode hub = createReadHub(graph, loadHub.getValue(), tool);
        loadHub.replaceAtUsagesAndDelete(hub);
    }

    protected void lowerLoadHubOrNullNode(LoadHubOrNullNode loadHubOrNullNode, LoweringTool tool) {
        StructuredGraph graph = loadHubOrNullNode.graph();
        if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
            return;
        }
        if (graph.getGuardsStage().allowsFloatingGuards()) {
            return;
        }
        final FixedWithNextNode predecessor = tool.lastFixedNode();
        final ValueNode value = loadHubOrNullNode.getValue();
        AbstractPointerStamp stamp = (AbstractPointerStamp) value.stamp(NodeView.DEFAULT);
        final LogicNode isNull = graph.addOrUniqueWithInputs(IsNullNode.create(value));
        final EndNode trueEnd = graph.add(new EndNode());
        final EndNode falseEnd = graph.add(new EndNode());
        final IfNode ifNode = graph.add(new IfNode(isNull, trueEnd, falseEnd, 0.5));
        final MergeNode merge = graph.add(new MergeNode());
        merge.addForwardEnd(trueEnd);
        merge.addForwardEnd(falseEnd);
        final AbstractPointerStamp hubStamp = (AbstractPointerStamp) loadHubOrNullNode.stamp(NodeView.DEFAULT);
        ValueNode nullHub = ConstantNode.forConstant(hubStamp.asAlwaysNull(), JavaConstant.NULL_POINTER, tool.getMetaAccess(), graph);
        final ValueNode nonNullValue = graph.addOrUniqueWithInputs(PiNode.create(value, stamp.asNonNull(), ifNode.falseSuccessor()));
        ValueNode hub = createReadHub(graph, nonNullValue, tool);
        ValueNode[] values = new ValueNode[]{nullHub, hub};
        final PhiNode hubPhi = graph.unique(new ValuePhiNode(hubStamp, merge, values));
        final FixedNode oldNext = predecessor.next();
        predecessor.setNext(ifNode);
        merge.setNext(oldNext);
        loadHubOrNullNode.replaceAtUsagesAndDelete(hubPhi);
    }

    protected void lowerLoadArrayComponentHubNode(LoadArrayComponentHubNode loadHub) {
        StructuredGraph graph = loadHub.graph();
        ValueNode hub = createReadArrayComponentHub(graph, loadHub.getValue(), loadHub);
        graph.replaceFixed(loadHub, hub);
    }

    protected void lowerCompareAndSwapNode(UnsafeCompareAndSwapNode cas) {
        StructuredGraph graph = cas.graph();
        JavaKind valueKind = cas.getValueKind();

        ValueNode expectedValue = implicitStoreConvert(graph, valueKind, cas.expected());
        ValueNode newValue = implicitStoreConvert(graph, valueKind, cas.newValue());

        AddressNode address = graph.unique(new OffsetAddressNode(cas.object(), cas.offset()));
        BarrierType barrierType = barrierSet.guessStoreBarrierType(cas.object(), newValue);
        LogicCompareAndSwapNode atomicNode = graph.add(new LogicCompareAndSwapNode(address, cas.getKilledLocationIdentity(), expectedValue, newValue, barrierType));
        atomicNode.setStateAfter(cas.stateAfter());
        graph.replaceFixedWithFixed(cas, atomicNode);
    }

    protected void lowerCompareAndExchangeNode(UnsafeCompareAndExchangeNode cas) {
        StructuredGraph graph = cas.graph();
        JavaKind valueKind = cas.getValueKind();

        ValueNode expectedValue = implicitStoreConvert(graph, valueKind, cas.expected());
        ValueNode newValue = implicitStoreConvert(graph, valueKind, cas.newValue());

        AddressNode address = graph.unique(new OffsetAddressNode(cas.object(), cas.offset()));
        BarrierType barrierType = barrierSet.guessStoreBarrierType(cas.object(), newValue);
        ValueCompareAndSwapNode atomicNode = graph.add(new ValueCompareAndSwapNode(address, expectedValue, newValue, cas.getKilledLocationIdentity(), barrierType));
        ValueNode coercedNode = implicitLoadConvert(graph, valueKind, atomicNode, true);
        atomicNode.setStateAfter(cas.stateAfter());
        cas.replaceAtUsages(coercedNode);
        graph.replaceFixedWithFixed(cas, atomicNode);
    }

    protected void lowerAtomicReadAndWriteNode(AtomicReadAndWriteNode n) {
        StructuredGraph graph = n.graph();
        JavaKind valueKind = n.getValueKind();

        ValueNode newValue = implicitStoreConvert(graph, valueKind, n.newValue());

        AddressNode address = graph.unique(new OffsetAddressNode(n.object(), n.offset()));
        BarrierType barrierType = barrierSet.guessStoreBarrierType(n.object(), newValue);
        LIRKind lirAccessKind = LIRKind.fromJavaKind(target.arch, valueKind);
        LoweredAtomicReadAndWriteNode memoryRead = graph.add(new LoweredAtomicReadAndWriteNode(address, n.getKilledLocationIdentity(), newValue, lirAccessKind, barrierType));
        memoryRead.setStateAfter(n.stateAfter());

        ValueNode readValue = implicitLoadConvert(graph, valueKind, memoryRead);
        n.stateAfter().replaceFirstInput(n, memoryRead);
        n.replaceAtUsages(readValue);
        graph.replaceFixedWithFixed(n, memoryRead);
    }

    /**
     * @param tool utility for performing the lowering
     */
    protected void lowerUnsafeLoadNode(RawLoadNode load, LoweringTool tool) {
        StructuredGraph graph = load.graph();
        if (load instanceof GuardedUnsafeLoadNode) {
            GuardedUnsafeLoadNode guardedLoad = (GuardedUnsafeLoadNode) load;
            GuardingNode guard = guardedLoad.getGuard();
            if (guard == null) {
                // can float freely if the guard folded away
                ReadNode memoryRead = createUnsafeRead(graph, load, null);
                memoryRead.setForceFixed(false);
                graph.replaceFixedWithFixed(load, memoryRead);
            } else {
                // must be guarded, but flows below the guard
                ReadNode memoryRead = createUnsafeRead(graph, load, guard);
                graph.replaceFixedWithFixed(load, memoryRead);
            }
        } else {
            // never had a guarding condition so it must be fixed, creation of the read will force
            // it to be fixed
            ReadNode memoryRead = createUnsafeRead(graph, load, null);
            graph.replaceFixedWithFixed(load, memoryRead);
        }
    }

    protected AddressNode createUnsafeAddress(StructuredGraph graph, ValueNode object, ValueNode offset) {
        if (object.isConstant() && object.asConstant().isDefaultForKind()) {
            return graph.addOrUniqueWithInputs(OffsetAddressNode.create(offset));
        } else {
            return graph.unique(new OffsetAddressNode(object, offset));
        }
    }

    protected ReadNode createUnsafeRead(StructuredGraph graph, RawLoadNode load, GuardingNode guard) {
        boolean compressible = load.accessKind() == JavaKind.Object;
        JavaKind readKind = load.accessKind();
        Stamp loadStamp = loadStamp(load.stamp(NodeView.DEFAULT), readKind, compressible);
        AddressNode address = createUnsafeAddress(graph, load.object(), load.offset());
        ReadNode memoryRead = null;
        if (load.isVolatile()) {
            memoryRead = new VolatileReadNode(address, load.getLocationIdentity(), loadStamp, barrierSet.readBarrierType(load));
        } else {
            memoryRead = new ReadNode(address, load.getLocationIdentity(), loadStamp, barrierSet.readBarrierType(load));
        }
        memoryRead = graph.add(memoryRead);
        if (guard == null) {
            // An unsafe read must not float otherwise it may float above
            // a test guaranteeing the read is safe.
            memoryRead.setForceFixed(true);
        } else {
            memoryRead.setGuard(guard);
        }
        ValueNode readValue = performBooleanCoercionIfNecessary(implicitLoadConvert(graph, readKind, memoryRead, compressible), readKind);
        load.replaceAtUsages(readValue);
        return memoryRead;
    }

    protected void lowerUnsafeMemoryLoadNode(UnsafeMemoryLoadNode load) {
        StructuredGraph graph = load.graph();
        JavaKind readKind = load.getKind();
        assert readKind != JavaKind.Object;
        Stamp loadStamp = loadStamp(load.stamp(NodeView.DEFAULT), readKind, false);
        AddressNode address = graph.addOrUniqueWithInputs(OffsetAddressNode.create(load.getAddress()));
        ReadNode memoryRead = graph.add(new ReadNode(address, load.getLocationIdentity(), loadStamp, BarrierType.NONE));
        // An unsafe read must not float otherwise it may float above
        // a test guaranteeing the read is safe.
        memoryRead.setForceFixed(true);
        ValueNode readValue = performBooleanCoercionIfNecessary(implicitLoadConvert(graph, readKind, memoryRead, false), readKind);
        load.replaceAtUsages(readValue);
        graph.replaceFixedWithFixed(load, memoryRead);
    }

    private static ValueNode performBooleanCoercionIfNecessary(ValueNode readValue, JavaKind readKind) {
        if (readKind == JavaKind.Boolean) {
            StructuredGraph graph = readValue.graph();
            IntegerEqualsNode eq = graph.addOrUnique(new IntegerEqualsNode(readValue, ConstantNode.forInt(0, graph)));
            return graph.addOrUnique(new ConditionalNode(eq, ConstantNode.forBoolean(false, graph), ConstantNode.forBoolean(true, graph)));
        }
        return readValue;
    }

    protected void lowerUnsafeStoreNode(RawStoreNode store) {
        StructuredGraph graph = store.graph();
        boolean compressible = store.value().getStackKind() == JavaKind.Object;
        JavaKind valueKind = store.accessKind();
        ValueNode value = implicitStoreConvert(graph, valueKind, store.value(), compressible);
        AddressNode address = createUnsafeAddress(graph, store.object(), store.offset());
        WriteNode write = null;
        if (store.isVolatile()) {
            write = new VolatileWriteNode(address, store.getLocationIdentity(), value, barrierSet.storeBarrierType(store));
        } else {
            write = new WriteNode(address, store.getLocationIdentity(), value, barrierSet.storeBarrierType(store));
        }
        write = graph.add(write);
        write.setStateAfter(store.stateAfter());
        graph.replaceFixedWithFixed(store, write);
    }

    protected void lowerUnsafeMemoryStoreNode(UnsafeMemoryStoreNode store) {
        StructuredGraph graph = store.graph();
        assert store.getValue().getStackKind() != JavaKind.Object;
        JavaKind valueKind = store.getKind();
        ValueNode value = implicitStoreConvert(graph, valueKind, store.getValue(), false);
        AddressNode address = graph.addOrUniqueWithInputs(OffsetAddressNode.create(store.getAddress()));
        WriteNode write = graph.add(new WriteNode(address, store.getKilledLocationIdentity(), value, BarrierType.NONE));
        write.setStateAfter(store.stateAfter());
        graph.replaceFixedWithFixed(store, write);
    }

    protected void lowerJavaReadNode(JavaReadNode read) {
        StructuredGraph graph = read.graph();
        JavaKind valueKind = read.getReadKind();
        Stamp loadStamp = loadStamp(read.stamp(NodeView.DEFAULT), valueKind, read.isCompressible());

        ReadNode memoryRead = graph.add(new ReadNode(read.getAddress(), read.getLocationIdentity(), loadStamp, read.getBarrierType()));
        GuardingNode guard = read.getGuard();
        ValueNode readValue = implicitLoadConvert(graph, valueKind, memoryRead, read.isCompressible());
        if (guard == null) {
            // An unsafe read must not float otherwise it may float above
            // a test guaranteeing the read is safe.
            memoryRead.setForceFixed(true);
        } else {
            memoryRead.setGuard(guard);
        }
        read.replaceAtUsages(readValue);
        graph.replaceFixed(read, memoryRead);
    }

    protected void lowerJavaWriteNode(JavaWriteNode write) {
        StructuredGraph graph = write.graph();
        ValueNode value = implicitStoreConvert(graph, write.getWriteKind(), write.value(), write.isCompressible());
        WriteNode memoryWrite = null;
        if (write.hasSideEffect()) {
            memoryWrite = graph.add(new WriteNode(write.getAddress(), write.getKilledLocationIdentity(), value, write.getBarrierType()));
        } else {
            memoryWrite = graph.add(new SideEffectFreeWrite(write.getAddress(), write.getKilledLocationIdentity(), value, write.getBarrierType()));
        }
        memoryWrite.setStateAfter(write.stateAfter());
        graph.replaceFixedWithFixed(write, memoryWrite);
        memoryWrite.setGuard(write.getGuard());
    }

    @SuppressWarnings("try")
    protected void lowerCommitAllocationNode(CommitAllocationNode commit, LoweringTool tool) {
        StructuredGraph graph = commit.graph();
        if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
            List<AbstractNewObjectNode> recursiveLowerings = new ArrayList<>();

            ValueNode[] allocations = new ValueNode[commit.getVirtualObjects().size()];
            BitSet omittedValues = new BitSet();
            int valuePos = 0;
            for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
                VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
                try (DebugCloseable nsp = graph.withNodeSourcePosition(virtual)) {
                    int entryCount = virtual.entryCount();
                    AbstractNewObjectNode newObject;
                    if (virtual instanceof VirtualInstanceNode) {
                        newObject = graph.add(new NewInstanceNode(virtual.type(), true));
                    } else {
                        assert virtual instanceof VirtualArrayNode;
                        newObject = graph.add(new NewArrayNode(((VirtualArrayNode) virtual).componentType(), ConstantNode.forInt(entryCount, graph), true));
                    }
                    // The final STORE_STORE barrier will be emitted by finishAllocatedObjects
                    newObject.clearEmitMemoryBarrier();

                    recursiveLowerings.add(newObject);
                    graph.addBeforeFixed(commit, newObject);
                    allocations[objIndex] = newObject;
                    for (int i = 0; i < entryCount; i++) {
                        ValueNode value = commit.getValues().get(valuePos);
                        if (value instanceof VirtualObjectNode) {
                            value = allocations[commit.getVirtualObjects().indexOf(value)];
                        }
                        if (value == null) {
                            omittedValues.set(valuePos);
                        } else if (!(value.isConstant() && value.asConstant().isDefaultForKind())) {
                            // Constant.illegal is always the defaultForKind, so it is skipped
                            JavaKind valueKind = value.getStackKind();
                            JavaKind storageKind = virtual.entryKind(tool.getProviders().getMetaAccessExtensionProvider(), i);

                            // Truffle requires some leniency in terms of what can be put where:
                            assert valueKind.getStackKind() == storageKind.getStackKind() ||
                                            (valueKind == JavaKind.Long || valueKind == JavaKind.Double || (valueKind == JavaKind.Int && virtual instanceof VirtualArrayNode) ||
                                                            (valueKind == JavaKind.Float && virtual instanceof VirtualArrayNode));
                            AddressNode address = null;
                            BarrierType barrierType = null;
                            if (virtual instanceof VirtualInstanceNode) {
                                ResolvedJavaField field = ((VirtualInstanceNode) virtual).field(i);
                                long offset = fieldOffset(field);
                                if (offset >= 0) {
                                    address = createOffsetAddress(graph, newObject, offset);
                                    barrierType = barrierSet.fieldStoreBarrierType(field, getStorageKind(field));
                                }
                            } else {
                                assert virtual instanceof VirtualArrayNode;
                                address = createOffsetAddress(graph, newObject, metaAccess.getArrayBaseOffset(storageKind) + i * metaAccess.getArrayIndexScale(storageKind));
                                barrierType = barrierSet.arrayStoreBarrierType(storageKind);
                            }
                            if (address != null) {
                                WriteNode write = new WriteNode(address, LocationIdentity.init(), arrayImplicitStoreConvert(graph, storageKind, value, commit, virtual, valuePos), barrierType);
                                graph.addAfterFixed(newObject, graph.add(write));
                            }
                        }
                        valuePos++;
                    }
                }
            }
            valuePos = 0;

            for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
                VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
                try (DebugCloseable nsp = graph.withNodeSourcePosition(virtual)) {
                    int entryCount = virtual.entryCount();
                    ValueNode newObject = allocations[objIndex];
                    for (int i = 0; i < entryCount; i++) {
                        if (omittedValues.get(valuePos)) {
                            ValueNode value = commit.getValues().get(valuePos);
                            assert value instanceof VirtualObjectNode;
                            ValueNode allocValue = allocations[commit.getVirtualObjects().indexOf(value)];
                            if (!(allocValue.isConstant() && allocValue.asConstant().isDefaultForKind())) {
                                assert virtual.entryKind(metaAccessExtensionProvider, i) == JavaKind.Object && allocValue.getStackKind() == JavaKind.Object;
                                AddressNode address;
                                BarrierType barrierType;
                                if (virtual instanceof VirtualInstanceNode) {
                                    VirtualInstanceNode virtualInstance = (VirtualInstanceNode) virtual;
                                    ResolvedJavaField field = virtualInstance.field(i);
                                    address = createFieldAddress(graph, newObject, field);
                                    barrierType = barrierSet.fieldStoreBarrierType(field, getStorageKind(field));
                                } else {
                                    assert virtual instanceof VirtualArrayNode;
                                    address = createArrayAddress(graph, newObject, virtual.entryKind(metaAccessExtensionProvider, i), ConstantNode.forInt(i, graph));
                                    barrierType = barrierSet.arrayStoreBarrierType(virtual.entryKind(metaAccessExtensionProvider, i));
                                }
                                if (address != null) {
                                    WriteNode write = new WriteNode(address, LocationIdentity.init(), implicitStoreConvert(graph, JavaKind.Object, allocValue), barrierType);
                                    graph.addBeforeFixed(commit, graph.add(write));
                                }
                            }
                        }
                        valuePos++;
                    }
                }
            }

            finishAllocatedObjects(tool, commit, commit, allocations);
            graph.removeFixed(commit);

            for (AbstractNewObjectNode recursiveLowering : recursiveLowerings) {
                recursiveLowering.lower(tool);
            }
        }

    }

    public void finishAllocatedObjects(LoweringTool tool, FixedWithNextNode insertAfter, CommitAllocationNode commit, ValueNode[] allocations) {
        FixedWithNextNode insertionPoint = insertAfter;
        StructuredGraph graph = commit.graph();
        for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
            FixedValueAnchorNode anchor = graph.add(new FixedValueAnchorNode(allocations[objIndex]));
            allocations[objIndex] = anchor;
            graph.addAfterFixed(insertionPoint, anchor);
            insertionPoint = anchor;
        }
        /*
         * Note that the FrameState that is assigned to these MonitorEnterNodes isn't the correct
         * state. It will be the state from before the allocation occurred instead of a valid state
         * after the locking is performed. In practice this should be fine since these are newly
         * allocated objects. The bytecodes themselves permit allocating an object, doing a
         * monitorenter and then dropping all references to the object which would produce the same
         * state, though that would normally produce an IllegalMonitorStateException. In HotSpot
         * some form of fast path locking should always occur so the FrameState should never
         * actually be used.
         */
        ArrayList<MonitorEnterNode> enters = null;
        FrameState stateBefore = SnippetTemplate.findLastFrameState(insertionPoint);
        for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
            List<MonitorIdNode> locks = commit.getLocks(objIndex);
            if (locks.size() > 1) {
                // Ensure that the lock operations are performed in lock depth order
                ArrayList<MonitorIdNode> newList = new ArrayList<>(locks);
                newList.sort((a, b) -> Integer.compare(a.getLockDepth(), b.getLockDepth()));
                locks = newList;
            }
            int lastDepth = -1;
            for (MonitorIdNode monitorId : locks) {
                assert lastDepth < monitorId.getLockDepth();
                lastDepth = monitorId.getLockDepth();
                MonitorEnterNode enter = graph.add(new MonitorEnterNode(allocations[objIndex], monitorId));
                graph.addAfterFixed(insertionPoint, enter);
                enter.setStateAfter(stateBefore.duplicate());
                insertionPoint = enter;
                if (enters == null) {
                    enters = new ArrayList<>();
                }
                enters.add(enter);
            }
        }
        for (Node usage : commit.usages().snapshot()) {
            if (usage instanceof AllocatedObjectNode) {
                AllocatedObjectNode addObject = (AllocatedObjectNode) usage;
                int index = commit.getVirtualObjects().indexOf(addObject.getVirtualObject());
                addObject.replaceAtUsagesAndDelete(allocations[index]);
            } else {
                assert enters != null;
                commit.replaceAtUsages(enters.get(enters.size() - 1), InputType.Memory);
            }
        }
        if (enters != null) {
            for (MonitorEnterNode enter : enters) {
                enter.lower(tool);
            }
        }
        assert commit.hasNoUsages();
        insertAllocationBarrier(insertAfter, commit, graph);
    }

    /**
     * Insert the required {@link MemoryBarriers#STORE_STORE} barrier for an allocation and also
     * include the {@link MemoryBarriers#LOAD_STORE} required for final fields if any final fields
     * are being written, as if {@link FinalFieldBarrierNode} were emitted.
     */
    private static void insertAllocationBarrier(FixedWithNextNode insertAfter, CommitAllocationNode commit, StructuredGraph graph) {
        int barrier = MemoryBarriers.STORE_STORE;
        outer: for (VirtualObjectNode vobj : commit.getVirtualObjects()) {
            for (ResolvedJavaField field : vobj.type().getInstanceFields(true)) {
                if (field.isFinal()) {
                    barrier = barrier | MemoryBarriers.LOAD_STORE;
                    break outer;
                }
            }
        }
        graph.addAfterFixed(insertAfter, graph.add(new MembarNode(barrier, LocationIdentity.init())));
    }

    public abstract int fieldOffset(ResolvedJavaField field);

    public FieldLocationIdentity fieldLocationIdentity(ResolvedJavaField field) {
        return new FieldLocationIdentity(field);
    }

    public abstract ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField field);

    public abstract int arrayLengthOffset();

    public Stamp loadStamp(Stamp stamp, JavaKind kind) {
        return loadStamp(stamp, kind, true);
    }

    private boolean useCompressedOops(JavaKind kind, boolean compressible) {
        return kind == JavaKind.Object && compressible && useCompressedOops;
    }

    protected abstract Stamp loadCompressedStamp(ObjectStamp stamp);

    /**
     * @param compressible whether the stamp should be compressible
     */
    protected Stamp loadStamp(Stamp stamp, JavaKind kind, boolean compressible) {
        if (useCompressedOops(kind, compressible)) {
            return loadCompressedStamp((ObjectStamp) stamp);
        }

        switch (kind) {
            case Boolean:
            case Byte:
                return IntegerStamp.OPS.getNarrow().foldStamp(32, 8, stamp);
            case Char:
            case Short:
                return IntegerStamp.OPS.getNarrow().foldStamp(32, 16, stamp);
        }
        return stamp;
    }

    public final ValueNode implicitLoadConvert(StructuredGraph graph, JavaKind kind, ValueNode value) {
        return implicitLoadConvert(graph, kind, value, true);
    }

    public ValueNode implicitLoadConvert(JavaKind kind, ValueNode value) {
        return implicitLoadConvert(kind, value, true);
    }

    protected final ValueNode implicitLoadConvert(StructuredGraph graph, JavaKind kind, ValueNode value, boolean compressible) {
        ValueNode ret = implicitLoadConvert(kind, value, compressible);
        if (!ret.isAlive()) {
            ret = graph.addOrUnique(ret);
        }
        return ret;
    }

    protected abstract ValueNode newCompressionNode(CompressionOp op, ValueNode value);

    /**
     * @param compressible whether the convert should be compressible
     */
    protected ValueNode implicitLoadConvert(JavaKind kind, ValueNode value, boolean compressible) {
        if (useCompressedOops(kind, compressible)) {
            return newCompressionNode(CompressionOp.Uncompress, value);
        }

        switch (kind) {
            case Byte:
            case Short:
                return new SignExtendNode(value, 32);
            case Boolean:
            case Char:
                return new ZeroExtendNode(value, 32);
        }
        return value;
    }

    public ValueNode arrayImplicitStoreConvert(StructuredGraph graph,
                    JavaKind entryKind,
                    ValueNode value,
                    CommitAllocationNode commit,
                    VirtualObjectNode virtual,
                    int valuePos) {
        if (!virtual.isVirtualByteArray(metaAccessExtensionProvider)) {
            return implicitStoreConvert(graph, entryKind, value);
        }
        // A virtual entry in a byte array can span multiple bytes. This shortens the entry to fit
        // in its declared size.
        int entryIndex = valuePos + 1;
        int bytes = 1;
        while (entryIndex < commit.getValues().size() && commit.getValues().get(entryIndex).isIllegalConstant()) {
            bytes++;
            entryIndex++;
        }
        assert bytes <= value.getStackKind().getByteCount();
        ValueNode entry = value;
        if (value.getStackKind() == JavaKind.Float) {
            entry = graph.addOrUnique(ReinterpretNode.create(JavaKind.Int, entry, NodeView.DEFAULT));
        } else if (value.getStackKind() == JavaKind.Double) {
            entry = graph.addOrUnique(ReinterpretNode.create(JavaKind.Long, entry, NodeView.DEFAULT));
        }
        if (bytes < value.getStackKind().getByteCount()) {
            entry = graph.unique(new NarrowNode(entry, bytes << 3));
        }
        return entry;
    }

    public final ValueNode implicitStoreConvert(StructuredGraph graph, JavaKind kind, ValueNode value) {
        return implicitStoreConvert(graph, kind, value, true);
    }

    public ValueNode implicitStoreConvert(JavaKind kind, ValueNode value) {
        return implicitStoreConvert(kind, value, true);
    }

    protected final ValueNode implicitStoreConvert(StructuredGraph graph, JavaKind kind, ValueNode value, boolean compressible) {
        ValueNode ret = implicitStoreConvert(kind, value, compressible);
        if (!ret.isAlive()) {
            ret = graph.addOrUnique(ret);
        }
        return ret;
    }

    /**
     * @param compressible whether the covert should be compressible
     */
    protected ValueNode implicitStoreConvert(JavaKind kind, ValueNode value, boolean compressible) {
        if (useCompressedOops(kind, compressible)) {
            return newCompressionNode(CompressionOp.Compress, value);
        }

        switch (kind) {
            case Boolean:
            case Byte:
                return new NarrowNode(value, 8);
            case Char:
            case Short:
                return new NarrowNode(value, 16);
        }
        return value;
    }

    protected abstract ValueNode createReadHub(StructuredGraph graph, ValueNode object, LoweringTool tool);

    protected abstract ValueNode createReadArrayComponentHub(StructuredGraph graph, ValueNode arrayHub, FixedNode anchor);

    protected ValueNode proxyIndex(AccessIndexedNode n, ValueNode index, ValueNode array, LoweringTool tool) {
        StructuredGraph graph = index.graph();
        ValueNode arrayLength = readOrCreateArrayLength(n, array, tool, graph);
        ValueNode lengthMinusOne = SubNode.create(arrayLength, ConstantNode.forInt(1), NodeView.DEFAULT);
        return branchlessMax(branchlessMin(index, lengthMinusOne, NodeView.DEFAULT), ConstantNode.forInt(0), NodeView.DEFAULT);
    }

    protected GuardingNode getBoundsCheck(AccessIndexedNode n, ValueNode array, LoweringTool tool) {
        if (n.getBoundsCheck() != null) {
            return n.getBoundsCheck();
        }

        StructuredGraph graph = n.graph();
        ValueNode arrayLength = readOrCreateArrayLength(n, array, tool, graph);

        LogicNode boundsCheck = IntegerBelowNode.create(n.index(), arrayLength, NodeView.DEFAULT);
        if (boundsCheck.isTautology()) {
            return null;
        }
        return tool.createGuard(n, graph.addOrUniqueWithInputs(boundsCheck), BoundsCheckException, InvalidateReprofile);
    }

    private ValueNode readOrCreateArrayLength(AccessIndexedNode n, ValueNode array, LoweringTool tool, StructuredGraph graph) {
        ValueNode arrayLength = readArrayLength(array, tool.getConstantReflection());
        if (arrayLength == null) {
            arrayLength = createReadArrayLength(array, n, tool);
        } else {
            arrayLength = arrayLength.isAlive() ? arrayLength : graph.addOrUniqueWithInputs(arrayLength);
        }
        return arrayLength;
    }

    protected GuardingNode createNullCheck(ValueNode object, FixedNode before, LoweringTool tool) {
        if (StampTool.isPointerNonNull(object)) {
            return null;
        }
        return tool.createGuard(before, before.graph().unique(IsNullNode.create(object)), NullCheckException, InvalidateReprofile, SpeculationLog.NO_SPECULATION, true, null);
    }

    protected ValueNode createNullCheckedValue(ValueNode object, FixedNode before, LoweringTool tool) {
        GuardingNode nullCheck = createNullCheck(object, before, tool);
        if (nullCheck == null) {
            return object;
        }
        return before.graph().maybeAddOrUnique(PiNode.create(object, (object.stamp(NodeView.DEFAULT)).join(StampFactory.objectNonNull()), (ValueNode) nullCheck));
    }

    @Override
    public ValueNode reconstructArrayIndex(JavaKind elementKind, AddressNode address) {
        StructuredGraph graph = address.graph();
        ValueNode offset = ((OffsetAddressNode) address).getOffset();

        int base = metaAccess.getArrayBaseOffset(elementKind);
        ValueNode scaledIndex = graph.unique(new SubNode(offset, ConstantNode.forIntegerStamp(offset.stamp(NodeView.DEFAULT), base, graph)));

        int shift = CodeUtil.log2(metaAccess.getArrayIndexScale(elementKind));
        ValueNode ret = graph.unique(new RightShiftNode(scaledIndex, ConstantNode.forInt(shift, graph)));
        return IntegerConvertNode.convert(ret, StampFactory.forKind(JavaKind.Int), graph, NodeView.DEFAULT);
    }

    private static void lowerVolatileRead(VolatileReadNode n, LoweringTool tool) {
        if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
            return;
        }
        final StructuredGraph graph = n.graph();
        MembarNode preMembar = graph.add(new MembarNode(JMM_PRE_VOLATILE_READ));
        graph.addBeforeFixed(n, preMembar);
        MembarNode postMembar = graph.add(new MembarNode(JMM_POST_VOLATILE_READ));
        graph.addAfterFixed(n, postMembar);
        n.replaceAtUsages(postMembar, InputType.Memory);
        ReadNode nonVolatileRead = graph.add(new ReadNode(n.getAddress(), n.getLocationIdentity(), n.getAccessStamp(NodeView.DEFAULT), n.getBarrierType()));
        graph.replaceFixedWithFixed(n, nonVolatileRead);
    }

    private static void lowerVolatileWrite(VolatileWriteNode n, LoweringTool tool) {
        if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
            return;
        }
        final StructuredGraph graph = n.graph();
        MembarNode preMembar = graph.add(new MembarNode(JMM_PRE_VOLATILE_WRITE));
        graph.addBeforeFixed(n, preMembar);
        MembarNode postMembar = graph.add(new MembarNode(JMM_POST_VOLATILE_WRITE));
        graph.addAfterFixed(n, postMembar);
        n.replaceAtUsages(postMembar, InputType.Memory);
        WriteNode nonVolatileWrite = graph.add(new WriteNode(n.getAddress(), n.getLocationIdentity(), n.value(), n.getBarrierType()));
        graph.replaceFixedWithFixed(n, nonVolatileWrite);
    }
}
