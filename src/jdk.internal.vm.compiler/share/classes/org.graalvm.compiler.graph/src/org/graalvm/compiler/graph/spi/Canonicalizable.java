/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.graph.spi;

import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;

import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Nodes can implement {@link Canonicalizable} or one of the two sub-interfaces {@link Unary} and
 * {@link Binary} to provide local optimizations like constant folding and strength reduction.
 * Implementations should return a replacement that is always semantically correct for the given
 * inputs, or "this" if they do not see an opportunity for improvement.<br/>
 * <br/>
 * <b>Implementations of {@link Canonicalizable#canonical(CanonicalizerTool)} or the equivalent
 * methods of the two sub-interfaces must not have any side effects.</b><br/>
 * They are not allowed to change inputs, successors or properties of any node (including the
 * current one) and they also cannot add new nodes to the graph.<br/>
 * <br/>
 * In addition to pre-existing nodes they can return newly created nodes, which will be added to the
 * graph automatically if (and only if) the effects of the canonicalization are committed.
 * Non-cyclic graphs (DAGs) of newly created nodes (i.e., one newly created node with an input to
 * another newly created node) will be handled correctly.
 */
public interface Canonicalizable {

    /**
     * Implementations of this method can provide local optimizations like constant folding and
     * strength reduction. Implementations should look at the properties and inputs of the current
     * node and determine if there is a more optimal and always semantically correct replacement.
     * <br/>
     * The return value determines the effect that the canonicalization will have:
     * <ul>
     * <li>Returning an pre-existing node will replace the current node with the given one.</li>
     * <li>Returning a newly created node (that was not yet added to the graph) will replace the
     * current node with the given one, after adding it to the graph. If both the replacement and
     * the replacee are anchored in control flow (fixed nodes), the replacement will be added to the
     * control flow. It is invalid to replace a non-fixed node with a newly created fixed node
     * (because its placement in the control flow cannot be determined without scheduling).</li>
     * <li>Returning {@code null} will delete the current node and replace it with {@code null} at
     * all usages. Note that it is not necessary to delete floating nodes that have no more usages
     * this way - they will be deleted automatically.</li>
     * </ul>
     *
     * @param tool provides access to runtime interfaces like {@link MetaAccessProvider}
     */
    Node canonical(CanonicalizerTool tool);

    /**
     * This sub-interface of {@link Canonicalizable} is intended for nodes that have exactly one
     * input. It has an additional {@link #canonical(CanonicalizerTool, Node)} method that looks at
     * the given input instead of the current input of the node - which can be used to ask "what if
     * this input is changed to this node" - questions.
     *
     * @param <T> the common supertype of all inputs of this node
     */
    public interface Unary<T extends Node> extends Canonicalizable {

        /**
         * Similar to {@link Canonicalizable#canonical(CanonicalizerTool)}, except that
         * implementations should act as if the current input of the node was the given one, i.e.,
         * they should never look at the inputs via the this pointer.
         */
        Node canonical(CanonicalizerTool tool, T forValue);

        /**
         * Gets the current value of the input, so that calling
         * {@link #canonical(CanonicalizerTool, Node)} with the value returned from this method
         * should behave exactly like {@link Canonicalizable#canonical(CanonicalizerTool)}.
         */
        T getValue();

        @SuppressWarnings("unchecked")
        @Override
        default T canonical(CanonicalizerTool tool) {
            return (T) canonical(tool, getValue());
        }
    }

    /**
     * This sub-interface of {@link Canonicalizable} is intended for nodes that have exactly two
     * inputs. It has an additional {@link #canonical(CanonicalizerTool, Node, Node)} method that
     * looks at the given inputs instead of the current inputs of the node - which can be used to
     * ask "what if this input is changed to this node" - questions.
     *
     * @param <T> the common supertype of all inputs of this node
     */
    public interface Binary<T extends Node> extends Canonicalizable {

        /**
         * Similar to {@link Canonicalizable#canonical(CanonicalizerTool)}, except that
         * implementations should act as if the current input of the node was the given one, i.e.,
         * they should never look at the inputs via the this pointer.
         */
        Node canonical(CanonicalizerTool tool, T forX, T forY);

        /**
         * Gets the current value of the input, so that calling
         * {@link #canonical(CanonicalizerTool, Node, Node)} with the value returned from this
         * method should behave exactly like {@link Canonicalizable#canonical(CanonicalizerTool)}.
         */
        T getX();

        /**
         * Gets the current value of the input, so that calling
         * {@link #canonical(CanonicalizerTool, Node, Node)} with the value returned from this
         * method should behave exactly like {@link Canonicalizable#canonical(CanonicalizerTool)}.
         */
        T getY();

        @SuppressWarnings("unchecked")
        @Override
        default T canonical(CanonicalizerTool tool) {
            return (T) canonical(tool, getX(), getY());
        }
    }

    /**
     * This sub-interface of {@link Canonicalizable.Binary} is for nodes with two inputs where the
     * operation is commutative. It is used to improve GVN by trying to merge nodes with the same
     * inputs in different order.
     */
    public interface BinaryCommutative<T extends Node> extends Binary<T> {

        /**
         * Ensure a canonical ordering of inputs for commutative nodes to improve GVN results. Order
         * the inputs by increasing {@link Node#id} and call {@link Graph#findDuplicate(Node)} on
         * the node if it's currently in a graph. It's assumed that if there was a constant on the
         * left it's been moved to the right by other code and that ordering is left alone.
         *
         * @return the original node or another node with the same input ordering
         */
        Node maybeCommuteInputs();
    }
}
