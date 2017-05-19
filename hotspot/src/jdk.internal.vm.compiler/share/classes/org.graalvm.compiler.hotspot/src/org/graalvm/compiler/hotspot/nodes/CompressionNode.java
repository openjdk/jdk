/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.hotspot.CompressEncoding;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerator;
import org.graalvm.compiler.hotspot.nodes.type.KlassPointerStamp;
import org.graalvm.compiler.hotspot.nodes.type.NarrowOopStamp;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConvertNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.type.StampTool;

import jdk.vm.ci.hotspot.HotSpotCompressedNullConstant;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

/**
 * Compress or uncompress an oop or metaspace pointer.
 */
@NodeInfo(nameTemplate = "{p#op/s}", cycles = CYCLES_2, size = SIZE_2)
public final class CompressionNode extends UnaryNode implements ConvertNode, LIRLowerable {

    public static final NodeClass<CompressionNode> TYPE = NodeClass.create(CompressionNode.class);

    public enum CompressionOp {
        Compress,
        Uncompress
    }

    protected final CompressionOp op;
    protected final CompressEncoding encoding;

    public CompressionNode(CompressionOp op, ValueNode input, CompressEncoding encoding) {
        super(TYPE, mkStamp(op, input.stamp(), encoding), input);
        this.op = op;
        this.encoding = encoding;
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        assert newStamp.isCompatible(getValue().stamp());
        return mkStamp(op, newStamp, encoding);
    }

    public static CompressionNode compress(ValueNode input, CompressEncoding encoding) {
        return input.graph().unique(new CompressionNode(CompressionOp.Compress, input, encoding));
    }

    public static CompressionNode compressNoUnique(ValueNode input, CompressEncoding encoding) {
        return new CompressionNode(CompressionOp.Compress, input, encoding);
    }

    public static CompressionNode uncompress(ValueNode input, CompressEncoding encoding) {
        return input.graph().unique(new CompressionNode(CompressionOp.Uncompress, input, encoding));
    }

    private static Constant compress(Constant c) {
        if (JavaConstant.NULL_POINTER.equals(c)) {
            return HotSpotCompressedNullConstant.COMPRESSED_NULL;
        } else if (c instanceof HotSpotConstant) {
            return ((HotSpotConstant) c).compress();
        } else {
            throw GraalError.shouldNotReachHere("invalid constant input for compress op: " + c);
        }
    }

    private static Constant uncompress(Constant c) {
        if (c instanceof HotSpotConstant) {
            return ((HotSpotConstant) c).uncompress();
        } else {
            throw GraalError.shouldNotReachHere("invalid constant input for uncompress op: " + c);
        }
    }

    @Override
    public Constant convert(Constant c, ConstantReflectionProvider constantReflection) {
        switch (op) {
            case Compress:
                return compress(c);
            case Uncompress:
                return uncompress(c);
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    @Override
    public Constant reverse(Constant c, ConstantReflectionProvider constantReflection) {
        switch (op) {
            case Compress:
                return uncompress(c);
            case Uncompress:
                return compress(c);
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    @Override
    public boolean isLossless() {
        return true;
    }

    private static Stamp mkStamp(CompressionOp op, Stamp input, CompressEncoding encoding) {
        switch (op) {
            case Compress:
                if (input instanceof ObjectStamp) {
                    // compressed oop
                    return NarrowOopStamp.compressed((ObjectStamp) input, encoding);
                } else if (input instanceof KlassPointerStamp) {
                    // compressed klass pointer
                    return ((KlassPointerStamp) input).compressed(encoding);
                }
                break;
            case Uncompress:
                if (input instanceof NarrowOopStamp) {
                    // oop
                    assert encoding.equals(((NarrowOopStamp) input).getEncoding());
                    return ((NarrowOopStamp) input).uncompressed();
                } else if (input instanceof KlassPointerStamp) {
                    // metaspace pointer
                    assert encoding.equals(((KlassPointerStamp) input).getEncoding());
                    return ((KlassPointerStamp) input).uncompressed();
                }
                break;
        }
        throw GraalError.shouldNotReachHere(String.format("Unexpected input stamp %s", input));
    }

    public CompressionOp getOp() {
        return op;
    }

    public CompressEncoding getEncoding() {
        return encoding;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant()) {
            if (GeneratePIC.getValue()) {
                // We always want uncompressed constants
                return this;
            }
            int stableDimension = ((ConstantNode) forValue).getStableDimension();
            boolean isDefaultStable = ((ConstantNode) forValue).isDefaultStable();
            return ConstantNode.forConstant(stamp(), convert(forValue.asConstant(), tool.getConstantReflection()), stableDimension, isDefaultStable, tool.getMetaAccess());
        } else if (forValue instanceof CompressionNode) {
            CompressionNode other = (CompressionNode) forValue;
            if (op != other.op && encoding.equals(other.encoding)) {
                return other.getValue();
            }
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        HotSpotLIRGenerator hsGen = (HotSpotLIRGenerator) gen.getLIRGeneratorTool();
        boolean nonNull;
        if (getValue().stamp() instanceof AbstractObjectStamp) {
            nonNull = StampTool.isPointerNonNull(getValue().stamp());
        } else {
            // metaspace pointers are never null
            nonNull = true;
        }

        Value result;
        switch (op) {
            case Compress:
                result = hsGen.emitCompress(gen.operand(getValue()), encoding, nonNull);
                break;
            case Uncompress:
                result = hsGen.emitUncompress(gen.operand(getValue()), encoding, nonNull);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    public static native Object compression(@ConstantNodeParameter CompressionOp op, Object object, @ConstantNodeParameter CompressEncoding encoding);
}
