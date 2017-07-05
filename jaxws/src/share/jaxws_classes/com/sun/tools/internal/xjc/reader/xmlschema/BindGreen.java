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

package com.sun.tools.internal.xjc.reader.xmlschema;

import java.util.Iterator;

import com.sun.tools.internal.xjc.reader.Ring;
import com.sun.tools.internal.xjc.reader.xmlschema.ct.ComplexTypeFieldBuilder;
import com.sun.xml.internal.xsom.XSAttContainer;
import com.sun.xml.internal.xsom.XSAttGroupDecl;
import com.sun.xml.internal.xsom.XSAttributeDecl;
import com.sun.xml.internal.xsom.XSAttributeUse;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSWildcard;

/**
 * @author Kohsuke Kawaguchi
 */
public final class BindGreen extends ColorBinder {

    private final ComplexTypeFieldBuilder ctBuilder = Ring.get(ComplexTypeFieldBuilder.class);

    public void attGroupDecl(XSAttGroupDecl ag) {
        attContainer(ag);
    }

    public void attContainer(XSAttContainer cont) {
        // inline
        Iterator itr = cont.iterateDeclaredAttributeUses();
        while(itr.hasNext())
            builder.ying((XSAttributeUse)itr.next(),cont);
        itr = cont.iterateAttGroups();
        while(itr.hasNext())
            builder.ying((XSAttGroupDecl)itr.next(),cont);

        XSWildcard w = cont.getAttributeWildcard();
        if(w!=null)
            builder.ying(w,cont);
    }

    public void complexType(XSComplexType ct) {
        ctBuilder.build(ct);
    }








    public void attributeDecl(XSAttributeDecl xsAttributeDecl) {
        // TODO: implement this method later
        throw new UnsupportedOperationException();
    }

    public void wildcard(XSWildcard xsWildcard) {
        // TODO: implement this method later
        throw new UnsupportedOperationException();
    }

    public void modelGroupDecl(XSModelGroupDecl xsModelGroupDecl) {
        // TODO: implement this method later
        throw new UnsupportedOperationException();
    }

    public void modelGroup(XSModelGroup xsModelGroup) {
        // TODO: implement this method later
        throw new UnsupportedOperationException();
    }

    public void elementDecl(XSElementDecl xsElementDecl) {
        // TODO: implement this method later
        throw new UnsupportedOperationException();
    }

    public void particle(XSParticle xsParticle) {
        // TODO: implement this method later
        throw new UnsupportedOperationException();
    }

    public void empty(XSContentType xsContentType) {
        // TODO: implement this method later
        throw new UnsupportedOperationException();
    }


/*

    Components for which ying should yield to purple.

*/
    public void simpleType(XSSimpleType xsSimpleType) {
        // simple type always maps to a type, so this is never possible
        throw new IllegalStateException();
    }

    public void attributeUse(XSAttributeUse use) {
        // attribute use always maps to a property
        throw new IllegalStateException();
    }
}
