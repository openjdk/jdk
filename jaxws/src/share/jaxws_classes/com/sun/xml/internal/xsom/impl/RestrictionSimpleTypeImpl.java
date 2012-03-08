/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.xsom.impl;

import com.sun.xml.internal.xsom.XSFacet;
import com.sun.xml.internal.xsom.XSRestrictionSimpleType;
import com.sun.xml.internal.xsom.XSVariety;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.impl.parser.SchemaDocumentImpl;
import com.sun.xml.internal.xsom.visitor.XSSimpleTypeFunction;
import com.sun.xml.internal.xsom.visitor.XSSimpleTypeVisitor;
import org.xml.sax.Locator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class RestrictionSimpleTypeImpl extends SimpleTypeImpl implements XSRestrictionSimpleType {

    public RestrictionSimpleTypeImpl( SchemaDocumentImpl _parent,
                                      AnnotationImpl _annon, Locator _loc, ForeignAttributesImpl _fa,
                                      String _name, boolean _anonymous, Set<XSVariety> finalSet,
                                      Ref.SimpleType _baseType ) {

        super( _parent, _annon, _loc, _fa, _name, _anonymous, finalSet, _baseType );
    }


    private final List<XSFacet> facets = new ArrayList<XSFacet>();
    public void addFacet( XSFacet facet ) {
        facets.add(facet);
    }
    public Iterator<XSFacet> iterateDeclaredFacets() {
        return facets.iterator();
    }

    public Collection<? extends XSFacet> getDeclaredFacets() {
        return facets;
    }

    public XSFacet getDeclaredFacet( String name ) {
        int len = facets.size();
        for( int i=0; i<len; i++ ) {
            XSFacet f = facets.get(i);
            if(f.getName().equals(name))
                return f;
        }
        return null;
    }

    public List<XSFacet> getDeclaredFacets(String name) {
        List<XSFacet> r = new ArrayList<XSFacet>();
        for( XSFacet f : facets )
            if(f.getName().equals(name))
                r.add(f);
        return r;
    }

    public XSFacet getFacet( String name ) {
        XSFacet f = getDeclaredFacet(name);
        if(f!=null)     return f;

        // none was found on this datatype. check the base type.
        return getSimpleBaseType().getFacet(name);
    }

    public List<XSFacet> getFacets( String name ) {
        List<XSFacet> f = getDeclaredFacets(name);
        if(!f.isEmpty())     return f;

        // none was found on this datatype. check the base type.
        return getSimpleBaseType().getFacets(name);
    }

    public XSVariety getVariety() { return getSimpleBaseType().getVariety(); }

    public XSSimpleType getPrimitiveType() {
        if(isPrimitive())       return this;
        return getSimpleBaseType().getPrimitiveType();
    }

    public boolean isPrimitive() {
        return getSimpleBaseType()==getOwnerSchema().getRoot().anySimpleType;
    }

    public void visit( XSSimpleTypeVisitor visitor ) {
        visitor.restrictionSimpleType(this);
    }
    public Object apply( XSSimpleTypeFunction function ) {
        return function.restrictionSimpleType(this);
    }

    public boolean isRestriction() { return true; }
    public XSRestrictionSimpleType asRestriction() { return this; }
}
