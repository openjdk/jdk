/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.asm.NumUtil;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.core.amd64.AMD64AddressLowering;
import org.graalvm.compiler.core.amd64.AMD64AddressNode;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.CompressEncoding;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.nodes.CompressionNode;
import org.graalvm.compiler.hotspot.nodes.CompressionNode.CompressionOp;
import org.graalvm.compiler.hotspot.nodes.GraalHotSpotVMConfigNode;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;

public class AMD64HotSpotAddressLowering extends AMD64AddressLowering {

    private final long heapBase;
    private final Register heapBaseRegister;
    private final GraalHotSpotVMConfig config;

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
            LIRKind kind = generator.getLIRGeneratorTool().getLIRKind(stamp());
            generator.setResult(this, heapBaseRegister.asValue(kind));
        }
    }

    public AMD64HotSpotAddressLowering(GraalHotSpotVMConfig config, Register heapBaseRegister) {
        this.heapBase = config.getOopEncoding().base;
        this.config = config;
        if (heapBase == 0 && !GeneratePIC.getValue()) {
            this.heapBaseRegister = null;
        } else {
            this.heapBaseRegister = heapBaseRegister;
        }
    }

    @Override
    protected boolean improve(AMD64AddressNode addr) {
        if (addr.getScale() == Scale.Times1) {
            if (addr.getBase() == null && addr.getIndex() instanceof CompressionNode) {
                if (improveUncompression(addr, (CompressionNode) addr.getIndex())) {
                    return true;
                }
            }

            if (addr.getIndex() == null && addr.getBase() instanceof CompressionNode) {
                if (improveUncompression(addr, (CompressionNode) addr.getBase())) {
                    return true;
                }
            }
        }

        return super.improve(addr);
    }

    private boolean improveUncompression(AMD64AddressNode addr, CompressionNode compression) {
        if (compression.getOp() == CompressionOp.Uncompress) {
            CompressEncoding encoding = compression.getEncoding();
            Scale scale = Scale.fromShift(encoding.shift);
            if (scale == null) {
                return false;
            }

            if (heapBaseRegister != null && encoding.base == heapBase) {
                if (!GeneratePIC.getValue() || compression.stamp() instanceof ObjectStamp) {
                    // With PIC it is only legal to do for oops since the base value may be
                    // different at runtime.
                    ValueNode base = compression.graph().unique(new HeapBaseNode(heapBaseRegister));
                    addr.setBase(base);
                } else {
                    return false;
                }
            } else if (encoding.base != 0 || (GeneratePIC.getValue() && compression.stamp() instanceof KlassPointerStamp)) {
                if (GeneratePIC.getValue()) {
                    ValueNode base = compression.graph().unique(new GraalHotSpotVMConfigNode(config, config.MARKID_NARROW_KLASS_BASE_ADDRESS, JavaKind.Long));
                    addr.setBase(base);
                } else {
                    long disp = addr.getDisplacement() + encoding.base;
                    if (NumUtil.isInt(disp)) {
                        addr.setDisplacement((int) disp);
                        addr.setBase(null);
                    } else {
                        return false;
                    }
                }
            } else {
                addr.setBase(null);
            }

            addr.setScale(scale);
            addr.setIndex(compression.getValue());
            return true;
        } else {
            return false;
        }
    }
}
