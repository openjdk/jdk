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

import jdk.nashorn.internal.runtime.Source;

/**
 * IR base for accessing/indexing nodes.
 *
 * @see AccessNode
 * @see IndexNode
 */
public abstract class BaseNode extends Node implements FunctionCall {

    /** Base Node. */
    protected Node base;

    /**
     * Constructor
     *
     * @param source source code
     * @param token  token
     * @param finish finish
     * @param base   base node
     */
    public BaseNode(final Source source, final long token, final int finish, final Node base) {
        super(source, token, finish);
        this.base = base;
        setStart(base.getStart());
    }

    /**
     * Copy constructor
     *
     * @param baseNode the base node
     * @param cs       a copy state
     */
    protected BaseNode(final BaseNode baseNode, final CopyState cs) {
        super(baseNode);
        this.base = cs.existingOrCopy(baseNode.getBase());
        setStart(base.getStart());
    }

    @Override
    public boolean equals(final Object other) {
        if (!super.equals(other)) {
            return false;
        }
        final BaseNode baseNode = (BaseNode)other;
        return base.equals(baseNode.getBase());
    }

    @Override
    public int hashCode() {
        return base.hashCode();
    }

    /**
     * Get the base node for this access
     * @return the base node
     */
    public Node getBase() {
        return base;
    }

    /**
     * Reset the base node for this access
     * @param base new base node
     */
    public void setBase(final Node base) {
        this.base = base;
    }

    @Override
    public boolean isFunction() {
        return false;
    }
}
