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

import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation for a labeled statement.
 *
 */

public class LabelNode extends Node {
    /** Label ident. */
    private IdentNode label;

    /** Statements. */
    private Block body;

    /** Node to break from. */
    @Ignore
    private Node breakNode;

    /** Node to continue. */
    @Ignore
    private Node continueNode;

    /**
     * Constructor
     *
     * @param source the source
     * @param token  token
     * @param finish finish
     * @param label  label identifier
     * @param body   body of label node
     */
    public LabelNode(final Source source, final long token, final int finish, final IdentNode label, final Block body) {
        super(source, token, finish);

        this.label = label;
        this.body  = body;
    }

    private LabelNode(final LabelNode labelNode, final CopyState cs) {
        super(labelNode);

        this.label        = (IdentNode)cs.existingOrCopy(labelNode.label);
        this.body         = (Block)cs.existingOrCopy(labelNode.body);
        this.breakNode    = cs.existingOrSame(labelNode.breakNode);
        this.continueNode = cs.existingOrSame(labelNode.continueNode);
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new LabelNode(this, cs);
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enter(this) != null) {
            label = (IdentNode)label.accept(visitor);
            body  = (Block)body.accept(visitor);
            return visitor.leave(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        label.toString(sb);
        sb.append(':');
    }

    /**
     * Get the body of the node
     * @return the body
     */
    public Block getBody() {
        return body;
    }

    /**
     * Reset the body of the node
     * @param body new body
     */
    public void setBody(final Block body) {
        this.body = body;
    }

    /**
     * Get the break node for this node
     * @return the break node
     */
    public Node getBreakNode() {
        return breakNode;
    }

    /**
     * Reset the break node for this node
     * @param breakNode the break node
     */
    public void setBreakNode(final Node breakNode) {
        assert breakNode instanceof BreakableNode || breakNode instanceof Block : "Invalid break node: " + breakNode;
        this.breakNode = breakNode;
    }

    /**
     * Get the continue node for this node
     * @return the continue node
     */
    public Node getContinueNode() {
        return continueNode;
    }

    /**
     * Reset the continue node for this node
     * @param continueNode the continue node
     */
    public void setContinueNode(final Node continueNode) {
        assert continueNode instanceof WhileNode : "invalid continue node: " + continueNode;
        this.continueNode = continueNode;
    }

    /**
     * Get the identifier representing the label name
     * @return the label
     */
    public IdentNode getLabel() {
        return label;
    }

}
