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

import jdk.nashorn.internal.ir.annotations.Reference;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation of an object literal property.
 */
public class PropertyNode extends Node {

    /** Property key. */
    private PropertyKey key;

    /** Property value. */
    private Node value;

    /** Property getter. */
    @Reference
    private Node getter;

    /** Property getter. */
    @Reference
    private Node setter;

    /**
     * Constructor
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param key     the key of this property
     * @param value   the value of this property
     */
    public PropertyNode(final Source source, final long token, final int finish, final PropertyKey key, final Node value) {
        super(source, token, finish);

        this.key    = key;
        this.value  = value;
    }

    private PropertyNode(final PropertyNode propertyNode, final CopyState cs) {
        super(propertyNode);

        this.key    = (PropertyKey)cs.existingOrCopy((Node)propertyNode.key);
        this.value  = cs.existingOrCopy(propertyNode.value);
        this.getter = cs.existingOrSame(propertyNode.getter);
        this.setter = cs.existingOrSame(propertyNode.setter);
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new PropertyNode(this, cs);
    }

    /**
     * Get the name of the property key
     * @return key name
     */
    public String getKeyName() {
        return key.getPropertyName();
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enterPropertyNode(this) != null) {
            key = (PropertyKey)((Node)key).accept(visitor);

            if (value != null) {
                value = value.accept(visitor);
            }

            if (getter != null) {
                getter = getter.accept(visitor);
            }

            if (setter != null) {
                setter = setter.accept(visitor);
            }

            return visitor.leavePropertyNode(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        if (value instanceof FunctionNode && ((FunctionNode)value).getIdent() != null) {
            value.toString(sb);
        }

        if (value != null) {
            ((Node)key).toString(sb);
            sb.append(": ");
            value.toString(sb);
        }

        if (getter != null) {
            sb.append(' ');
            getter.toString(sb);
        }

        if (setter != null) {
            sb.append(' ');
            setter.toString(sb);
        }
    }

    /**
     * Get the getter for this property
     * @return getter or null if none exists
     */
    public Node getGetter() {
        return getter;
    }

    /**
     * Set the getter of this property, null if none
     * @param getter getter
     */
    public void setGetter(final Node getter) {
        this.getter = getter;
    }

    /**
     * Return the key for this property node
     * @return the key
     */
    public Node getKey() {
        return (Node)key;
    }

    /**
     * Get the setter for this property
     * @return setter or null if none exists
     */
    public Node getSetter() {
        return setter;
    }

    /**
     * Set the setter for this property, null if none
     * @param setter setter
     */
    public void setSetter(final Node setter) {
        this.setter = setter;
    }

    /**
     * Get the value of this property
     * @return property value
     */
    public Node getValue() {
        return value;
    }

    /**
     * Set the value of this property
     * @param value new value
     */
    public void setValue(final Node value) {
        this.value = value;
    }
}
