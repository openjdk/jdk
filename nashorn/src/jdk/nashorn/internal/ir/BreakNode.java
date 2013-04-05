/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.ir;

import jdk.nashorn.internal.codegen.Label;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation for {@code break} statements.
 */
public class BreakNode extends LabeledNode {

     /**
     * Constructor
     *
     * @param source     source code
     * @param token      token
     * @param finish     finish
     * @param labelNode  break label
     * @param targetNode node to break to
     * @param tryChain   surrounding try chain
     */
    public BreakNode(final Source source, final long token, final int finish, final LabelNode labelNode, final Node targetNode, final TryNode tryChain) {
        super(source, token, finish, labelNode, targetNode, tryChain);
        setHasGoto();
    }

    private BreakNode(final BreakNode breakNode, final CopyState cs) {
        super(breakNode, cs);
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new BreakNode(this, cs);
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enterBreakNode(this) != null) {
            return visitor.leaveBreakNode(this);
        }

        return this;
    }

    /**
     * Return the target label of this break node.
     * @return the target label.
     */
    public Label getTargetLabel() {
        if (targetNode instanceof BreakableNode) {
            return ((BreakableNode)targetNode).getBreakLabel();
        } else if (targetNode instanceof Block) {
            return ((Block)targetNode).getBreakLabel();
        }

        throw new AssertionError("Invalid break target " + targetNode.getClass());
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append("break");

        if (labelNode != null) {
            sb.append(' ');
            labelNode.getLabel().toString(sb);
        }
    }
}
