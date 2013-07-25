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

import static jdk.nashorn.internal.codegen.CompilerConstants.__DIR__;
import static jdk.nashorn.internal.codegen.CompilerConstants.__FILE__;
import static jdk.nashorn.internal.codegen.CompilerConstants.__LINE__;
import static jdk.nashorn.internal.codegen.ObjectClassGenerator.DEBUG_FIELDS;

import jdk.nashorn.internal.codegen.ObjectClassGenerator;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * IR representation for an identifier.
 */
@Immutable
public final class IdentNode extends Expression implements PropertyKey, TypeOverride<IdentNode>, FunctionCall {
    private static final int PROPERTY_NAME    = 1 << 0;
    private static final int INITIALIZED_HERE = 1 << 1;
    private static final int FUNCTION         = 1 << 2;

    /** Identifier. */
    private final String name;

    /** Type for a callsite, e.g. X in a get()X or a set(X)V */
    private final Type callSiteType;

    private final int flags;

    /**
     * Constructor
     *
     * @param token   token
     * @param finish  finish position
     * @param name    name of identifier
     */
    public IdentNode(final long token, final int finish, final String name) {
        super(token, finish);
        this.name = name.intern();
        this.callSiteType = null;
        this.flags = 0;
    }

    private IdentNode(final IdentNode identNode, final String name, final Type callSiteType, final int flags) {
        super(identNode);
        this.name = name;
        this.callSiteType = callSiteType;
        this.flags = flags;
    }

    /**
     * Copy constructor - create a new IdentNode for the same location
     *
     * @param identNode  identNode
     */
    public IdentNode(final IdentNode identNode) {
        super(identNode);
        this.name         = identNode.getName();
        this.callSiteType = null;
        this.flags        = identNode.flags;
    }

    @Override
    public Type getType() {
        return callSiteType == null ? super.getType() : callSiteType;
    }

    @Override
    public boolean isAtom() {
        return true;
    }

    private boolean hasCallSiteType() {
        //this is an identity that's part of a getter or setter
        return callSiteType != null;
    }

    @Override
    public IdentNode setType(final TemporarySymbols ts, final LexicalContext lc, final Type type) {
        // do NOT, repeat NOT touch the symbol here. it might be a local variable or whatever. This is the override if it isn't
        if (this.callSiteType == type) {
            return this;
        }
        if (DEBUG_FIELDS && getSymbol() != null && !Type.areEquivalent(getSymbol().getSymbolType(), type)) {
            ObjectClassGenerator.LOG.info(getClass().getName(), " ", this, " => ", type, " instead of ", getType());
        }

        return new IdentNode(this, name, type, flags);
    }

    /**
     * Assist in IR navigation.
     *
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterIdentNode(this)) {
            return visitor.leaveIdentNode(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        if (hasCallSiteType()) {
            sb.append('{');
            final String desc = getType().getDescriptor();
            sb.append(desc.charAt(desc.length() - 1) == ';' ? 'O' : getType().getDescriptor());
            sb.append('}');
        }

        sb.append(name);
    }

    /**
     * Get the name of the identifier
     * @return  IdentNode name
     */
    public String getName() {
        return name;
    }

    @Override
    public String getPropertyName() {
        return getName();
    }

    /**
     * We can only override type if the symbol lives in the scope, otherwise
     * it is strongly determined by the local variable already allocated
     *
     * @return true if can have callsite type
     */
    @Override
    public boolean canHaveCallSiteType() {
        return getSymbol() != null && getSymbol().isScope();
    }

    /**
     * Check if this IdentNode is a property name
     * @return true if this is a property name
     */
    public boolean isPropertyName() {
        return (flags & PROPERTY_NAME) != 0;
    }

    /**
     * Flag this IdentNode as a property name
     * @return a node equivalent to this one except for the requested change.
     */
    public IdentNode setIsPropertyName() {
        if (isPropertyName()) {
            return this;
        }
        return new IdentNode(this, name, callSiteType, flags | PROPERTY_NAME);
    }

    /**
     * Helper function for local def analysis.
     * @return true if IdentNode is initialized on creation
     */
    public boolean isInitializedHere() {
        return (flags & INITIALIZED_HERE) != 0;
    }

    /**
     * Flag IdentNode to be initialized on creation
     * @return a node equivalent to this one except for the requested change.
     */
    public IdentNode setIsInitializedHere() {
        if (isInitializedHere()) {
            return this;
        }
        return new IdentNode(this, name, callSiteType, flags | INITIALIZED_HERE);
    }

    /**
     * Check if this IdentNode is a special identity, currently __DIR__, __FILE__
     * or __LINE__
     *
     * @return true if this IdentNode is special
     */
    public boolean isSpecialIdentity() {
        return name.equals(__DIR__.symbolName()) || name.equals(__FILE__.symbolName()) || name.equals(__LINE__.symbolName());
    }

    @Override
    public boolean isFunction() {
        return (flags & FUNCTION) != 0;
    }

    /**
     * Mark this node as being the callee operand of a {@link CallNode}.
     * @return an ident node identical to this one in all aspects except with its function flag set.
     */
    public IdentNode setIsFunction() {
        if (isFunction()) {
            return this;
        }
        return new IdentNode(this, name, callSiteType, flags | FUNCTION);
    }
}
