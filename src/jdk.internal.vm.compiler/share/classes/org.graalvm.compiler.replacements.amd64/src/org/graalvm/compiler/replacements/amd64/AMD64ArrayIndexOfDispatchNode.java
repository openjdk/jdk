/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.amd64;

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_512;

import java.nio.ByteOrder;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * This node is a placeholder for all variants of intrinsified indexof-operations. It may be lowered
 * to a {@link AMD64ArrayIndexOfNode} or a specialized snippet.
 */
@NodeInfo(size = SIZE_512, cycles = NodeCycles.CYCLES_UNKNOWN)
public class AMD64ArrayIndexOfDispatchNode extends FixedWithNextNode implements Canonicalizable, Lowerable, MemoryAccess, DeoptimizingNode.DeoptBefore {

    public static final NodeClass<AMD64ArrayIndexOfDispatchNode> TYPE = NodeClass.create(AMD64ArrayIndexOfDispatchNode.class);

    private final ForeignCallSignature stubCallDescriptor;
    private final JavaKind arrayKind;
    private final JavaKind valueKind;
    private final boolean findTwoConsecutive;

    @Input private ValueNode arrayPointer;
    @Input private ValueNode arrayLength;
    @Input private ValueNode fromIndex;
    @Input private NodeInputList<ValueNode> searchValues;

    @OptionalInput(InputType.Memory) private MemoryKill lastLocationAccess;
    @OptionalInput(InputType.State) protected FrameState stateBefore;

    public AMD64ArrayIndexOfDispatchNode(@ConstantNodeParameter ForeignCallSignature stubCallDescriptor, @ConstantNodeParameter JavaKind arrayKind, @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive, ValueNode arrayPointer, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.stubCallDescriptor = stubCallDescriptor;
        this.arrayKind = arrayKind;
        this.valueKind = valueKind;
        this.findTwoConsecutive = findTwoConsecutive;
        this.arrayPointer = arrayPointer;
        this.arrayLength = arrayLength;
        this.fromIndex = fromIndex;
        this.searchValues = new NodeInputList<>(this, searchValues);
    }

    public boolean isFindTwoConsecutive() {
        return findTwoConsecutive;
    }

    public ValueNode getArrayPointer() {
        return arrayPointer;
    }

    public ValueNode getArrayLength() {
        return arrayLength;
    }

    public ValueNode getFromIndex() {
        return fromIndex;
    }

