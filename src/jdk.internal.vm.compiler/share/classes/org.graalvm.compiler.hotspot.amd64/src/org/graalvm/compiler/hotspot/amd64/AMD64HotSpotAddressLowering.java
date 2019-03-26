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



package org.graalvm.compiler.hotspot.amd64;

import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;

import jdk.internal.vm.compiler.collections.EconomicMap;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.core.amd64.AMD64AddressNode;
import org.graalvm.compiler.core.amd64.AMD64CompressAddressLowering;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.nodes.GraalHotSpotVMConfigNode;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.loop.BasicInductionVariable;
import org.graalvm.compiler.loop.CountedLoopInfo;
import org.graalvm.compiler.loop.DerivedInductionVariable;
import org.graalvm.compiler.loop.InductionVariable;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;

public class AMD64HotSpotAddressLowering extends AMD64CompressAddressLowering {

    private static final int ADDRESS_BITS = 64;
    private static final int INT_BITS = 32;

    private final long heapBase;
    private final Register heapBaseRegister;
    private final GraalHotSpotVMConfig config;
    private final boolean generatePIC;

    public AMD64HotSpotAddressLowering(GraalHotSpotVMConfig config, Register heapBaseRegister, OptionValues options) {
        this.heapBase = config.getOopEncoding().getBase();
        this.config = config;
        this.generatePIC = GeneratePIC.getValue(options);
        if (heapBase == 0 && !generatePIC) {
            this.heapBaseRegister = null;
        } else {
            this.heapBaseRegister = heapBaseRegister;
        }
    }

    @Override
    protected final boolean improveUncompression(AMD64AddressNode addr, CompressionNode compression, ValueNode other) {
        CompressEncoding encoding = compression.getEncoding();
        Scale scale = Scale.fromShift(encoding.getShift());
        if (scale == null) {
            return false;
        }

        if (heapBaseRegister != null && encoding.getBase() == heapBase) {
            if ((!generatePIC || compression.stamp(NodeView.DEFAULT) instanceof ObjectStamp) && other == null) {
                // With PIC it is only legal to do for oops since the base value may be
                // different at runtime.
                ValueNode base = compression.graph().unique(new HeapBaseNode(heapBaseRegister));
                addr.setBase(base);
            } else {
                return false;
            }
        } else if (encoding.getBase() != 0 || (generatePIC && compression.stamp(NodeView.DEFAULT) instanceof KlassPointerStamp)) {
            if (generatePIC) {
                if (other == null) {
                    ValueNode base = compression.graph().unique(new GraalHotSpotVMConfigNode(config, config.MARKID_NARROW_KLASS_BASE_ADDRESS, JavaKind.Long));
                    addr.setBase(base);
                } else {
                    return false;
                }
            } else {
                if (updateDisplacement(addr, encoding.getBase(), false)) {
                    addr.setBase(other);
                } else {
                    return false;
                }
            }
        } else {
            addr.setBase(other);
        }

        addr.setScale(scale);
        addr.setIndex(compression.getValue());
        return true;
    }

    @Override
    public void preProcess(StructuredGraph graph) {
        if (graph.hasLoops()) {
            LoopsData loopsData = new LoopsData(graph);
            loopsData.detectedCountedLoops();
            for (LoopEx loop : loopsData.countedLoops()) {
                for (OffsetAddressNode offsetAdressNode : loop.whole().nodes().filter(OffsetAddressNode.class)) {
                    tryOptimize(offsetAdressNode, loop);
                }
            }
        }
    }

    @Override
    public void postProcess(AddressNode lowered) {
        // Allow implicit zero extend for always positive input. This
        // assumes that the upper bits of the operand is zero out by
        // the backend.
        AMD64AddressNode address = (AMD64AddressNode) lowered;
        address.setBase(tryImplicitZeroExtend(address.getBase()));
        address.setIndex(tryImplicitZeroExtend(address.getIndex()));
    }

