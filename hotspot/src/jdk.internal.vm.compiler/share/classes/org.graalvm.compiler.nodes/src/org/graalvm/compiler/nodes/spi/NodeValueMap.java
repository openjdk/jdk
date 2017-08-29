/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.nodes.spi;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.Value;

public interface NodeValueMap {

    /**
     * Returns the operand that has been previously initialized by
     * {@link #setResult(ValueNode, Value)} with the result of an instruction. It's a code
     * generation error to ask for the operand of ValueNode that doesn't have one yet.
     *
     * @param node A node that produces a result value.
     */
    Value operand(Node node);

    /**
     * @return {@code true} if there is an {@link Value operand} associated with the {@code node} in
     *         the current block.
     */
    boolean hasOperand(Node node);

    /**
     * Associates {@code operand} with the {@code node} in the current block.
     *
     * @return {@code operand}
     */
    Value setResult(ValueNode node, Value operand);

    /**
     * Gets the {@link ValueNode} that produced a {@code value}. If the {@code value} is not
     * associated with a {@link ValueNode} {@code null} is returned.
     *
     * This method is intended for debugging purposes only.
     */
    ValueNode valueForOperand(Value value);
}
