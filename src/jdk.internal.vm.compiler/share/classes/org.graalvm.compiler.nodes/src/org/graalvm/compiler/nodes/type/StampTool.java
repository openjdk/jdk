/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.type;

import java.util.Iterator;

import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Helper class that is used to keep all stamp-related operations in one place.
 */
public class StampTool {

    public static Stamp meet(Iterable<? extends ValueNode> values) {
        Stamp stamp = meetOrNull(values, null);
        if (stamp == null) {
            return StampFactory.forVoid();
        }
        return stamp;
    }

    /**
     * Meet a collection of {@link ValueNode}s optionally excluding {@code selfValue}. If no values
     * are encountered then return {@code null}.
     */
    public static Stamp meetOrNull(Iterable<? extends ValueNode> values, ValueNode selfValue) {
        Iterator<? extends ValueNode> iterator = values.iterator();
        Stamp stamp = null;
        while (iterator.hasNext()) {
            ValueNode nextValue = iterator.next();
            if (nextValue != selfValue) {
                if (stamp == null) {
                    stamp = nextValue.stamp(NodeView.DEFAULT);
                } else {
                    stamp = stamp.meet(nextValue.stamp(NodeView.DEFAULT));
                }
            }
        }
        return stamp;
    }

    /**
     * Compute the stamp resulting from the unsigned comparison being true.
     *
     * @return null if it's can't be true or it nothing useful can be encoded.
     */
    public static Stamp unsignedCompare(Stamp stamp, Stamp stamp2) {
        IntegerStamp x = (IntegerStamp) stamp;
        IntegerStamp y = (IntegerStamp) stamp2;
        if (x.isUnrestricted() && y.isUnrestricted()) {
            // Don't know anything.
            return null;
        }
        // c <| n, where c is a constant and n is known to be positive.
        if (x.lowerBound() == x.upperBound()) {
            if (y.isPositive()) {
                if (x.lowerBound() == (1 << x.getBits()) - 1) {
                    // Constant is MAX_VALUE which must fail.
                    return null;
                }
                if (x.lowerBound() <= y.lowerBound()) {
                    // Test will fail. Return illegalStamp instead?
                    return null;
                }
                // If the test succeeds then this proves that n is at greater than c so the bounds
                // are [c+1..-n.upperBound)].
                return StampFactory.forInteger(x.getBits(), x.lowerBound() + 1, y.upperBound());
            }
            return null;
        }
        // n <| c, where c is a strictly positive constant
        if (y.lowerBound() == y.upperBound() && y.isStrictlyPositive()) {
            // The test proves that n is positive and less than c, [0..c-1]
            return StampFactory.forInteger(y.getBits(), 0, y.lowerBound() - 1);
        }
        return null;
    }

    public static Stamp stampForLeadingZeros(IntegerStamp valueStamp) {
        long mask = CodeUtil.mask(valueStamp.getBits());
        // Don't count zeros from the mask in the result.
        int adjust = Long.numberOfLeadingZeros(mask);
        assert adjust == 0 || adjust == 32;
        int min = Long.numberOfLeadingZeros(valueStamp.upMask() & mask) - adjust;
        int max = Long.numberOfLeadingZeros(valueStamp.downMask() & mask) - adjust;
        return StampFactory.forInteger(JavaKind.Int, min, max);
    }

    public static Stamp stampForTrailingZeros(IntegerStamp valueStamp) {
        long mask = CodeUtil.mask(valueStamp.getBits());
        int min = Long.numberOfTrailingZeros(valueStamp.upMask() & mask);
        int max = Long.numberOfTrailingZeros(valueStamp.downMask() & mask);
        return StampFactory.forInteger(JavaKind.Int, min, max);
    }

    /**
     * Checks whether this {@link ValueNode} represents a {@linkplain Stamp#hasValues() legal}
     * pointer value which is known to be always null.
     *
     * @param node the node to check
     * @return true if this node represents a legal object value which is known to be always null
     */
    public static boolean isPointerAlwaysNull(ValueNode node) {
        return isPointerAlwaysNull(node.stamp(NodeView.DEFAULT));
    }

    /**
     * Checks whether this {@link Stamp} represents a {@linkplain Stamp#hasValues() legal} pointer
     * stamp whose values are known to be always null.
     *
     * @param stamp the stamp to check
     * @return true if this stamp represents a legal object stamp whose values are known to be
     *         always null
     */
    public static boolean isPointerAlwaysNull(Stamp stamp) {
        if (stamp instanceof AbstractPointerStamp && stamp.hasValues()) {
            return ((AbstractPointerStamp) stamp).alwaysNull();
        }
        return false;
    }

