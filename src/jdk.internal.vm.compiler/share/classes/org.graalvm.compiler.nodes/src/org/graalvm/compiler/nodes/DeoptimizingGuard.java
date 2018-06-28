/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.extended.GuardingNode;

/**
 * Shared interface to capture core methods of {@link AbstractFixedGuardNode} and {@link GuardNode}.
 *
 */
public interface DeoptimizingGuard extends GuardingNode, StaticDeoptimizingNode {

    LogicNode getCondition();

    void setCondition(LogicNode x, boolean negated);

    boolean isNegated();

    NodeSourcePosition getNoDeoptSuccessorPosition();

    void setNoDeoptSuccessorPosition(NodeSourcePosition noDeoptSuccessorPosition);

    default void addCallerToNoDeoptSuccessorPosition(NodeSourcePosition caller) {
        NodeSourcePosition noDeoptSuccessorPosition = getNoDeoptSuccessorPosition();
        if (noDeoptSuccessorPosition == null) {
            return;
        }
        setNoDeoptSuccessorPosition(new NodeSourcePosition(caller, noDeoptSuccessorPosition.getMethod(), noDeoptSuccessorPosition.getBCI()));
    }
}
