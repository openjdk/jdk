/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

/**
 * This node is a placeholder for all variants of intrinsified indexof-operations. It may be lowered
 * to a {@link AMD64ArrayIndexOfNode} or a specialized snippet.
 */
@NodeInfo(size = SIZE_512, cycles = NodeCycles.CYCLES_UNKNOWN)
public class AMD64ArrayIndexOfDispatchNode extends FixedWithNextNode implements Lowerable, MemoryAccess, DeoptimizingNode.DeoptBefore {

    public static final NodeClass<AMD64ArrayIndexOfDispatchNode> TYPE = NodeClass.create(AMD64ArrayIndexOfDispatchNode.class);

    private final ForeignCallDescriptor stubCallDescriptor;
    private final JavaKind arrayKind;
    private final JavaKind valueKind;
    private final boolean findTwoConsecutive;

    @Input private ValueNode arrayPointer;
    @Input private ValueNode arrayLength;
    @Input private ValueNode fromIndex;
    @Input private NodeInputList<ValueNode> searchValues;

    @OptionalInput(InputType.Memory) private MemoryNode lastLocationAccess;
    @OptionalInput(InputType.State) protected FrameState stateBefore;

    public AMD64ArrayIndexOfDispatchNode(@ConstantNodeParameter ForeignCallDescriptor stubCallDescriptor, @ConstantNodeParameter JavaKind arrayKind, @ConstantNodeParameter JavaKind valueKind,
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

    public ForeignCallDescriptor getStubCallDescriptor() {
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

    public AMD64ArrayIndexOfDispatchNode(@ConstantNodeParameter ForeignCallDescriptor stubCallDescriptor, @ConstantNodeParameter JavaKind arrayKind, @ConstantNodeParameter JavaKind valueKind,
                    ValueNode arrayPointer, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        this(stubCallDescriptor, arrayKind, valueKind, false, arrayPointer, arrayLength, fromIndex, searchValues);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(arrayKind);
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public MemoryNode getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryNode lla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(lla));
        lastLocationAccess = lla;
    }

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallDescriptor descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallDescriptor descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte v2);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallDescriptor descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallDescriptor descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3, byte v4);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallDescriptor descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallDescriptor descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char v2);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallDescriptor descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char v2, char v3);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallDescriptor descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char v2, char v3, char v4);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter ForeignCallDescriptor descriptor,
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, int searchValue);

    public static int indexOf(@ConstantNodeParameter ForeignCallDescriptor descriptor, byte[] array, int arrayLength, int fromIndex, byte v1) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallDescriptor descriptor, byte[] array, int arrayLength, int fromIndex, byte v1, byte v2) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1, v2);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallDescriptor descriptor, byte[] array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1, v2, v3);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallDescriptor descriptor, byte[] array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3, byte v4) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallDescriptor descriptor, byte[] array, int arrayLength, int fromIndex, char v1) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallDescriptor descriptor, byte[] array, int arrayLength, int fromIndex, char v1, char v2) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallDescriptor descriptor, byte[] array, int arrayLength, int fromIndex, char v1, char v2, char v3) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallDescriptor descriptor, byte[] array, int arrayLength, int fromIndex, char v1, char v2, char v3, char v4) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallDescriptor descriptor, char[] array, int arrayLength, int fromIndex, char v1) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallDescriptor descriptor, char[] array, int arrayLength, int fromIndex, char v1, char v2) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallDescriptor descriptor, char[] array, int arrayLength, int fromIndex, char v1, char v2, char v3) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3);
    }

    public static int indexOf(@ConstantNodeParameter ForeignCallDescriptor descriptor, char[] array, int arrayLength, int fromIndex, char v1, char v2, char v3, char v4) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    public static int indexOf2ConsecutiveBytes(@ConstantNodeParameter ForeignCallDescriptor descriptor, byte[] array, int arrayLength, int fromIndex, int values) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Byte, true, array, arrayLength, fromIndex, values);
    }

    public static int indexOf2ConsecutiveChars(@ConstantNodeParameter ForeignCallDescriptor descriptor, byte[] array, int arrayLength, int fromIndex, int values) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Byte, JavaKind.Char, true, array, arrayLength, fromIndex, values);
    }

    public static int indexOf2ConsecutiveChars(@ConstantNodeParameter ForeignCallDescriptor descriptor, char[] array, int arrayLength, int fromIndex, int values) {
        return optimizedArrayIndexOf(descriptor, JavaKind.Char, JavaKind.Char, true, array, arrayLength, fromIndex, values);
    }
}
