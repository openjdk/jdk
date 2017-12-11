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
package org.graalvm.compiler.replacements;

import static jdk.vm.ci.code.MemoryBarriers.JMM_POST_VOLATILE_READ;
import static jdk.vm.ci.code.MemoryBarriers.JMM_POST_VOLATILE_WRITE;
import static jdk.vm.ci.code.MemoryBarriers.JMM_PRE_VOLATILE_READ;
import static jdk.vm.ci.code.MemoryBarriers.JMM_PRE_VOLATILE_WRITE;
import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.BoundsCheckException;
import static jdk.vm.ci.meta.DeoptimizationReason.NullCheckException;
import static org.graalvm.compiler.nodes.NamedLocationIdentity.ARRAY_LENGTH_LOCATION;
import static org.graalvm.compiler.nodes.java.ArrayLengthNode.readArrayLength;
import static org.graalvm.compiler.nodes.util.GraphUtil.skipPiWhileNonNull;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.CompressionNode.CompressionOp;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
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
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.extended.MembarNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.extended.UnsafeMemoryLoadNode;
import org.graalvm.compiler.nodes.extended.UnsafeMemoryStoreNode;
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
import org.graalvm.compiler.nodes.java.RawMonitorEnterNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.java.UnsafeCompareAndSwapNode;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.LoweringTool;
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
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.MemoryBarriers;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * VM-independent lowerings for standard Java nodes. VM-specific methods are abstract and must be
 * implemented by VM-specific subclasses.
 */
public abstract class DefaultJavaLoweringProvider implements LoweringProvider {

    protected final MetaAccessProvider metaAccess;
    protected final ForeignCallsProvider foreignCalls;
    protected final TargetDescription target;
    private final boolean useCompressedOops;

    private BoxingSnippets.Templates boxingSnippets;
    private ConstantStringIndexOfSnippets.Templates indexOfSnippets;

