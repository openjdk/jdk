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

import java.util.LinkedHashSet;

/**
 * Factory methods for {@link ElementSet}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ElementSets {
    /**
     * Returns an union of two {@link ElementSet}s.
     *
     * This method performs better if lhs is bigger than rhs
     */
    public static ElementSet union(ElementSet lhs, ElementSet rhs) {
        if(lhs.contains(rhs))
            return lhs;
        if(lhs==ElementSet.EMPTY_SET)
            return rhs;
        if(rhs==ElementSet.EMPTY_SET)
            return lhs;
        return new MultiValueSet(lhs,rhs);
    }

    /**
     * {@link ElementSet} that has multiple {@link Element}s in it.
     *
     * This isn't particularly efficient or anything, but it will do for now.
     */
    private static final class MultiValueSet extends LinkedHashSet<Element> implements ElementSet {
        public MultiValueSet(ElementSet lhs, ElementSet rhs) {
            addAll(lhs);
            addAll(rhs);
            // not that anything will break with size==1 MultiValueSet,
            // but it does suggest that we are missing an easy optimization
            assert size()>1;
        }

        private void addAll(ElementSet lhs) {
            if(lhs instanceof MultiValueSet) {
                super.addAll((MultiValueSet)lhs);
            } else {
                for (Element e : lhs)
                    add(e);
            }
        }

        public boolean contains(ElementSet rhs) {
            // this isn't complete but sound
            return super.contains(rhs) || rhs==ElementSet.EMPTY_SET;
        }

        public void addNext(Element element) {
            for (Element e : this)
                e.addNext(element);
        }
    }
}
