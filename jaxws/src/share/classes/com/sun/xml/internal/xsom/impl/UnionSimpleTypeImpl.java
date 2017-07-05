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
package com.sun.xml.internal.xsom.impl;

import com.sun.xml.internal.xsom.XSFacet;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSUnionSimpleType;
import com.sun.xml.internal.xsom.XSVariety;
import com.sun.xml.internal.xsom.impl.parser.SchemaDocumentImpl;
import com.sun.xml.internal.xsom.visitor.XSSimpleTypeFunction;
import com.sun.xml.internal.xsom.visitor.XSSimpleTypeVisitor;
import org.xml.sax.Locator;

import java.util.Iterator;
import java.util.Set;

public class UnionSimpleTypeImpl extends SimpleTypeImpl implements XSUnionSimpleType
{
    public UnionSimpleTypeImpl( SchemaDocumentImpl _parent,
                                AnnotationImpl _annon, Locator _loc, ForeignAttributesImpl _fa,
                                String _name, boolean _anonymous, Set<XSVariety> finalSet,
                                Ref.SimpleType[] _members ) {

        super(_parent,_annon,_loc,_fa,_name,_anonymous, finalSet,
            _parent.getSchema().parent.anySimpleType);

        this.memberTypes = _members;
    }

    private final Ref.SimpleType[] memberTypes;
    public XSSimpleType getMember( int idx ) { return memberTypes[idx].getType(); }
    public int getMemberSize() { return memberTypes.length; }

    public Iterator<XSSimpleType> iterator() {
        return new Iterator<XSSimpleType>() {
            int idx=0;
            public boolean hasNext() {
                return idx<memberTypes.length;
            }

            public XSSimpleType next() {
                return memberTypes[idx++].getType();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public void visit( XSSimpleTypeVisitor visitor ) {
        visitor.unionSimpleType(this);
    }
    public Object apply( XSSimpleTypeFunction function ) {
        return function.unionSimpleType(this);
    }

    public XSUnionSimpleType getBaseUnionType() {
        return this;
    }

    // union type by itself doesn't have any facet. */
    public XSFacet getFacet( String name ) { return null; }

    public XSVariety getVariety() { return XSVariety.LIST; }

    public XSSimpleType getPrimitiveType() { return null; }

    public boolean isUnion() { return true; }
    public XSUnionSimpleType asUnion() { return this; }
}
