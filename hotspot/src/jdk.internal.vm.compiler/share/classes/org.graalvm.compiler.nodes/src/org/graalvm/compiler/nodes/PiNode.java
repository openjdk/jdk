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
package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.ValueProxy;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

//JaCoCo Exclude

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A node that changes the type of its input, usually narrowing it. For example, a {@link PiNode}
 * refines the type of a receiver during type-guarded inlining to be the type tested by the guard.
 *
 * In contrast to a {@link GuardedValueNode}, a {@link PiNode} is useless as soon as the type of its
 * input is as narrow or narrower than the {@link PiNode}'s type. The {@link PiNode}, and therefore
 * also the scheduling restriction enforced by the anchor, will go away.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public class PiNode extends FloatingGuardedNode implements LIRLowerable, Virtualizable, IterableNodeType, Canonicalizable, ValueProxy {

    public static final NodeClass<PiNode> TYPE = NodeClass.create(PiNode.class);
    @Input ValueNode object;
    protected final Stamp piStamp;

    public ValueNode object() {
        return object;
    }

    protected PiNode(NodeClass<? extends PiNode> c, ValueNode object, Stamp stamp, GuardingNode guard) {
        super(c, stamp, guard);
        assert stamp != StampFactory.forNodeIntrinsic();
        this.object = object;
        this.piStamp = stamp;
        assert piStamp.isCompatible(object.stamp()) : "Object stamp not compatible to piStamp";
        inferStamp();
    }

    public PiNode(ValueNode object, Stamp stamp) {
        this(object, stamp, null);
    }

    public PiNode(ValueNode object, Stamp stamp, ValueNode anchor) {
        this(TYPE, object, stamp, (GuardingNode) anchor);
    }

    public PiNode(ValueNode object, ValueNode anchor) {
        this(object, AbstractPointerStamp.pointerNonNull(object.stamp()), anchor);
    }

    public PiNode(ValueNode object, ResolvedJavaType toType, boolean exactType, boolean nonNull) {
        this(object, StampFactory.object(exactType ? TypeReference.createExactTrusted(toType) : TypeReference.createWithoutAssumptions(toType), nonNull || StampTool.isPointerNonNull(object.stamp())));
    }

    public final Stamp piStamp() {
        return piStamp;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        if (generator.hasOperand(object)) {
            generator.setResult(this, generator.operand(object));
        }
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(computeStamp());
    }

    private Stamp computeStamp() {
        return piStamp.improveWith(object().stamp());
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object());
        if (alias instanceof VirtualObjectNode) {
            VirtualObjectNode virtual = (VirtualObjectNode) alias;
            if (StampTool.typeOrNull(this) != null && StampTool.typeOrNull(this).isAssignableFrom(virtual.type())) {
                tool.replaceWithVirtual(virtual);
            }
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        // Use most up to date stamp.
        Stamp computedStamp = computeStamp();

        ValueNode o = object();

        // The pi node does not give any additional information => skip it.
        if (computedStamp.equals(o.stamp())) {
            return o;
        }

        GuardingNode g = getGuard();
        if (g == null) {

            // Try to merge the pi node with a load node.
            if (o instanceof ReadNode) {
                ReadNode readNode = (ReadNode) o;
                readNode.setStamp(readNode.stamp().improveWith(this.piStamp));
                return readNode;
            }
        } else {
            for (Node n : g.asNode().usages()) {
                if (n instanceof PiNode) {
                    PiNode otherPi = (PiNode) n;
                    if (o == otherPi.object() && computedStamp.equals(otherPi.stamp())) {
                        /*
                         * Two PiNodes with the same guard and same result, so return the one with
                         * the more precise piStamp.
                         */
                        Stamp newStamp = piStamp.join(otherPi.piStamp);
                        if (newStamp.equals(otherPi.piStamp)) {
                            return otherPi;
                        }
                    }
                }
            }
        }
        return this;
    }

    @Override
    public ValueNode getOriginalNode() {
        return object;
    }

    public void setOriginalNode(ValueNode newNode) {
        this.updateUsages(object, newNode);
        this.object = newNode;
        assert piStamp.isCompatible(object.stamp()) : "New object stamp not compatible to piStamp";
    }

    /**
     * Casts an object to have an exact, non-null stamp representing {@link Class}.
     */
    public static Class<?> asNonNullClass(Object object) {
        return asNonNullClassIntrinsic(object, Class.class, true, true);
    }

    /**
     * Casts an object to have an exact, non-null stamp representing {@link Class}.
     */
    public static Class<?> asNonNullObject(Object object) {
        return asNonNullClassIntrinsic(object, Object.class, false, true);
    }

    @NodeIntrinsic(PiNode.class)
    private static native Class<?> asNonNullClassIntrinsic(Object object, @ConstantNodeParameter Class<?> toType, @ConstantNodeParameter boolean exactType, @ConstantNodeParameter boolean nonNull);

    /**
     * Changes the stamp of an object inside a snippet to be the stamp of the node replaced by the
     * snippet.
     */
    @NodeIntrinsic(PiNode.Placeholder.class)
    public static native Object piCastToSnippetReplaceeStamp(Object object);

    /**
     * Changes the stamp of an object and ensures the newly stamped value is non-null and does not
     * float above a given anchor.
     */
    @NodeIntrinsic
    public static native Object piCastNonNull(Object object, GuardingNode anchor);

    /**
     * Changes the stamp of an object and ensures the newly stamped value is non-null and does not
     * float above a given anchor.
     */
    @NodeIntrinsic
    public static native Class<?> piCastNonNullClass(Class<?> type, GuardingNode anchor);

    /**
     * Changes the stamp of an object to represent a given type and to indicate that the object is
     * not null.
     */
    public static Object piCastNonNull(Object object, @ConstantNodeParameter Class<?> toType) {
        return piCast(object, toType, false, true);
    }

    @NodeIntrinsic
    public static native Object piCast(Object object, @ConstantNodeParameter Class<?> toType, @ConstantNodeParameter boolean exactType, @ConstantNodeParameter boolean nonNull);

    /**
     * A placeholder node in a snippet that will be replaced with an appropriate {@link PiNode} when
     * the snippet is instantiated. Using a placeholder means that {@link PiNode} never needs to
     * deal with {@link StampFactory#forNodeIntrinsic()} stamps.
     */
    @NodeInfo(cycles = CYCLES_0, size = SIZE_0)
    public static class Placeholder extends FloatingGuardedNode {

        public static final NodeClass<Placeholder> TYPE = NodeClass.create(Placeholder.class);
        @Input ValueNode object;

        public ValueNode object() {
            return object;
        }

        protected Placeholder(NodeClass<? extends Placeholder> c, ValueNode object) {
            super(c, StampFactory.forNodeIntrinsic(), null);
            this.object = object;
        }

        public Placeholder(ValueNode object) {
            this(TYPE, object);
        }

        /**
         * Gets a new {@link PiNode} that replaces this placeholder during snippet instantiation.
         *
         * @param snippetReplaceeStamp the stamp of the node being replace by the snippet
         */
        public PiNode getReplacement(Stamp snippetReplaceeStamp) {
            return graph().addOrUnique(new PiNode(object(), snippetReplaceeStamp, null));
        }
    }
}
