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



package org.graalvm.compiler.core.amd64;

import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.phases.common.AddressLoweringPhase.AddressLowering;

import jdk.vm.ci.meta.JavaConstant;

public class AMD64AddressLowering extends AddressLowering {
    private static final int ADDRESS_BITS = 64;

    @Override
    public AddressNode lower(ValueNode base, ValueNode offset) {
        AMD64AddressNode ret = new AMD64AddressNode(base, offset);
        StructuredGraph graph = base.graph();

        boolean changed;
        do {
            changed = improve(graph, base.getDebug(), ret, false, false);
        } while (changed);

        assert checkAddressBitWidth(ret.getBase());
        assert checkAddressBitWidth(ret.getIndex());
        return graph.unique(ret);
    }

    private static boolean checkAddressBitWidth(ValueNode value) {
        return value == null || value.stamp(NodeView.DEFAULT) instanceof AbstractPointerStamp || IntegerStamp.getBits(value.stamp(NodeView.DEFAULT)) == ADDRESS_BITS;
    }

    /**
     * Tries to optimize addresses so that they match the AMD64-specific addressing mode better
     * (base + index * scale + displacement).
     *
     * @param graph the current graph
     * @param debug the current debug context
     * @param ret the address that should be optimized
     * @param isBaseNegated determines if the address base is negated. if so, all values that are
     *            extracted from the base will be negated as well
     * @param isIndexNegated determines if the index is negated. if so, all values that are
     *            extracted from the index will be negated as well
     * @return true if the address was modified
     */
    protected boolean improve(StructuredGraph graph, DebugContext debug, AMD64AddressNode ret, boolean isBaseNegated, boolean isIndexNegated) {
        ValueNode newBase = improveInput(ret, ret.getBase(), 0, isBaseNegated);
        if (newBase != ret.getBase()) {
            ret.setBase(newBase);
            return true;
        }

        ValueNode newIdx = improveInput(ret, ret.getIndex(), ret.getScale().log2, isIndexNegated);
        if (newIdx != ret.getIndex()) {
            ret.setIndex(newIdx);
            return true;
        }

        if (ret.getIndex() instanceof LeftShiftNode) {
            LeftShiftNode shift = (LeftShiftNode) ret.getIndex();
            if (shift.getY().isConstant()) {
                int amount = ret.getScale().log2 + shift.getY().asJavaConstant().asInt();
                Scale scale = Scale.fromShift(amount);
                if (scale != null) {
                    ret.setIndex(shift.getX());
                    ret.setScale(scale);
                    return true;
                }
            }
        }

        if (ret.getScale() == Scale.Times1) {
            if (ret.getIndex() == null && ret.getBase() instanceof AddNode) {
                AddNode add = (AddNode) ret.getBase();
                ret.setBase(add.getX());
                ret.setIndex(considerNegation(graph, add.getY(), isBaseNegated));
                return true;
            }

            if (ret.getBase() == null && ret.getIndex() instanceof AddNode) {
                AddNode add = (AddNode) ret.getIndex();
                ret.setBase(considerNegation(graph, add.getX(), isIndexNegated));
                ret.setIndex(add.getY());
                return true;
            }

            if (ret.getBase() instanceof LeftShiftNode && !(ret.getIndex() instanceof LeftShiftNode)) {
                ValueNode tmp = ret.getBase();
                ret.setBase(considerNegation(graph, ret.getIndex(), isIndexNegated != isBaseNegated));
                ret.setIndex(considerNegation(graph, tmp, isIndexNegated != isBaseNegated));
                return true;
            }
        }

        return improveNegation(graph, debug, ret, isBaseNegated, isIndexNegated);
    }

