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

package com.sun.xml.internal.xsom.impl.scd;

import com.sun.xml.internal.xsom.XSComponent;
import com.sun.xml.internal.xsom.XSDeclaration;
import com.sun.xml.internal.xsom.XSFacet;
import com.sun.xml.internal.xsom.XSType;
import com.sun.xml.internal.xsom.SCD;
import com.sun.xml.internal.xsom.XSSchema;
import com.sun.xml.internal.xsom.impl.UName;

import java.util.Iterator;

/**
 * Building block of {@link SCD}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Step<T extends XSComponent> {
    public final Axis<? extends T> axis;

    /**
     * 'Predicate' in SCD designates the index of the item. -1 if there's no predicate.
     * Predicate starts from 1.
     *
     * <p>
     * Because of the parsing order this parameter cannot be marked
     * final, even though it's immutable once it's parsed.
     */
    int predicate = -1;

    protected Step(Axis<? extends T> axis) {
        this.axis = axis;
    }

    /**
     * Perform filtering (which is different depending on the kind of step.)
     */
    protected abstract Iterator<? extends T> filter( Iterator<? extends T> base );

    /**
     * Evaluate this step against the current node set
     * and returns matched nodes.
     */
    public final Iterator<T> evaluate(Iterator<XSComponent> nodeSet) {
        // list up the whole thing
        Iterator<T> r = new Iterators.Map<T,XSComponent>(nodeSet) {
            protected Iterator<? extends T> apply(XSComponent contextNode) {
                return filter(axis.iterator(contextNode));
            }
        };

        // avoid duplicates
        r = new Iterators.Unique<T>(r);

        if(predicate>=0) {
            T item=null;
            for( int i=predicate; i>0; i-- ) {
                if(!r.hasNext())
                    return Iterators.empty();
                item = r.next();
            }
            return new Iterators.Singleton<T>(item);
        }

        return r;
    }

    /**
     * Matches any name.
     */
    static final class Any extends Step<XSComponent> {
        public Any(Axis<? extends XSComponent> axis) {
            super(axis);
        }

        // no filtering.
        protected Iterator<? extends XSComponent> filter(Iterator<? extends XSComponent> base) {
            return base;
        }
    }

    private static abstract class Filtered<T extends XSComponent> extends Step<T> {
        protected Filtered(Axis<? extends T> axis) {
            super(axis);
        }

        protected Iterator<T> filter(Iterator<? extends T> base) {
            return new Iterators.Filter<T>(base) {
                protected boolean matches(T d) {
                    return match(d);
                }
            };
        }

        protected abstract boolean match(T d);
    }

    /**
     * Matches a particular name.
     */
    static final class Named extends Filtered<XSDeclaration> {
        private final String nsUri;
        private final String localName;

        public Named(Axis<? extends XSDeclaration> axis, UName n) {
            this(axis,n.getNamespaceURI(),n.getName());
        }

        public Named(Axis<? extends XSDeclaration> axis, String nsUri, String localName) {
            super(axis);
            this.nsUri = nsUri;
            this.localName = localName;
        }

        protected boolean match(XSDeclaration d) {
            return d.getName().equals(localName) && d.getTargetNamespace().equals(nsUri);
        }
    }

    /**
     * Matches anonymous types.
     */
    static final class AnonymousType extends Filtered<XSType> {
        public AnonymousType(Axis<? extends XSType> axis) {
            super(axis);
        }

        protected boolean match(XSType node) {
            return node.isLocal();
        }
    }

    /**
     * Matches a particular kind of facets.
     */
    static final class Facet extends Filtered<XSFacet> {
        private final String name;
        public Facet(Axis<XSFacet> axis, String facetName) {
            super(axis);
            this.name = facetName;
        }

        protected boolean match(XSFacet f) {
            return f.getName().equals(name);
        }
    }

    /**
     * Matches a schema in a particular namespace.
     */
    static final class Schema extends Filtered<XSSchema> {
        private final String uri;
        public Schema(Axis<XSSchema> axis, String uri) {
            super(axis);
            this.uri = uri;
        }

        protected boolean match(XSSchema d) {
            return d.getTargetNamespace().equals(uri);
        }
    }
}
