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


package org.graalvm.compiler.hotspot.nodes;

import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerator;
import org.graalvm.compiler.hotspot.HotSpotMarkId;
import org.graalvm.compiler.hotspot.HotSpotReplacementsImpl;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

/**
 * Represents {@link GraalHotSpotVMConfig} values that may change after compilation.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public class GraalHotSpotVMConfigNode extends FloatingNode implements LIRLowerable, Canonicalizable {
    public static final NodeClass<GraalHotSpotVMConfigNode> TYPE = NodeClass.create(GraalHotSpotVMConfigNode.class);

    private final GraalHotSpotVMConfig config;
    protected final HotSpotMarkId markId;

    /**
     * Constructor for node intrinsics below.
     *
     * @param config
     * @param markId id of the config value
     */
    public GraalHotSpotVMConfigNode(@InjectedNodeParameter Stamp stamp, @InjectedNodeParameter GraalHotSpotVMConfig config, HotSpotMarkId markId) {
        super(TYPE, stamp);
        this.config = config;
        this.markId = markId;
    }

    /**
     * Constructor with explicit type specification.
     *
     * @param config
     * @param markId id of the config value
     * @param kind explicit type of the node
     */
    public GraalHotSpotVMConfigNode(GraalHotSpotVMConfig config, HotSpotMarkId markId, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind));
        this.config = config;
        this.markId = markId;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        Value res = ((HotSpotLIRGenerator) generator.getLIRGeneratorTool()).emitLoadConfigValue(markId, generator.getLIRGeneratorTool().getLIRKind(stamp));
        generator.setResult(this, res);
    }

    @NodeIntrinsic
    private static native long loadLongConfigValue(@ConstantNodeParameter HotSpotMarkId markId);

    @NodeIntrinsic
    private static native int loadIntConfigValue(@ConstantNodeParameter HotSpotMarkId markId);

    public static long cardTableAddress() {
        return loadLongConfigValue(HotSpotMarkId.CARD_TABLE_ADDRESS);
    }

    public static long crcTableAddress() {
        return loadLongConfigValue(HotSpotMarkId.CRC_TABLE_ADDRESS);
    }

    public static int logOfHeapRegionGrainBytes() {
        return loadIntConfigValue(HotSpotMarkId.LOG_OF_HEAP_REGION_GRAIN_BYTES);
    }

    @SuppressWarnings("unused")
    public static boolean intrinsify(GraphBuilderContext b, ResolvedJavaMethod method, @InjectedNodeParameter Stamp returnStamp, @InjectedNodeParameter GraalHotSpotVMConfig config,
                    HotSpotMarkId mark) {
        HotSpotReplacementsImpl replacements = (HotSpotReplacementsImpl) b.getReplacements();
        if (replacements.isEncodingSnippets()) {
            // This plugin must be deferred so that these constants aren't embedded in libgraal
            return false;
        }
        b.addPush(returnStamp.getStackKind(), new GraalHotSpotVMConfigNode(returnStamp, config, mark));
        return true;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        Boolean generatePIC = GeneratePIC.getValue(tool.getOptions());
        if (markId == null) {
            return ConstantNode.forBoolean(!generatePIC);
        } else if (!generatePIC) {
            if (markId == HotSpotMarkId.CARD_TABLE_ADDRESS) {
                return ConstantNode.forLong(config.cardtableStartAddress);
            } else if (markId == HotSpotMarkId.CRC_TABLE_ADDRESS) {
                return ConstantNode.forLong(config.crcTableAddress);
            } else if (markId == HotSpotMarkId.LOG_OF_HEAP_REGION_GRAIN_BYTES) {
                return ConstantNode.forInt(config.logOfHRGrainBytes);
            } else {
                throw GraalError.shouldNotReachHere(markId.toString());
            }
        }
        return this;
    }
}