    private boolean improveNegation(StructuredGraph graph, DebugContext debug, AMD64AddressNode ret, boolean originalBaseNegated, boolean originalIndexNegated) {
        boolean baseNegated = originalBaseNegated;
        boolean indexNegated = originalIndexNegated;

        ValueNode originalBase = ret.getBase();
        ValueNode originalIndex = ret.getIndex();

        if (ret.getBase() instanceof NegateNode) {
            NegateNode negate = (NegateNode) ret.getBase();
            ret.setBase(negate.getValue());
            baseNegated = !baseNegated;
        }

        if (ret.getIndex() instanceof NegateNode) {
            NegateNode negate = (NegateNode) ret.getIndex();
            ret.setIndex(negate.getValue());
            indexNegated = !indexNegated;
        }

        if (baseNegated != originalBaseNegated || indexNegated != originalIndexNegated) {
            ValueNode base = ret.getBase();
            ValueNode index = ret.getIndex();

            boolean improved = improve(graph, debug, ret, baseNegated, indexNegated);
            if (baseNegated != originalBaseNegated) {
                if (base == ret.getBase()) {
                    ret.setBase(originalBase);
                } else if (ret.getBase() != null) {
                    ret.setBase(graph.maybeAddOrUnique(NegateNode.create(ret.getBase(), NodeView.DEFAULT)));
                }
            }

            if (indexNegated != originalIndexNegated) {
                if (index == ret.getIndex()) {
                    ret.setIndex(originalIndex);
                } else if (ret.getIndex() != null) {
                    ret.setIndex(graph.maybeAddOrUnique(NegateNode.create(ret.getIndex(), NodeView.DEFAULT)));
                }
            }
            return improved;
        } else {
            assert ret.getBase() == originalBase && ret.getIndex() == originalIndex;
        }
        return false;
    }

    private static ValueNode considerNegation(StructuredGraph graph, ValueNode value, boolean negate) {
        if (negate && value != null) {
            return graph.maybeAddOrUnique(NegateNode.create(value, NodeView.DEFAULT));
        }
        return value;
    }

    private static ValueNode improveInput(AMD64AddressNode address, ValueNode node, int shift, boolean negateExtractedDisplacement) {
        if (node == null) {
            return null;
        }

        JavaConstant c = node.asJavaConstant();
        if (c != null) {
            return improveConstDisp(address, node, c, null, shift, negateExtractedDisplacement);
        } else {
            if (node.stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                assert IntegerStamp.getBits(node.stamp(NodeView.DEFAULT)) == ADDRESS_BITS;

                /*
                 * we can't swallow zero-extends because of multiple reasons:
                 *
                 * a) we might encounter something like the following: ZeroExtend(Add(negativeValue,
                 * positiveValue)). if we swallow the zero-extend in this case and subsequently
                 * optimize the add, we might end up with a negative value that has less than 64
                 * bits in base or index. such a value would require sign extension instead of
                 * zero-extension but the backend can only do (implicit) zero-extension by using a
                 * larger register (e.g., rax instead of eax).
                 *
                 * b) our backend does not guarantee that the upper half of a 64-bit register equals
                 * 0 if a 32-bit value is stored in there.
                 *
                 * c) we also can't swallow zero-extends with less than 32 bits as most of these
                 * values are immediately sign-extended to 32 bit by the backend (therefore, the
                 * subsequent implicit zero-extension to 64 bit won't do what we expect).
                 */

                if (node instanceof AddNode) {
                    AddNode add = (AddNode) node;
                    if (add.getX().isConstant()) {
                        return improveConstDisp(address, node, add.getX().asJavaConstant(), add.getY(), shift, negateExtractedDisplacement);
                    } else if (add.getY().isConstant()) {
                        return improveConstDisp(address, node, add.getY().asJavaConstant(), add.getX(), shift, negateExtractedDisplacement);
                    }
                }
            }
        }

        return node;
    }

    private static ValueNode improveConstDisp(AMD64AddressNode address, ValueNode original, JavaConstant c, ValueNode other, int shift, boolean negateExtractedDisplacement) {
        if (c.getJavaKind().isNumericInteger()) {
            long delta = c.asLong() << shift;
            if (updateDisplacement(address, delta, negateExtractedDisplacement)) {
                return other;
            }
        }
        return original;
    }

    protected static boolean updateDisplacement(AMD64AddressNode address, long displacementDelta, boolean negateDelta) {
        long sign = negateDelta ? -1 : 1;
        long disp = address.getDisplacement() + displacementDelta * sign;
        if (NumUtil.isInt(disp)) {
            address.setDisplacement((int) disp);
            return true;
        }
        return false;
    }
}
