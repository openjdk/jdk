/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.internal.txw2.model;

import org.xml.sax.Locator;

import java.util.Iterator;

/**
 * {@link Node} is a {@link Leaf} that has children.
 *
 * Children are orderless.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Node extends Leaf implements Iterable<Leaf> {

    /**
     * Children of this node.
     */
    public Leaf leaf;

    protected Node(Locator location, Leaf leaf) {
        super(location);
        this.leaf = leaf;
    }

    /**
     * Iterates all the children.
     */
    public final Iterator<Leaf> iterator() {
        return new CycleIterator(leaf);
    }

    /**
     * Returns true if this node has only one child node.
     */
    public final boolean hasOneChild() {
        return leaf==leaf.getNext();
    }

    /**
     * Adds the given {@link Leaf} and their sibling as children of this {@link Node}.
     */
    public final void addChild(Leaf child) {
        if(this.leaf==null)
            leaf = child;
        else
            leaf.merge(child);
    }

}
