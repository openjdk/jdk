/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.reader.gbind;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents one strongly-connected component
 * of the {@link Element} graph.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ConnectedComponent implements Iterable<Element> {
    /**
     * {@link Element}s that belong to this component.
     */
    private final List<Element> elements = new ArrayList<Element>();

    /*package*/ boolean isRequired;

    /**
     * Returns true iff this {@link ConnectedComponent}
     * can match a substring whose length is greater than 1.
     *
     * <p>
     * That means this property will become a collection property.
     */
    public final boolean isCollection() {
        assert !elements.isEmpty();

        // a strongly connected component by definition has a cycle,
        // so if its size is bigger than 1 there must be a cycle.
        if(elements.size()>1)
            return true;

        // if size is 1, it might be still forming a self-cycle
        Element n = elements.get(0);
        return n.hasSelfLoop();
    }

    /**
     * Returns true iff this {@link ConnectedComponent}
     * forms a cut set of a graph.
     *
     * <p>
     * That means any valid element sequence must have at least
     * one value for this property.
     */
    public final boolean isRequired() {
        return isRequired;
    }

    /*package*/void add(Element e) {
        assert !elements.contains(e);
        elements.add(e);
    }

    public Iterator<Element> iterator() {
        return elements.iterator();
    }

    /**
     * Just produces debug representation
     */
    public String toString() {
        String s = elements.toString();
        if(isRequired())
            s += '!';
        if(isCollection())
            s += '*';
        return s;
    }
}
