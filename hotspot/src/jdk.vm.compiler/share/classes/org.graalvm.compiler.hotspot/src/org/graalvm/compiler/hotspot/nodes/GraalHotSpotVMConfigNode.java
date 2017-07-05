/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.nodes;

import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerator;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Represents {@link GraalHotSpotVMConfig} values that may change after compilation.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public class GraalHotSpotVMConfigNode extends FloatingNode implements LIRLowerable, Canonicalizable {
    public static final NodeClass<GraalHotSpotVMConfigNode> TYPE = NodeClass.create(GraalHotSpotVMConfigNode.class);

    private final GraalHotSpotVMConfig config;
    protected final int markId;

    public GraalHotSpotVMConfigNode(@InjectedNodeParameter GraalHotSpotVMConfig config, int markId, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind));
        this.config = config;
        this.markId = markId;
    }

    /**
     * Constructor selected by {@link #loadConfigValue(int, JavaKind)}.
     *
     * @param config
     * @param markId
     */
    public GraalHotSpotVMConfigNode(@InjectedNodeParameter GraalHotSpotVMConfig config, int markId) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean));
        this.config = config;
        this.markId = 0;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        Value res = ((HotSpotLIRGenerator) generator.getLIRGeneratorTool()).emitLoadConfigValue(markId);
        generator.setResult(this, res);
    }

    @NodeIntrinsic
    private static native boolean isConfigValueConstant(@ConstantNodeParameter int markId);

    @NodeIntrinsic
    private static native long loadConfigValue(@ConstantNodeParameter int markId, @ConstantNodeParameter JavaKind kind);

    public static long cardTableAddress() {
        return loadConfigValue(cardTableAddressMark(INJECTED_VMCONFIG), JavaKind.Long);
    }

    public static boolean isCardTableAddressConstant() {
        return isConfigValueConstant(cardTableAddressMark(INJECTED_VMCONFIG));
    }

    public static long heapTopAddress() {
        return loadConfigValue(heapTopAddressMark(INJECTED_VMCONFIG), JavaKind.Long);
    }

    public static long heapEndAddress() {
        return loadConfigValue(heapEndAddressMark(INJECTED_VMCONFIG), JavaKind.Long);
    }

    public static long crcTableAddress() {
        return loadConfigValue(crcTableAddressMark(INJECTED_VMCONFIG), JavaKind.Long);
    }

    public static int logOfHeapRegionGrainBytes() {
        return (int) loadConfigValue(logOfHeapRegionGrainBytesMark(INJECTED_VMCONFIG), JavaKind.Byte);
    }

    public static boolean inlineContiguousAllocationSupported() {
        return loadConfigValue(inlineContiguousAllocationSupportedMark(INJECTED_VMCONFIG), JavaKind.Byte) > 0;
    }

    @Fold
    public static int cardTableAddressMark(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.MARKID_CARD_TABLE_ADDRESS;
    }

    @Fold
    public static int heapTopAddressMark(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.MARKID_HEAP_TOP_ADDRESS;
    }

    @Fold
    public static int heapEndAddressMark(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.MARKID_HEAP_END_ADDRESS;
    }

    @Fold
    public static int crcTableAddressMark(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.MARKID_CRC_TABLE_ADDRESS;
    }

    @Fold
    public static int logOfHeapRegionGrainBytesMark(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.MARKID_LOG_OF_HEAP_REGION_GRAIN_BYTES;
    }

    @Fold
    public static int inlineContiguousAllocationSupportedMark(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.MARKID_INLINE_CONTIGUOUS_ALLOCATION_SUPPORTED;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (markId == 0) {
            return ConstantNode.forBoolean(!GeneratePIC.getValue());
        }
        if (!GeneratePIC.getValue()) {
            if (markId == cardTableAddressMark(config)) {
                return ConstantNode.forLong(config.cardtableStartAddress);
            } else if (markId == heapTopAddressMark(config)) {
                return ConstantNode.forLong(config.heapTopAddress);
            } else if (markId == heapEndAddressMark(config)) {
                return ConstantNode.forLong(config.heapEndAddress);
            } else if (markId == crcTableAddressMark(config)) {
                return ConstantNode.forLong(config.crcTableAddress);
            } else if (markId == logOfHeapRegionGrainBytesMark(config)) {
                return ConstantNode.forInt(config.logOfHRGrainBytes);
            } else if (markId == inlineContiguousAllocationSupportedMark(config)) {
                return ConstantNode.forBoolean(config.inlineContiguousAllocationSupported);
            } else {
                assert false;
            }
        }
        return this;
    }
}