    public NodeInputList<ValueNode> getSearchValues() {
        return searchValues;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public void setStateBefore(FrameState f) {
        updateUsages(stateBefore, f);
        stateBefore = f;
    }

    @Override
    public FrameState stateBefore() {
        return stateBefore;
    }

    public ForeignCallSignature getStubCallDescriptor() {
        return stubCallDescriptor;
    }

    public int getNumberOfValues() {
        return searchValues.size();
    }

    public JavaKind getArrayKind() {
        return arrayKind;
    }

    public JavaKind getValueKind() {
        return valueKind;
    }

    public JavaKind getComparisonKind() {
        return findTwoConsecutive ? (valueKind == JavaKind.Byte ? JavaKind.Char : JavaKind.Int) : valueKind;
    }

    public ValueNode[] getStubCallArgs() {
        ValueNode[] ret = new ValueNode[searchValues.size() + 3];
        ret[0] = arrayPointer;
        ret[1] = arrayLength;
        ret[2] = fromIndex;
        for (int i = 0; i < searchValues.size(); i++) {
            ret[3 + i] = searchValues.get(i);
        }
        return ret;
    }

    public AMD64ArrayIndexOfDispatchNode(@ConstantNodeParameter ForeignCallSignature stubCallDescriptor, @ConstantNodeParameter JavaKind arrayKind, @ConstantNodeParameter JavaKind valueKind,
                    ValueNode arrayPointer, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        this(stubCallDescriptor, arrayKind, valueKind, false, arrayPointer, arrayLength, fromIndex, searchValues);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(arrayKind);
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (!findTwoConsecutive && arrayPointer.isConstant() && ((ConstantNode) arrayPointer).getStableDimension() > 0 && fromIndex.isConstant()) {
            ConstantReflectionProvider provider = tool.getConstantReflection();
            JavaConstant arrayConstant = arrayPointer.asJavaConstant();
            int length = provider.readArrayLength(arrayConstant);
            int fromIndexConstant = fromIndex.asJavaConstant().asInt();

            if (searchValues.size() == 1 && searchValues.get(0).isConstant()) {
                int ch = searchValues.get(0).asJavaConstant().asInt();
                assert ch < Character.MIN_SUPPLEMENTARY_CODE_POINT;
                if (arrayKind == JavaKind.Byte) {
                    // Java 9+
                    if (valueKind == JavaKind.Byte && length < GraalOptions.StringIndexOfLimit.getValue(tool.getOptions())) {
                        for (int i = fromIndexConstant; i < length; i++) {
                            if ((provider.readArrayElement(arrayConstant, i).asInt() & 0xFF) == ch) {
                                return ConstantNode.forInt(i);
                            }
                        }
                        return ConstantNode.forInt(-1);
                    } else if (length < GraalOptions.StringIndexOfLimit.getValue(tool.getOptions()) * 2) {
                        assert valueKind == JavaKind.Char;
                        length >>= 1;
                        for (int i = fromIndexConstant; i < length; i++) {
                            int b0 = provider.readArrayElement(arrayConstant, i * 2).asInt() & 0xFF;
                            int b1 = provider.readArrayElement(arrayConstant, i * 2 + 1).asInt() & 0xFF;
                            char c;
                            if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                                c = (char) ((b0 << 8) | b1);
                            } else {
                                c = (char) (b0 | (b1 << 8));
                            }
                            if (c == ch) {
                                return ConstantNode.forInt(i);
                            }
                        }
                        return ConstantNode.forInt(-1);
                    }
                } else if (arrayKind == JavaKind.Char && length < GraalOptions.StringIndexOfLimit.getValue(tool.getOptions())) {
                    // Java 8
                    assert valueKind == JavaKind.Char;
                    for (int i = fromIndexConstant; i < length; i++) {
                        if ((provider.readArrayElement(arrayConstant, i).asInt() & 0xFFFF) == ch) {
                            return ConstantNode.forInt(i);
                        }
                    }
                    return ConstantNode.forInt(-1);
                }
            }
        }
        return this;
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(lla));
        lastLocationAccess = lla;
    }

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte v2);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3, byte v4);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char v2);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char v2, char v3);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char v2, char v3, char v4);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallSignature descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, int searchValue);

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, byte v1) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, byte v1, byte v2) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1, v2);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1, v2, v3);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3, byte v4) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, char v1) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, char v1, char v2) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, char v1, char v2, char v3) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, char v1, char v2, char v3, char v4) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, char[] array, int arrayLength, int fromIndex, char v1) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, char[] array, int arrayLength, int fromIndex, char v1, char v2) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, char[] array, int arrayLength, int fromIndex, char v1, char v2, char v3) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallSignature descriptor, char[] array, int arrayLength, int fromIndex, char v1, char v2, char v3, char v4) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    public static int indexOf2ConsecutiveBytes(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, int values) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Byte, true, array, arrayLength, fromIndex, values);
    }

    public static int indexOf2ConsecutiveChars(@ConstantNodeParameter ForeignCallSignature descriptor, byte[] array, int arrayLength, int fromIndex, int values) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Char, true, array, arrayLength, fromIndex, values);
    }

    public static int indexOf2ConsecutiveChars(@ConstantNodeParameter ForeignCallSignature descriptor, char[] array, int arrayLength, int fromIndex, int values) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Char, JavaKind.Char, true, array, arrayLength, fromIndex, values);
    }
}