    /**
     * Checks whether this {@link ValueNode} represents a {@linkplain Stamp#hasValues() legal}
     * pointer value which is known to never be null.
     *
     * @param node the node to check
     * @return true if this node represents a legal object value which is known to never be null
     */
    public static boolean isPointerNonNull(ValueNode node) {
        return isPointerNonNull(node.stamp(NodeView.DEFAULT));
    }

    /**
     * Checks whether this {@link Stamp} represents a {@linkplain Stamp#hasValues() legal} pointer
     * stamp whose values are known to never be null.
     *
     * @param stamp the stamp to check
     * @return true if this stamp represents a legal object stamp whose values are known to be
     *         always null
     */
    public static boolean isPointerNonNull(Stamp stamp) {
        if (stamp instanceof AbstractPointerStamp) {
            return ((AbstractPointerStamp) stamp).nonNull();
        }
        return false;
    }

    /**
     * Returns the {@linkplain ResolvedJavaType Java type} this {@linkplain ValueNode} has if it is
     * a {@linkplain Stamp#hasValues() legal} Object value.
     *
     * @param node the node to check
     * @return the Java type this value has if it is a legal Object type, null otherwise
     */
    public static TypeReference typeReferenceOrNull(ValueNode node) {
        return typeReferenceOrNull(node.stamp(NodeView.DEFAULT));
    }

    public static ResolvedJavaType typeOrNull(ValueNode node) {
        return typeOrNull(node.stamp(NodeView.DEFAULT));
    }

    public static ResolvedJavaType typeOrNull(Stamp stamp) {
        TypeReference type = typeReferenceOrNull(stamp);
        return type == null ? null : type.getType();
    }

    public static ResolvedJavaType typeOrNull(Stamp stamp, MetaAccessProvider metaAccess) {
        if (stamp instanceof AbstractObjectStamp && stamp.hasValues()) {
            AbstractObjectStamp abstractObjectStamp = (AbstractObjectStamp) stamp;
            ResolvedJavaType type = abstractObjectStamp.type();
            if (type == null) {
                return metaAccess.lookupJavaType(Object.class);
            } else {
                return type;
            }
        }
        return null;
    }

    public static ResolvedJavaType typeOrNull(ValueNode node, MetaAccessProvider metaAccess) {
        return typeOrNull(node.stamp(NodeView.DEFAULT), metaAccess);
    }

    /**
     * Returns the {@linkplain ResolvedJavaType Java type} this {@linkplain Stamp} has if it is a
     * {@linkplain Stamp#hasValues() legal} Object stamp.
     *
     * @param stamp the stamp to check
     * @return the Java type this stamp has if it is a legal Object stamp, null otherwise
     */
    public static TypeReference typeReferenceOrNull(Stamp stamp) {
        if (stamp instanceof AbstractObjectStamp && stamp.hasValues()) {
            AbstractObjectStamp abstractObjectStamp = (AbstractObjectStamp) stamp;
            if (abstractObjectStamp.isExactType()) {
                return TypeReference.createExactTrusted(abstractObjectStamp.type());
            } else {
                return TypeReference.createTrustedWithoutAssumptions(abstractObjectStamp.type());
            }
        }
        return null;
    }

    /**
     * Checks whether this {@link ValueNode} represents a {@linkplain Stamp#hasValues() legal}
     * Object value whose Java type is known exactly. If this method returns true then the
     * {@linkplain ResolvedJavaType Java type} returned by {@link #typeReferenceOrNull(ValueNode)}
     * is the concrete dynamic/runtime Java type of this value.
     *
     * @param node the node to check
     * @return true if this node represents a legal object value whose Java type is known exactly
     */
    public static boolean isExactType(ValueNode node) {
        return isExactType(node.stamp(NodeView.DEFAULT));
    }

    /**
     * Checks whether this {@link Stamp} represents a {@linkplain Stamp#hasValues() legal} Object
     * stamp whose {@linkplain ResolvedJavaType Java type} is known exactly. If this method returns
     * true then the Java type returned by {@link #typeReferenceOrNull(Stamp)} is the only concrete
     * dynamic/runtime Java type possible for values of this stamp.
     *
     * @param stamp the stamp to check
     * @return true if this node represents a legal object stamp whose Java type is known exactly
     */
    public static boolean isExactType(Stamp stamp) {
        if (stamp instanceof AbstractObjectStamp && stamp.hasValues()) {
            return ((AbstractObjectStamp) stamp).isExactType();
        }
        return false;
    }
}
