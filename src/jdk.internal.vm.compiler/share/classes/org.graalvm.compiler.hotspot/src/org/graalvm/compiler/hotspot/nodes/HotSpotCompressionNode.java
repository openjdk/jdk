/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.nodes.type.HotSpotNarrowOopStamp;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.CompressionNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.hotspot.HotSpotCompressedNullConstant;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;

@NodeInfo(nameTemplate = "{p#op/s}", cycles = CYCLES_2, size = SIZE_2)
public final class HotSpotCompressionNode extends CompressionNode {

    public static final NodeClass<HotSpotCompressionNode> TYPE = NodeClass.create(HotSpotCompressionNode.class);

    public HotSpotCompressionNode(CompressionOp op, ValueNode input, CompressEncoding encoding) {
        super(TYPE, op, input, HotSpotNarrowOopStamp.mkStamp(op, input.stamp(NodeView.DEFAULT), encoding), encoding);
    }

    public static HotSpotCompressionNode compress(ValueNode input, CompressEncoding encoding) {
        return input.graph().unique(new HotSpotCompressionNode(CompressionOp.Compress, input, encoding));
    }

    public static CompressionNode uncompress(ValueNode input, CompressEncoding encoding) {
        return input.graph().unique(new HotSpotCompressionNode(CompressionOp.Uncompress, input, encoding));
    }

    @Override
    protected Constant compress(Constant c) {
        if (JavaConstant.NULL_POINTER.equals(c)) {
            return HotSpotCompressedNullConstant.COMPRESSED_NULL;
        } else if (c instanceof HotSpotConstant) {
            return ((HotSpotConstant) c).compress();
        } else {
            throw GraalError.shouldNotReachHere("invalid constant input for compress op: " + c);
        }
    }

    @Override
    protected Constant uncompress(Constant c) {
        if (c instanceof HotSpotConstant) {
            return ((HotSpotConstant) c).uncompress();
        } else {
            throw GraalError.shouldNotReachHere("invalid constant input for uncompress op: " + c);
        }
    }

    @Override
    protected Stamp mkStamp(Stamp input) {
        return HotSpotNarrowOopStamp.mkStamp(op, input, encoding);
    }
}
