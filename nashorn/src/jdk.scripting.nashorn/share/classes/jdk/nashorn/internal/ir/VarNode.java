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

import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Token;

/**
 * Node represents a var/let declaration.
 */
@Immutable
public final class VarNode extends Statement implements Assignment<IdentNode> {
    private static final long serialVersionUID = 1L;

    /** Var name. */
    private final IdentNode name;

    /** Initialization expression. */
    private final Expression init;

    /** Is this a var statement (as opposed to a "var" in a for loop statement) */
    private final int flags;

    /** Flag for ES6 LET declaration */
    public static final int IS_LET                       = 1 << 0;

    /** Flag for ES6 CONST declaration */
    public static final int IS_CONST                     = 1 << 1;

    /** Flag that determines if this is the last function declaration in a function
     *  This is used to micro optimize the placement of return value assignments for
     *  a program node */
    public static final int IS_LAST_FUNCTION_DECLARATION = 1 << 2;

    /**
     * Constructor
     *
     * @param lineNumber line number
     * @param token      token
     * @param finish     finish
     * @param name       name of variable
     * @param init       init node or null if just a declaration
     */
    public VarNode(final int lineNumber, final long token, final int finish, final IdentNode name, final Expression init) {
        this(lineNumber, token, finish, name, init, 0);
    }

    private VarNode(final VarNode varNode, final IdentNode name, final Expression init, final int flags) {
        super(varNode);
        this.name = init == null ? name : name.setIsInitializedHere();
        this.init = init;
        this.flags = flags;
    }

    /**
     * Constructor
     *
     * @param lineNumber line number
     * @param token      token
     * @param finish     finish
     * @param name       name of variable
     * @param init       init node or null if just a declaration
     * @param flags      flags
     */
    public VarNode(final int lineNumber, final long token, final int finish, final IdentNode name, final Expression init, final int flags) {
        super(lineNumber, token, finish);

        this.name  = init == null ? name : name.setIsInitializedHere();
        this.init  = init;
        this.flags = flags;
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
    public VarNode setAssignmentDest(final IdentNode n) {
        return setName(n);
    }

    @Override
    public Expression getAssignmentSource() {
        return isAssignment() ? getInit() : null;
    }

    /**
     * Is this a VAR node block scoped? This returns true for ECMAScript 6 LET and CONST nodes.
     * @return true if an ES6 LET or CONST node
     */
    public boolean isBlockScoped() {
        return getFlag(IS_LET) || getFlag(IS_CONST);
    }

    /**
     * Is this an ECMAScript 6 LET node?
     * @return true if LET node
     */
    public boolean isLet() {
        return getFlag(IS_LET);
    }

    /**
     * Is this an ECMAScript 6 CONST node?
     * @return true if CONST node
     */
    public boolean isConst() {
        return getFlag(IS_CONST);
    }

    /**
     * Return the flags to use for symbols for this declaration.
     * @return the symbol flags
     */
    public int getSymbolFlags() {
        if (isLet()) {
            return Symbol.IS_VAR | Symbol.IS_LET;
        } else if (isConst()) {
            return Symbol.IS_VAR | Symbol.IS_CONST;
        }
        return Symbol.IS_VAR;
    }

    /**
     * Does this variable declaration have an init value
     * @return true if an init exists, false otherwise
     */
    public boolean hasInit() {
        return init != null;
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterVarNode(this)) {
            // var is right associative, so visit init before name
            final Expression newInit = init == null ? null : (Expression)init.accept(visitor);
            final IdentNode  newName = (IdentNode)name.accept(visitor);
            final VarNode    newThis;
            if (name != newName || init != newInit) {
                newThis = new VarNode(this, newName, newInit, flags);
            } else {
                newThis = this;
            }
            return visitor.leaveVarNode(newThis);
        }
        return this;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        sb.append(Token.descType(getToken()).getName()).append(' ');
        name.toString(sb, printType);

        if (init != null) {
            sb.append(" = ");
            init.toString(sb, printType);
        }
    }

    /**
     * If this is an assignment of the form {@code var x = init;}, get the init part.
     * @return the expression to initialize the variable to, null if just a declaration
     */
    public Expression getInit() {
        return init;
    }

    /**
     * Reset the initialization expression
     * @param init new initialization expression
     * @return a node equivalent to this one except for the requested change.
     */
    public VarNode setInit(final Expression init) {
        if (this.init == init) {
            return this;
        }
        return new VarNode(this, name, init, flags);
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
     * @return a node equivalent to this one except for the requested change.
     */
    public VarNode setName(final IdentNode name) {
        if (this.name == name) {
            return this;
        }
        return new VarNode(this, name, init, flags);
    }

    private VarNode setFlags(final int flags) {
        if (this.flags == flags) {
            return this;
        }
        return new VarNode(this, name, init, flags);
    }

    /**
     * Check if a flag is set for this var node
     * @param flag flag
     * @return true if flag is set
     */
    public boolean getFlag(final int flag) {
        return (flags & flag) == flag;
    }

    /**
     * Set a flag for this var node
     * @param flag flag
     * @return new node if flags changed, same otherwise
     */
    public VarNode setFlag(final int flag) {
        return setFlags(flags | flag);
    }

    /**
     * Returns true if this is a function declaration.
     * @return true if this is a function declaration.
     */
    public boolean isFunctionDeclaration() {
        return init instanceof FunctionNode && ((FunctionNode)init).isDeclared();
    }

    /**
     * Returns true if this is an anonymous function declaration.
     * @return true if this is an anonymous function declaration.
     */
    public boolean isAnonymousFunctionDeclaration() {
        return isFunctionDeclaration() && ((FunctionNode)init).isAnonymous();
    }
}
