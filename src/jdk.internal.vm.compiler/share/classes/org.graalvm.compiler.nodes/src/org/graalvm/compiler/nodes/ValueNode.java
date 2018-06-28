/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes;

import java.util.function.Predicate;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.iterators.NodePredicate;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.spi.NodeValueMap;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * This class represents a value within the graph, including local variables, phis, and all other
 * instructions.
 */
@NodeInfo
public abstract class ValueNode extends org.graalvm.compiler.graph.Node implements ValueNodeInterface {

    public static final NodeClass<ValueNode> TYPE = NodeClass.create(ValueNode.class);
    /**
     * The kind of this value. This is {@link JavaKind#Void} for instructions that produce no value.
     * This kind is guaranteed to be a {@linkplain JavaKind#getStackKind() stack kind}.
     */
    protected Stamp stamp;

    public ValueNode(NodeClass<? extends ValueNode> c, Stamp stamp) {
        super(c);
        this.stamp = stamp;
    }

    public final Stamp stamp(NodeView view) {
        return view.stamp(this);
    }

    public final void setStamp(Stamp stamp) {
        this.stamp = stamp;
        assert !isAlive() || !inferStamp() : "setStamp called on a node that overrides inferStamp: " + this;
    }

    @Override
    public final StructuredGraph graph() {
        return (StructuredGraph) super.graph();
    }

    /**
     * Checks if the given stamp is different than the current one (
     * {@code newStamp.equals(oldStamp) == false}). If it is different then the new stamp will
     * become the current stamp for this node.
     *
     * @return true if the stamp has changed, false otherwise.
     */
    protected final boolean updateStamp(Stamp newStamp) {
        if (newStamp == null || newStamp.equals(stamp)) {
            return false;
        } else {
            stamp = newStamp;
            return true;
        }
    }

    /**
     * This method can be overridden by subclasses of {@link ValueNode} if they need to recompute
     * their stamp if their inputs change. A typical implementation will compute the stamp and pass
     * it to {@link #updateStamp(Stamp)}, whose return value can be used as the result of this
     * method.
     *
     * @return true if the stamp has changed, false otherwise.
     */
    public boolean inferStamp() {
        return false;
    }

    public final JavaKind getStackKind() {
        return stamp(NodeView.DEFAULT).getStackKind();
    }

    /**
     * Checks whether this value is a constant (i.e. it is of type {@link ConstantNode}.
     *
     * @return {@code true} if this value is a constant
     */
    public final boolean isConstant() {
        return this instanceof ConstantNode;
    }

    private static final NodePredicate IS_CONSTANT = new NodePredicate() {
        @Override
        public boolean apply(Node n) {
            return n instanceof ConstantNode;
        }
    };

    public static NodePredicate isConstantPredicate() {
        return IS_CONSTANT;
    }

    /**
     * Checks whether this value represents the null constant.
     *
     * @return {@code true} if this value represents the null constant
     */
    public final boolean isNullConstant() {
        JavaConstant value = asJavaConstant();
        return value != null && value.isNull();
    }

    /**
     * Convert this value to a constant if it is a constant, otherwise return null.
     *
     * @return the {@link JavaConstant} represented by this value if it is a constant; {@code null}
     *         otherwise
     */
    public final Constant asConstant() {
        if (this instanceof ConstantNode) {
            return ((ConstantNode) this).getValue();
        } else {
            return null;
        }
    }

    public final boolean isJavaConstant() {
        return isConstant() && asConstant() instanceof JavaConstant;
    }

    public final JavaConstant asJavaConstant() {
        Constant value = asConstant();
        if (value instanceof JavaConstant) {
            return (JavaConstant) value;
        } else {
            return null;
        }
    }

    @Override
    public ValueNode asNode() {
        return this;
    }

    @Override
    public boolean isAllowedUsageType(InputType type) {
        if (getStackKind() != JavaKind.Void && type == InputType.Value) {
            return true;
        } else {
            return super.isAllowedUsageType(type);
        }
    }

    /**
     * Checks if this node has usages other than the given node {@code node}.
     *
     * @param node node which is ignored when searching for usages
     * @param nodeValueMap
     * @return true if this node has other usages, false otherwise
     */
    public boolean hasUsagesOtherThan(ValueNode node, NodeValueMap nodeValueMap) {
        for (Node usage : usages()) {
            if (usage != node && usage instanceof ValueNode && nodeValueMap.hasOperand(usage)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void replaceAtUsages(Node other, Predicate<Node> filter, Node toBeDeleted) {
        super.replaceAtUsages(other, filter, toBeDeleted);
        assert checkReplaceAtUsagesInvariants(other);
    }

    private boolean checkReplaceAtUsagesInvariants(Node other) {
        assert other == null || other instanceof ValueNode;
        if (this.hasUsages() && !this.stamp(NodeView.DEFAULT).isEmpty() && !(other instanceof PhiNode) && other != null) {
            assert ((ValueNode) other).stamp(NodeView.DEFAULT).getClass() == stamp(NodeView.DEFAULT).getClass() : "stamp have to be of same class";
            boolean morePrecise = ((ValueNode) other).stamp(NodeView.DEFAULT).join(stamp(NodeView.DEFAULT)).equals(((ValueNode) other).stamp(NodeView.DEFAULT));
            assert morePrecise : "stamp can only get more precise " + toString(Verbosity.All) + " " +
                            other.toString(Verbosity.All);
        }
        return true;
    }
}
