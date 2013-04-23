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
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation for an identifier.
 */
public class IdentNode extends Node implements PropertyKey, TypeOverride<IdentNode>, FunctionCall {
    private static final int PROPERTY_NAME    = 1 << 0;
    private static final int INITIALIZED_HERE = 1 << 1;
    private static final int FUNCTION         = 1 << 2;

    /** Identifier. */
    private final String name;

    /** Type for a callsite, e.g. X in a get()X or a set(X)V */
    private Type callSiteType;

    private byte flags;

    /**
     * Constructor
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish position
     * @param name    name of identifier
     */
    public IdentNode(final Source source, final long token, final int finish, final String name) {
        super(source, token, finish);
        this.name = name;
    }

    /**
     * Copy constructor - create a new IdentNode for the same location
     *
     * @param identNode  identNode
     */
    public IdentNode(final IdentNode identNode) {
        super(identNode);
        this.name  = identNode.getName();
        this.flags = identNode.flags;
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
    public IdentNode setType(final Type type) {
        if (DEBUG_FIELDS && getSymbol() != null && !Type.areEquivalent(getSymbol().getSymbolType(), type)) {
            ObjectClassGenerator.LOG.info(getClass().getName() + " " + this + " => " + type + " instead of " + getType());
        }
        // do NOT, repeat NOT touch the symbol here. it might be a local variable or whatever. This is the override if it isn't
        if(this.callSiteType == type) {
            return this;
        }
        final IdentNode n = (IdentNode)clone();
        n.callSiteType = type;
        return n;
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new IdentNode(this);
    }

    /**
     * Test to see if two IdentNode are the same.
     *
     * @param other Other ident.
     * @return true if the idents are the same.
     */
    @Override
    public boolean equals(final Object other) {
        if (other instanceof IdentNode) {
            return name.equals(((IdentNode)other).name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Assist in IR navigation.
     *
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enterIdentNode(this) != null) {
            return visitor.leaveIdentNode(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        if (hasCallSiteType()) {
            sb.append('{');
            final String desc = getType().getDescriptor();
            sb.append(desc.charAt(desc.length() - 1) == ';' ? "O" : getType().getDescriptor());
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
        if(isPropertyName()) return this;
        final IdentNode n = (IdentNode)clone();
        n.flags |= PROPERTY_NAME;
        return n;
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
        if(isInitializedHere()) return this;
        final IdentNode n = (IdentNode)clone();
        n.flags |= INITIALIZED_HERE;
        return n;
    }

    /**
     * Check if this IdentNode is a special identity, currently __DIR__, __FILE__
     * or __LINE__
     *
     * @return true if this IdentNode is special
     */
    public boolean isSpecialIdentity() {
        return name.equals(__DIR__.tag()) || name.equals(__FILE__.tag()) || name.equals(__LINE__.tag());
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
        if(isFunction()) return this;
        final IdentNode n = (IdentNode)clone();
        n.flags |= FUNCTION;
        return n;
    }
}
