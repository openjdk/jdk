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

import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * Node represents a var/let declaration.
 */
public class VarNode extends Node implements Assignment<IdentNode> {
    /** Var name. */
    private IdentNode name;

    /** Initialization expression. */
    private Node init;

    /** Is this a function var node */
    private boolean isFunctionVarNode;

    /**
     * Constructor
     *
     * @param source the source
     * @param token  token
     * @param finish finish
     * @param name   name of variable
     * @param init   init node or null if just a declaration
     */
    public VarNode(final Source source, final long token, final int finish, final IdentNode name, final Node init) {
        super(source, token, finish);

        this.name  = name;
        this.init  = init;
        if (init != null) {
            this.name.setIsInitializedHere();
        }
    }

    private VarNode(final VarNode varNode, final CopyState cs) {
        super(varNode);

        this.name = (IdentNode)cs.existingOrCopy(varNode.name);
        this.init = cs.existingOrCopy(varNode.init);
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new VarNode(this, cs);
    }

    @Override
    public boolean isAssignment() {
        return hasInit();
    }

    @Override
    public IdentNode getAssignmentDest() {
        return isAssignment() ? name : null;
    }

    @Override
    public Node getAssignmentSource() {
        return isAssignment() ? getInit() : null;
    }

    /**
     * Does this variable declaration have an init value
     * @return true if an init exists, false otherwise
     */
    public boolean hasInit() {
        return init != null;
    }

    /**
     * Test to see if two VarNodes are the same.
     * @param other Other VarNode.
     * @return True if the VarNodes are the same.
     */
    @Override
    public boolean equals(final Object other) {
        if (other instanceof VarNode) {
            final VarNode otherNode    = (VarNode)other;
            final boolean nameMatches  = name.equals(otherNode.name);
            if (hasInit() != otherNode.hasInit()) {
                return false;
            } else if (init == null) {
                return nameMatches;
            } else {
                return nameMatches && init.equals(otherNode.init);
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode() ^ (init == null ? 0 : init.hashCode());
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enter(this) != null) {
            name = (IdentNode)name.accept(visitor);

            if (init != null) {
                init = init.accept(visitor);
            }

            return visitor.leave(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append("var ");
        name.toString(sb);

        if (init != null) {
            sb.append(" = ");
            init.toString(sb);
        }
    }

    /**
     * If this is an assignment of the form {@code var x = init;}, get the init part.
     * @return the expression to initialize the variable to, null if just a declaration
     */
    public Node getInit() {
        return init;
    }

    /**
     * Reset the initialization expression
     * @param init new initialization expression
     */
    public void setInit(final Node init) {
        this.init = init;
    }

    /**
     * Get the identifier for the variable
     * @return IdentNode representing the variable being set or declared
     */
    public IdentNode getName() {
        return name;
    }

    /**
     * Reset the identifier for this VarNode
     * @param name new IdentNode representing the variable being set or declared
     */
    public void setName(final IdentNode name) {
        this.name = name;
    }

    /**
     * Check if this is a virtual assignment of a function node. Function nodes declared
     * with a name are hoisted to the top of the scope and appear as symbols too. This is
     * implemented by representing them as virtual VarNode assignments added to the code
     * during lowering
     *
     * @see FunctionNode
     *
     * @return true if this is a virtual function declaration
     */
    public boolean isFunctionVarNode() {
        return isFunctionVarNode;
    }

    /**
     * Flag this var node as a virtual function var node assignment as described in
     * {@link VarNode#isFunctionVarNode()}
     */
    public void setIsFunctionVarNode() {
        this.isFunctionVarNode = true;
    }

}