    private static void tryOptimize(OffsetAddressNode offsetAddress, LoopEx loop) {
        EconomicMap<Node, InductionVariable> ivs = loop.getInductionVariables();
        InductionVariable currentIV = ivs.get(offsetAddress.getOffset());
        while (currentIV != null) {
            if (!(currentIV instanceof DerivedInductionVariable)) {
                break;
            }
            ValueNode currentValue = currentIV.valueNode();
            if (currentValue.isDeleted()) {
                break;
            }

            if (currentValue instanceof ZeroExtendNode) {
                ZeroExtendNode zeroExtendNode = (ZeroExtendNode) currentValue;
                if (applicableToImplicitZeroExtend(zeroExtendNode)) {
                    ValueNode input = zeroExtendNode.getValue();
                    if (input instanceof AddNode) {
                        AddNode add = (AddNode) input;
                        if (add.getX().isConstant()) {
                            optimizeAdd(zeroExtendNode, (ConstantNode) add.getX(), add.getY(), loop);
                        } else if (add.getY().isConstant()) {
                            optimizeAdd(zeroExtendNode, (ConstantNode) add.getY(), add.getX(), loop);
                        }
                    }
                }
            }

            currentIV = ((DerivedInductionVariable) currentIV).getBase();
        }
    }

    /**
     * Given that Add(a, cst) is always positive, performs the following: ZeroExtend(Add(a, cst)) ->
     * Add(SignExtend(a), SignExtend(cst)).
     */
    private static void optimizeAdd(ZeroExtendNode zeroExtendNode, ConstantNode constant, ValueNode other, LoopEx loop) {
        StructuredGraph graph = zeroExtendNode.graph();
        AddNode addNode = graph.unique(new AddNode(signExtend(other, loop), ConstantNode.forLong(constant.asJavaConstant().asInt(), graph)));
        zeroExtendNode.replaceAtUsages(addNode);
    }

    /**
     * Create a sign extend for {@code input}, or zero extend if {@code input} can be proven
     * positive.
     */
    private static ValueNode signExtend(ValueNode input, LoopEx loop) {
        StructuredGraph graph = input.graph();
        if (input instanceof PhiNode) {
            EconomicMap<Node, InductionVariable> ivs = loop.getInductionVariables();
            InductionVariable inductionVariable = ivs.get(input);
            if (inductionVariable != null && inductionVariable instanceof BasicInductionVariable) {
                CountedLoopInfo countedLoopInfo = loop.counted();
                IntegerStamp initStamp = (IntegerStamp) inductionVariable.initNode().stamp(NodeView.DEFAULT);
                if (initStamp.isPositive()) {
                    if (inductionVariable.isConstantExtremum() && countedLoopInfo.counterNeverOverflows()) {
                        long init = inductionVariable.constantInit();
                        long stride = inductionVariable.constantStride();
                        long extremum = inductionVariable.constantExtremum();

                        if (init >= 0 && extremum >= 0) {
                            long shortestTrip = (extremum - init) / stride + 1;
                            if (countedLoopInfo.constantMaxTripCount().equals(shortestTrip)) {
                                return graph.unique(new ZeroExtendNode(input, INT_BITS, ADDRESS_BITS, true));
                            }
                        }
                    }
                    if (countedLoopInfo.getCounter() == inductionVariable &&
                                    inductionVariable.direction() == InductionVariable.Direction.Up &&
                                    (countedLoopInfo.getOverFlowGuard() != null || countedLoopInfo.counterNeverOverflows())) {
                        return graph.unique(new ZeroExtendNode(input, INT_BITS, ADDRESS_BITS, true));
                    }
                }
            }
        }
        return input.graph().maybeAddOrUnique(SignExtendNode.create(input, ADDRESS_BITS, NodeView.DEFAULT));
    }

    private static boolean applicableToImplicitZeroExtend(ZeroExtendNode zeroExtendNode) {
        return zeroExtendNode.isInputAlwaysPositive() && zeroExtendNode.getInputBits() == INT_BITS && zeroExtendNode.getResultBits() == ADDRESS_BITS;
    }

    private static ValueNode tryImplicitZeroExtend(ValueNode input) {
        if (input instanceof ZeroExtendNode) {
            ZeroExtendNode zeroExtendNode = (ZeroExtendNode) input;
            if (applicableToImplicitZeroExtend(zeroExtendNode)) {
                return zeroExtendNode.getValue();
            }
        }
        return input;
    }

}
