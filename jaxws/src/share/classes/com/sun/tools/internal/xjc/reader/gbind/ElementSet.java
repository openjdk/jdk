/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.tools.internal.xjc.reader.gbind;

import java.util.Collections;
import java.util.Iterator;

/**
 * A set over a list of {@link Element}.
 *
 * @author Kohsuke Kawaguchi
 */
interface ElementSet extends Iterable<Element> {
    /**
     * For each element in this set, adds an edge to the given element.
     */
    void addNext(Element element);

    public static final ElementSet EMPTY_SET = new ElementSet() {
        public void addNext(Element element) {
            // noop
        }

        public boolean contains(ElementSet element) {
            return this==element;
        }

        public Iterator<Element> iterator() {
            return Collections.<Element>emptySet().iterator();
        }
    };

    /**
     * Doesn't have to be strict (it's OK for this method to return false
     * when it's actually true) since this is used just for optimization.
     *
     * (Erring on the other side is NG)
     */
    boolean contains(ElementSet rhs);
}
