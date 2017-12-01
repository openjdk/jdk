/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.core.amd64.AMD64AddressLowering;
import org.graalvm.compiler.core.amd64.AMD64AddressNode;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.nodes.GraalHotSpotVMConfigNode;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.CompressionNode.CompressionOp;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;

public class AMD64HotSpotAddressLowering extends AMD64AddressLowering {

    private static final CounterKey counterFoldedUncompressDuringAddressLowering = DebugContext.counter("FoldedUncompressDuringAddressLowering");

    private final long heapBase;
    private final Register heapBaseRegister;
    private final GraalHotSpotVMConfig config;
    private final boolean generatePIC;

    @NodeInfo(cycles = CYCLES_0, size = SIZE_0)
    public static class HeapBaseNode extends FloatingNode implements LIRLowerable {

        public static final NodeClass<HeapBaseNode> TYPE = NodeClass.create(HeapBaseNode.class);

        private final Register heapBaseRegister;

        public HeapBaseNode(Register heapBaseRegister) {
            super(TYPE, StampFactory.pointer());
            this.heapBaseRegister = heapBaseRegister;
        }

        @Override
        public void generate(NodeLIRBuilderTool generator) {
            LIRKind kind = generator.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
            generator.setResult(this, heapBaseRegister.asValue(kind));
        }
    }

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
    protected boolean improve(StructuredGraph graph, DebugContext debug, AMD64AddressNode addr, boolean isBaseNegated, boolean isIndexNegated) {
        if (super.improve(graph, debug, addr, isBaseNegated, isIndexNegated)) {
            return true;
        }

        if (addr.getScale() == Scale.Times1) {
            if (addr.getIndex() instanceof CompressionNode) {
                if (improveUncompression(addr, (CompressionNode) addr.getIndex(), addr.getBase(), isBaseNegated, isIndexNegated)) {
                    counterFoldedUncompressDuringAddressLowering.increment(debug);
                    return true;
                }
            }

            if (addr.getBase() instanceof CompressionNode) {
                if (improveUncompression(addr, (CompressionNode) addr.getBase(), addr.getIndex(), isBaseNegated, isIndexNegated)) {
                    counterFoldedUncompressDuringAddressLowering.increment(debug);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean improveUncompression(AMD64AddressNode addr, CompressionNode compression, ValueNode other, boolean isBaseNegated, boolean isIndexNegated) {
        if (isBaseNegated || isIndexNegated || compression.getOp() != CompressionOp.Uncompress) {
            return false;
        }

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
                if (updateDisplacement(addr, encoding.getBase(), isBaseNegated)) {
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
}
