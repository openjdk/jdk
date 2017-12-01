/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

// JaCoCo Exclude

/**
 * Compares two arrays with the same length.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_128)
public final class ArrayEqualsNode extends FixedWithNextNode implements LIRLowerable, Canonicalizable, Virtualizable, MemoryAccess {

    public static final NodeClass<ArrayEqualsNode> TYPE = NodeClass.create(ArrayEqualsNode.class);
    /** {@link JavaKind} of the arrays to compare. */
    protected final JavaKind kind;

    /** One array to be tested for equality. */
    @Input ValueNode array1;

    /** The other array to be tested for equality. */
    @Input ValueNode array2;

    /** Length of both arrays. */
    @Input ValueNode length;

    @OptionalInput(Memory) MemoryNode lastLocationAccess;

    public ArrayEqualsNode(ValueNode array1, ValueNode array2, ValueNode length, @ConstantNodeParameter JavaKind kind) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean));
        this.kind = kind;
        this.array1 = array1;
        this.array2 = array2;
        this.length = length;
    }

    public ValueNode getArray1() {
        return array1;
    }

    public ValueNode getArray2() {
        return array2;
    }

    public ValueNode getLength() {
        return length;
    }

    private static boolean isNaNFloat(JavaConstant constant) {
        JavaKind kind = constant.getJavaKind();
        return (kind == JavaKind.Float && Float.isNaN(constant.asFloat())) || (kind == JavaKind.Double && Double.isNaN(constant.asDouble()));
    }

    private static boolean arrayEquals(ConstantReflectionProvider constantReflection, JavaConstant a, JavaConstant b, int len) {
        for (int i = 0; i < len; i++) {
            JavaConstant aElem = constantReflection.readArrayElement(a, i);
            JavaConstant bElem = constantReflection.readArrayElement(b, i);
            if (!constantReflection.constantEquals(aElem, bElem) && !(isNaNFloat(aElem) && isNaNFloat(bElem))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        ValueNode a1 = GraphUtil.unproxify(array1);
        ValueNode a2 = GraphUtil.unproxify(array2);
        if (a1 == a2) {
            return ConstantNode.forBoolean(true);
        }
        if (a1.isConstant() && a2.isConstant() && length.isConstant()) {
            ConstantNode c1 = (ConstantNode) a1;
            ConstantNode c2 = (ConstantNode) a2;
            if (c1.getStableDimension() >= 1 && c2.getStableDimension() >= 1) {
                boolean ret = arrayEquals(tool.getConstantReflection(), c1.asJavaConstant(), c2.asJavaConstant(), length.asJavaConstant().asInt());
                return ConstantNode.forBoolean(ret);
            }
        }
        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias1 = tool.getAlias(array1);
        ValueNode alias2 = tool.getAlias(array2);
        if (alias1 == alias2) {
            // the same virtual objects will always have the same contents
            tool.replaceWithValue(ConstantNode.forBoolean(true, graph()));
        } else if (alias1 instanceof VirtualObjectNode && alias2 instanceof VirtualObjectNode) {
            VirtualObjectNode virtual1 = (VirtualObjectNode) alias1;
            VirtualObjectNode virtual2 = (VirtualObjectNode) alias2;

            if (virtual1.entryCount() == virtual2.entryCount()) {
                int entryCount = virtual1.entryCount();
                boolean allEqual = true;
                for (int i = 0; i < entryCount; i++) {
                    ValueNode entry1 = tool.getEntry(virtual1, i);
                    ValueNode entry2 = tool.getEntry(virtual2, i);
                    if (entry1 != entry2) {
                        if (entry1 instanceof ConstantNode && entry2 instanceof ConstantNode) {
                            // Float NaN constants are different constant nodes but treated as
                            // equal in Arrays.equals([F[F) or Arrays.equals([D[D).
                            if (entry1.getStackKind() == JavaKind.Float && entry2.getStackKind() == JavaKind.Float) {
                                float value1 = ((JavaConstant) ((ConstantNode) entry1).asConstant()).asFloat();
                                float value2 = ((JavaConstant) ((ConstantNode) entry2).asConstant()).asFloat();
                                if (Float.floatToIntBits(value1) != Float.floatToIntBits(value2)) {
                                    allEqual = false;
                                }
                            } else if (entry1.getStackKind() == JavaKind.Double && entry2.getStackKind() == JavaKind.Double) {
                                double value1 = ((JavaConstant) ((ConstantNode) entry1).asConstant()).asDouble();
                                double value2 = ((JavaConstant) ((ConstantNode) entry2).asConstant()).asDouble();
                                if (Double.doubleToLongBits(value1) != Double.doubleToLongBits(value2)) {
                                    allEqual = false;
                                }
                            } else {
                                allEqual = false;
                            }
                        } else {
                            // the contents might be different
                            allEqual = false;
                        }
                    }
                    if (entry1.stamp(NodeView.DEFAULT).alwaysDistinct(entry2.stamp(NodeView.DEFAULT))) {
                        // the contents are different
                        tool.replaceWithValue(ConstantNode.forBoolean(false, graph()));
                        return;
                    }
                }
                if (allEqual) {
                    tool.replaceWithValue(ConstantNode.forBoolean(true, graph()));
                }
            }
        }
    }

    @NodeIntrinsic
    public static native boolean equals(Object array1, Object array2, int length, @ConstantNodeParameter JavaKind kind);

    public static boolean equals(boolean[] array1, boolean[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Boolean);
    }

    public static boolean equals(byte[] array1, byte[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Byte);
    }

    public static boolean equals(char[] array1, char[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Char);
    }

    public static boolean equals(short[] array1, short[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Short);
    }

    public static boolean equals(int[] array1, int[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Int);
    }

    public static boolean equals(long[] array1, long[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Long);
    }

    public static boolean equals(float[] array1, float[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Float);
    }

    public static boolean equals(double[] array1, double[] array2, int length) {
        return equals(array1, array2, length, JavaKind.Double);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().emitArrayEquals(kind, gen.operand(array1), gen.operand(array2), gen.operand(length));
        gen.setResult(this, result);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(kind);
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
}