    public DefaultJavaLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, TargetDescription target, boolean useCompressedOops) {
        this.metaAccess = metaAccess;
        this.foreignCalls = foreignCalls;
        this.target = target;
        this.useCompressedOops = useCompressedOops;
    }

    public void initialize(OptionValues options, Iterable<DebugHandlersFactory> factories, SnippetCounter.Group.Factory factory, Providers providers, SnippetReflectionProvider snippetReflection) {
        boxingSnippets = new BoxingSnippets.Templates(options, factories, factory, providers, snippetReflection, target);
        indexOfSnippets = new ConstantStringIndexOfSnippets.Templates(options, factories, providers, snippetReflection, target);
        providers.getReplacements().registerSnippetTemplateCache(new SnippetCounterNode.SnippetCounterSnippets.Templates(options, factories, providers, snippetReflection, target));
    }

    public final TargetDescription getTarget() {
        return target;
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        assert n instanceof Lowerable;
        StructuredGraph graph = (StructuredGraph) n.graph();
        if (n instanceof LoadFieldNode) {
            lowerLoadFieldNode((LoadFieldNode) n, tool);
        } else if (n instanceof StoreFieldNode) {
            lowerStoreFieldNode((StoreFieldNode) n, tool);
        } else if (n instanceof LoadIndexedNode) {
            lowerLoadIndexedNode((LoadIndexedNode) n, tool);
        } else if (n instanceof StoreIndexedNode) {
            lowerStoreIndexedNode((StoreIndexedNode) n, tool);
        } else if (n instanceof ArrayLengthNode) {
            lowerArrayLengthNode((ArrayLengthNode) n, tool);
        } else if (n instanceof LoadHubNode) {
            lowerLoadHubNode((LoadHubNode) n, tool);
        } else if (n instanceof MonitorEnterNode) {
            lowerMonitorEnterNode((MonitorEnterNode) n, tool, graph);
        } else if (n instanceof UnsafeCompareAndSwapNode) {
            lowerCompareAndSwapNode((UnsafeCompareAndSwapNode) n);
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
            boxingSnippets.lower((BoxNode) n, tool);
        } else if (n instanceof UnboxNode) {
            boxingSnippets.lower((UnboxNode) n, tool);
        } else if (n instanceof VerifyHeapNode) {
            lowerVerifyHeap((VerifyHeapNode) n);
        } else if (n instanceof UnaryMathIntrinsicNode) {
            lowerUnaryMath((UnaryMathIntrinsicNode) n, tool);
        } else if (n instanceof BinaryMathIntrinsicNode) {
            lowerBinaryMath((BinaryMathIntrinsicNode) n, tool);
        } else if (n instanceof StringIndexOfNode) {
            lowerIndexOf((StringIndexOfNode) n);
        } else if (n instanceof UnpackEndianHalfNode) {
            lowerSecondHalf((UnpackEndianHalfNode) n);
        } else {
            throw GraalError.shouldNotReachHere("Node implementing Lowerable not handled: " + n);
        }
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

    private void lowerBinaryMath(BinaryMathIntrinsicNode math, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }
        ResolvedJavaMethod method = math.graph().method();
        if (method != null) {
            if (method.getAnnotation(Snippet.class) != null) {
                /*
                 * In the context of the snippet use the LIR lowering instead of the Node lowering.
                 */
                return;
            }
            if (method.getName().equalsIgnoreCase(math.getOperation().name()) && tool.getMetaAccess().lookupJavaType(Math.class).equals(method.getDeclaringClass())) {
                /*
                 * A root compilation of the intrinsic method should emit the full assembly
                 * implementation.
                 */
                return;
            }

        }
        ForeignCallDescriptor foreignCall = toForeignCall(math.getOperation());
        if (foreignCall != null) {
            StructuredGraph graph = math.graph();
            ForeignCallNode call = graph.add(new ForeignCallNode(foreignCalls, toForeignCall(math.getOperation()), math.getX(), math.getY()));
            graph.addAfterFixed(tool.lastFixedNode(), call);
            math.replaceAtUsages(call);
        }
    }

    private void lowerUnaryMath(UnaryMathIntrinsicNode math, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }
        ResolvedJavaMethod method = math.graph().method();
        if (method != null) {
            if (method.getAnnotation(Snippet.class) != null) {
                /*
                 * In the context of the snippet use the LIR lowering instead of the Node lowering.
                 */
                return;
            }
            if (method.getName().equalsIgnoreCase(math.getOperation().name()) && tool.getMetaAccess().lookupJavaType(Math.class).equals(method.getDeclaringClass())) {
                /*
                 * A root compilation of the intrinsic method should emit the full assembly
                 * implementation.
                 */
                return;
            }

        }
        ForeignCallDescriptor foreignCall = toForeignCall(math.getOperation());
        if (foreignCall != null) {
            StructuredGraph graph = math.graph();
            ForeignCallNode call = math.graph().add(new ForeignCallNode(foreignCalls, foreignCall, math.getValue()));
            graph.addAfterFixed(tool.lastFixedNode(), call);
            math.replaceAtUsages(call);
        }
    }

    protected ForeignCallDescriptor toForeignCall(UnaryOperation operation) {
        return operation.foreignCallDescriptor;
    }

    protected ForeignCallDescriptor toForeignCall(BinaryOperation operation) {
        return operation.foreignCallDescriptor;
    }

    protected void lowerVerifyHeap(VerifyHeapNode n) {
        GraphUtil.removeFixedWithUnusedInputs(n);
    }

    protected AddressNode createOffsetAddress(StructuredGraph graph, ValueNode object, long offset) {
        ValueNode o = ConstantNode.forIntegerKind(target.wordJavaKind, offset, graph);
        return graph.unique(new OffsetAddressNode(object, o));
    }

    protected AddressNode createFieldAddress(StructuredGraph graph, ValueNode object, ResolvedJavaField field) {
        int offset = fieldOffset(field);
        if (offset >= 0) {
            return createOffsetAddress(graph, object, offset);
        } else {
            return null;
        }
    }

    protected abstract JavaKind getStorageKind(ResolvedJavaField field);

    protected void lowerLoadFieldNode(LoadFieldNode loadField, LoweringTool tool) {
        assert loadField.getStackKind() != JavaKind.Illegal;
        StructuredGraph graph = loadField.graph();
        ResolvedJavaField field = loadField.field();
        ValueNode object = loadField.isStatic() ? staticFieldBase(graph, field) : loadField.object();
        object = createNullCheckedValue(object, loadField, tool);
        Stamp loadStamp = loadStamp(loadField.stamp(NodeView.DEFAULT), getStorageKind(field));

        AddressNode address = createFieldAddress(graph, object, field);
        assert address != null : "Field that is loaded must not be eliminated: " + field.getDeclaringClass().toJavaName(true) + "." + field.getName();

        ReadNode memoryRead = graph.add(new ReadNode(address, fieldLocationIdentity(field), loadStamp, fieldLoadBarrierType(field)));
        ValueNode readValue = implicitLoadConvert(graph, getStorageKind(field), memoryRead);
        loadField.replaceAtUsages(readValue);
        graph.replaceFixed(loadField, memoryRead);

        if (loadField.isVolatile()) {
            MembarNode preMembar = graph.add(new MembarNode(JMM_PRE_VOLATILE_READ));
            graph.addBeforeFixed(memoryRead, preMembar);
            MembarNode postMembar = graph.add(new MembarNode(JMM_POST_VOLATILE_READ));
            graph.addAfterFixed(memoryRead, postMembar);
        }
    }

    protected void lowerStoreFieldNode(StoreFieldNode storeField, LoweringTool tool) {
        StructuredGraph graph = storeField.graph();
        ResolvedJavaField field = storeField.field();
        ValueNode object = storeField.isStatic() ? staticFieldBase(graph, field) : storeField.object();
        object = createNullCheckedValue(object, storeField, tool);
        ValueNode value = implicitStoreConvert(graph, getStorageKind(storeField.field()), storeField.value());
        AddressNode address = createFieldAddress(graph, object, field);
        assert address != null;

        WriteNode memoryWrite = graph.add(new WriteNode(address, fieldLocationIdentity(field), value, fieldStoreBarrierType(storeField.field())));
        memoryWrite.setStateAfter(storeField.stateAfter());
        graph.replaceFixedWithFixed(storeField, memoryWrite);

        if (storeField.isVolatile()) {
            MembarNode preMembar = graph.add(new MembarNode(JMM_PRE_VOLATILE_WRITE));
            graph.addBeforeFixed(memoryWrite, preMembar);
            MembarNode postMembar = graph.add(new MembarNode(JMM_POST_VOLATILE_WRITE));
            graph.addAfterFixed(memoryWrite, postMembar);
        }
    }

    /**
     * Create a PiNode on the index proving that the index is positive. On some platforms this is
     * important to allow the index to be used as an int in the address mode.
     */
    public AddressNode createArrayIndexAddress(StructuredGraph graph, ValueNode array, JavaKind elementKind, ValueNode index, GuardingNode boundsCheck) {
        IntegerStamp indexStamp = StampFactory.forInteger(32, 0, Integer.MAX_VALUE - 1);
        ValueNode positiveIndex = graph.maybeAddOrUnique(PiNode.create(index, indexStamp, boundsCheck != null ? boundsCheck.asNode() : null));
        return createArrayAddress(graph, array, elementKind, positiveIndex);
    }

    public AddressNode createArrayAddress(StructuredGraph graph, ValueNode array, JavaKind elementKind, ValueNode index) {
        ValueNode wordIndex;
        if (target.wordSize > 4) {
            wordIndex = graph.unique(new SignExtendNode(index, target.wordSize * 8));
        } else {
            assert target.wordSize == 4 : "unsupported word size";
            wordIndex = index;
        }

        int shift = CodeUtil.log2(arrayScalingFactor(elementKind));
        ValueNode scaledIndex = graph.unique(new LeftShiftNode(wordIndex, ConstantNode.forInt(shift, graph)));

        int base = arrayBaseOffset(elementKind);
        ValueNode offset = graph.unique(new AddNode(scaledIndex, ConstantNode.forIntegerKind(target.wordJavaKind, base, graph)));

        return graph.unique(new OffsetAddressNode(array, offset));
    }

    protected void lowerLoadIndexedNode(LoadIndexedNode loadIndexed, LoweringTool tool) {
        StructuredGraph graph = loadIndexed.graph();
        ValueNode array = loadIndexed.array();
        array = createNullCheckedValue(array, loadIndexed, tool);
        JavaKind elementKind = loadIndexed.elementKind();
        Stamp loadStamp = loadStamp(loadIndexed.stamp(NodeView.DEFAULT), elementKind);

        GuardingNode boundsCheck = getBoundsCheck(loadIndexed, array, tool);
        AddressNode address = createArrayIndexAddress(graph, array, elementKind, loadIndexed.index(), boundsCheck);
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

        JavaKind elementKind = storeIndexed.elementKind();

        LogicNode condition = null;
        if (elementKind == JavaKind.Object && !StampTool.isPointerAlwaysNull(value)) {
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

        AddressNode address = createArrayIndexAddress(graph, array, elementKind, storeIndexed.index(), boundsCheck);
        WriteNode memoryWrite = graph.add(new WriteNode(address, NamedLocationIdentity.getArrayLocation(elementKind), implicitStoreConvert(graph, elementKind, value),
                        arrayStoreBarrierType(storeIndexed.elementKind())));
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

    protected void lowerMonitorEnterNode(MonitorEnterNode monitorEnter, LoweringTool tool, StructuredGraph graph) {
        ValueNode object = createNullCheckedValue(monitorEnter.object(), monitorEnter, tool);
        ValueNode hub = graph.addOrUnique(LoadHubNode.create(object, tool.getStampProvider(), tool.getMetaAccess(), tool.getConstantReflection()));
        RawMonitorEnterNode rawMonitorEnter = graph.add(new RawMonitorEnterNode(object, hub, monitorEnter.getMonitorId()));
        rawMonitorEnter.setStateBefore(monitorEnter.stateBefore());
        rawMonitorEnter.setStateAfter(monitorEnter.stateAfter());
        graph.replaceFixedWithFixed(monitorEnter, rawMonitorEnter);
    }

    protected void lowerCompareAndSwapNode(UnsafeCompareAndSwapNode cas) {
        StructuredGraph graph = cas.graph();
        JavaKind valueKind = cas.getValueKind();

        ValueNode expectedValue = implicitStoreConvert(graph, valueKind, cas.expected());
        ValueNode newValue = implicitStoreConvert(graph, valueKind, cas.newValue());

        AddressNode address = graph.unique(new OffsetAddressNode(cas.object(), cas.offset()));
        LogicCompareAndSwapNode atomicNode = graph.add(new LogicCompareAndSwapNode(address, cas.getLocationIdentity(), expectedValue, newValue, compareAndSwapBarrierType(cas)));
        atomicNode.setStateAfter(cas.stateAfter());
        graph.replaceFixedWithFixed(cas, atomicNode);
    }

    protected void lowerAtomicReadAndWriteNode(AtomicReadAndWriteNode n) {
        StructuredGraph graph = n.graph();
        JavaKind valueKind = n.getValueKind();

        ValueNode newValue = implicitStoreConvert(graph, valueKind, n.newValue());

        AddressNode address = graph.unique(new OffsetAddressNode(n.object(), n.offset()));
        LoweredAtomicReadAndWriteNode memoryRead = graph.add(new LoweredAtomicReadAndWriteNode(address, n.getLocationIdentity(), newValue, atomicReadAndWriteBarrierType(n)));
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
        ReadNode memoryRead = graph.add(new ReadNode(address, load.getLocationIdentity(), loadStamp, BarrierType.NONE));
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
        WriteNode write = graph.add(new WriteNode(address, store.getLocationIdentity(), value, unsafeStoreBarrierType(store)));
        write.setStateAfter(store.stateAfter());
        graph.replaceFixedWithFixed(store, write);
    }

    protected void lowerUnsafeMemoryStoreNode(UnsafeMemoryStoreNode store) {
        StructuredGraph graph = store.graph();
        assert store.getValue().getStackKind() != JavaKind.Object;
        JavaKind valueKind = store.getKind();
        ValueNode value = implicitStoreConvert(graph, valueKind, store.getValue(), false);
        AddressNode address = graph.addOrUniqueWithInputs(OffsetAddressNode.create(store.getAddress()));
        WriteNode write = graph.add(new WriteNode(address, store.getLocationIdentity(), value, BarrierType.NONE));
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
        WriteNode memoryWrite = graph.add(new WriteNode(write.getAddress(), write.getLocationIdentity(), value, write.getBarrierType()));
        memoryWrite.setStateAfter(write.stateAfter());
        graph.replaceFixedWithFixed(write, memoryWrite);
        memoryWrite.setGuard(write.getGuard());
    }

    protected void lowerCommitAllocationNode(CommitAllocationNode commit, LoweringTool tool) {
        StructuredGraph graph = commit.graph();
        if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
            List<AbstractNewObjectNode> recursiveLowerings = new ArrayList<>();

            ValueNode[] allocations = new ValueNode[commit.getVirtualObjects().size()];
            BitSet omittedValues = new BitSet();
            int valuePos = 0;
            for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
                VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
                int entryCount = virtual.entryCount();
                AbstractNewObjectNode newObject;
                if (virtual instanceof VirtualInstanceNode) {
                    newObject = graph.add(createNewInstanceFromVirtual(virtual));
                } else {
                    newObject = graph.add(createNewArrayFromVirtual(virtual, ConstantNode.forInt(entryCount, graph)));
                }
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
                        JavaKind entryKind = virtual.entryKind(i);

                        // Truffle requires some leniency in terms of what can be put where:
                        assert valueKind.getStackKind() == entryKind.getStackKind() ||
                                        (valueKind == JavaKind.Long || valueKind == JavaKind.Double || (valueKind == JavaKind.Int && virtual instanceof VirtualArrayNode));
                        AddressNode address = null;
                        BarrierType barrierType = null;
                        if (virtual instanceof VirtualInstanceNode) {
                            ResolvedJavaField field = ((VirtualInstanceNode) virtual).field(i);
                            long offset = fieldOffset(field);
                            if (offset >= 0) {
                                address = createOffsetAddress(graph, newObject, offset);
                                barrierType = fieldInitializationBarrier(entryKind);
                            }
                        } else {
                            address = createOffsetAddress(graph, newObject, arrayBaseOffset(entryKind) + i * arrayScalingFactor(entryKind));
                            barrierType = arrayInitializationBarrier(entryKind);
                        }
                        if (address != null) {
                            WriteNode write = new WriteNode(address, LocationIdentity.init(), implicitStoreConvert(graph, entryKind, value), barrierType);
                            graph.addAfterFixed(newObject, graph.add(write));
                        }
                    }
                    valuePos++;

                }
            }
            valuePos = 0;

            for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
                VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
                int entryCount = virtual.entryCount();
                ValueNode newObject = allocations[objIndex];
                for (int i = 0; i < entryCount; i++) {
                    if (omittedValues.get(valuePos)) {
                        ValueNode value = commit.getValues().get(valuePos);
                        assert value instanceof VirtualObjectNode;
                        ValueNode allocValue = allocations[commit.getVirtualObjects().indexOf(value)];
                        if (!(allocValue.isConstant() && allocValue.asConstant().isDefaultForKind())) {
                            assert virtual.entryKind(i) == JavaKind.Object && allocValue.getStackKind() == JavaKind.Object;
                            AddressNode address;
                            BarrierType barrierType;
                            if (virtual instanceof VirtualInstanceNode) {
                                VirtualInstanceNode virtualInstance = (VirtualInstanceNode) virtual;
                                address = createFieldAddress(graph, newObject, virtualInstance.field(i));
                                barrierType = BarrierType.IMPRECISE;
                            } else {
                                address = createArrayAddress(graph, newObject, virtual.entryKind(i), ConstantNode.forInt(i, graph));
                                barrierType = BarrierType.PRECISE;
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

            finishAllocatedObjects(tool, commit, allocations);
            graph.removeFixed(commit);

            for (AbstractNewObjectNode recursiveLowering : recursiveLowerings) {
                recursiveLowering.lower(tool);
            }
        }
    }

    public NewInstanceNode createNewInstanceFromVirtual(VirtualObjectNode virtual) {
        return new NewInstanceNode(virtual.type(), true);
    }

    protected NewArrayNode createNewArrayFromVirtual(VirtualObjectNode virtual, ValueNode length) {
        return new NewArrayNode(((VirtualArrayNode) virtual).componentType(), length, true);
    }

    public void finishAllocatedObjects(LoweringTool tool, CommitAllocationNode commit, ValueNode[] allocations) {
        StructuredGraph graph = commit.graph();
        for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
            FixedValueAnchorNode anchor = graph.add(new FixedValueAnchorNode(allocations[objIndex]));
            allocations[objIndex] = anchor;
            graph.addBeforeFixed(commit, anchor);
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
                graph.addBeforeFixed(commit, enter);
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
                commit.replaceAtUsages(InputType.Memory, enters.get(enters.size() - 1));
            }
        }
        if (enters != null) {
            for (MonitorEnterNode enter : enters) {
                enter.lower(tool);
            }
        }
        assert commit.hasNoUsages();
        insertAllocationBarrier(commit, graph);
    }

    /**
     * Insert the required {@link MemoryBarriers#STORE_STORE} barrier for an allocation and also
     * include the {@link MemoryBarriers#LOAD_STORE} required for final fields if any final fields
     * are being written, as if {@link FinalFieldBarrierNode} were emitted.
     */
    private static void insertAllocationBarrier(CommitAllocationNode commit, StructuredGraph graph) {
        int barrier = MemoryBarriers.STORE_STORE;
        outer: for (VirtualObjectNode vobj : commit.getVirtualObjects()) {
            for (ResolvedJavaField field : vobj.type().getInstanceFields(true)) {
                if (field.isFinal()) {
                    barrier = barrier | MemoryBarriers.LOAD_STORE;
                    break outer;
                }
            }
        }
        graph.addAfterFixed(commit, graph.add(new MembarNode(barrier, LocationIdentity.init())));
    }

    /**
     * @param field the field whose barrier type should be returned
     */
    protected BarrierType fieldLoadBarrierType(ResolvedJavaField field) {
        return BarrierType.NONE;
    }

    protected BarrierType fieldStoreBarrierType(ResolvedJavaField field) {
        if (field.getJavaKind() == JavaKind.Object) {
            return BarrierType.IMPRECISE;
        }
        return BarrierType.NONE;
    }

    protected BarrierType arrayStoreBarrierType(JavaKind elementKind) {
        if (elementKind == JavaKind.Object) {
            return BarrierType.PRECISE;
        }
        return BarrierType.NONE;
    }

    public BarrierType fieldInitializationBarrier(JavaKind entryKind) {
        return entryKind == JavaKind.Object ? BarrierType.IMPRECISE : BarrierType.NONE;
    }

    public BarrierType arrayInitializationBarrier(JavaKind entryKind) {
        return entryKind == JavaKind.Object ? BarrierType.PRECISE : BarrierType.NONE;
    }

    protected BarrierType unsafeStoreBarrierType(RawStoreNode store) {
        if (!store.needsBarrier()) {
            return BarrierType.NONE;
        }
        return storeBarrierType(store.object(), store.value());
    }

    protected BarrierType compareAndSwapBarrierType(UnsafeCompareAndSwapNode cas) {
        return storeBarrierType(cas.object(), cas.expected());
    }

    protected BarrierType atomicReadAndWriteBarrierType(AtomicReadAndWriteNode n) {
        return storeBarrierType(n.object(), n.newValue());
    }

    protected BarrierType storeBarrierType(ValueNode object, ValueNode value) {
        if (value.getStackKind() == JavaKind.Object) {
            ResolvedJavaType type = StampTool.typeOrNull(object);
            if (type != null && !type.isArray()) {
                return BarrierType.IMPRECISE;
            } else {
                return BarrierType.PRECISE;
            }
        }
        return BarrierType.NONE;
    }

    public abstract int fieldOffset(ResolvedJavaField field);

    public FieldLocationIdentity fieldLocationIdentity(ResolvedJavaField field) {
        return new FieldLocationIdentity(field);
    }

    public abstract ValueNode staticFieldBase(StructuredGraph graph, ResolvedJavaField field);

    public abstract int arrayLengthOffset();

    public abstract int arrayBaseOffset(JavaKind elementKind);

    public int arrayScalingFactor(JavaKind elementKind) {
        return target.arch.getPlatformKind(elementKind).getSizeInBytes();
    }

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

    protected GuardingNode getBoundsCheck(AccessIndexedNode n, ValueNode array, LoweringTool tool) {
        StructuredGraph graph = n.graph();
        ValueNode arrayLength = readArrayLength(array, tool.getConstantReflection());
        if (arrayLength == null) {
            arrayLength = createReadArrayLength(array, n, tool);
        } else {
            arrayLength = arrayLength.isAlive() ? arrayLength : graph.addOrUniqueWithInputs(arrayLength);
        }

        LogicNode boundsCheck = IntegerBelowNode.create(n.index(), arrayLength, NodeView.DEFAULT);
        if (boundsCheck.isTautology()) {
            return null;
        }
        return tool.createGuard(n, graph.addOrUniqueWithInputs(boundsCheck), BoundsCheckException, InvalidateReprofile);
    }

    protected GuardingNode createNullCheck(ValueNode object, FixedNode before, LoweringTool tool) {
        if (StampTool.isPointerNonNull(object)) {
            return null;
        }
        return tool.createGuard(before, before.graph().unique(IsNullNode.create(object)), NullCheckException, InvalidateReprofile, JavaConstant.NULL_POINTER, true);
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

        int base = arrayBaseOffset(elementKind);
        ValueNode scaledIndex = graph.unique(new SubNode(offset, ConstantNode.forIntegerStamp(offset.stamp(NodeView.DEFAULT), base, graph)));

        int shift = CodeUtil.log2(arrayScalingFactor(elementKind));
        ValueNode ret = graph.unique(new RightShiftNode(scaledIndex, ConstantNode.forInt(shift, graph)));
        return IntegerConvertNode.convert(ret, StampFactory.forKind(JavaKind.Int), graph, NodeView.DEFAULT);
    }
}
