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

import com.sun.xml.internal.xsom.XSAttGroupDecl;
import com.sun.xml.internal.xsom.XSAttributeUse;
import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.impl.parser.DelayedRef;
import com.sun.xml.internal.xsom.impl.parser.SchemaDocumentImpl;
import com.sun.xml.internal.xsom.visitor.XSFunction;
import com.sun.xml.internal.xsom.visitor.XSVisitor;
import org.xml.sax.Locator;

import java.util.Iterator;

public class AttGroupDeclImpl extends AttributesHolder implements XSAttGroupDecl
{
    public AttGroupDeclImpl( SchemaDocumentImpl _parent, AnnotationImpl _annon,
        Locator _loc, ForeignAttributesImpl _fa, String _name, WildcardImpl _wildcard ) {

        this(_parent,_annon,_loc,_fa,_name);
        setWildcard(_wildcard);
    }

    public AttGroupDeclImpl( SchemaDocumentImpl _parent, AnnotationImpl _annon,
        Locator _loc, ForeignAttributesImpl _fa, String _name ) {

        super(_parent,_annon,_loc,_fa,_name,false);
    }


    private WildcardImpl wildcard;
    public void setWildcard( WildcardImpl wc ) { wildcard=wc; }
    public XSWildcard getAttributeWildcard() { return wildcard; }

    public XSAttributeUse getAttributeUse( String nsURI, String localName ) {
        UName name = new UName(nsURI,localName);
        XSAttributeUse o=null;

        Iterator itr = iterateAttGroups();
        while(itr.hasNext() && o==null)
            o = ((XSAttGroupDecl)itr.next()).getAttributeUse(nsURI,localName);

        if(o==null)     o = attributes.get(name);

        return o;
    }

    public void redefine( AttGroupDeclImpl ag ) {
        for (Iterator itr = attGroups.iterator(); itr.hasNext();) {
            DelayedRef.AttGroup r = (DelayedRef.AttGroup) itr.next();
            r.redefine(ag);
        }
    }

    public void visit( XSVisitor visitor ) {
        visitor.attGroupDecl(this);
    }
    public Object apply( XSFunction function ) {
        return function.attGroupDecl(this);
    }
}
