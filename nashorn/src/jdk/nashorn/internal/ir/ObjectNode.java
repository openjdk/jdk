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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation of an object literal.
 */
public class ObjectNode extends Node {
    /** Literal context. */
    @Ignore
    private Block context;

    /** Literal elements. */
    private final List<Node> elements;

    /**
     * Constructor
     *
     * @param source   the source
     * @param token    token
     * @param finish   finish
     * @param context  the block for this ObjectNode
     * @param elements the elements used to initialize this ObjectNode
     */
    public ObjectNode(final Source source, final long token, final int finish, final Block context, final List<Node> elements) {
        super(source, token, finish);

        this.context  = context;
        this.elements = elements;
    }

    private ObjectNode(final ObjectNode objectNode, final CopyState cs) {
        super(objectNode);

        final List<Node> newElements = new ArrayList<>();

        for (final Node element : objectNode.elements) {
            newElements.add(cs.existingOrCopy(element));
        }

        this.context  = (Block)cs.existingOrCopy(objectNode.context);
        this.elements = newElements;
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new ObjectNode(this, cs);
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enter(this) != null) {
            if (context != null) {
                context = (Block)context.accept(visitor);
            }

            for (int i = 0, count = elements.size(); i < count; i++) {
                elements.set(i, elements.get(i).accept(visitor));
            }

            return visitor.leave(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append('{');

        if (!elements.isEmpty()) {
            sb.append(' ');

            boolean first = true;
            for (final Node element : elements) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;

                element.toString(sb);
            }
            sb.append(' ');
        }

        sb.append('}');
    }

    /**
     * Get the block that is this ObjectNode's literal context
     * @return the block
     */
    public Block getContext() {
        return context;
    }

    /**
     * Get the elements of this literal node
     * @return a list of elements
     */
    public List<Node> getElements() {
        return Collections.unmodifiableList(elements);
    }
}
