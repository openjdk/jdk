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
package com.sun.tools.internal.xjc.reader.xmlschema;

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
 * This is the first color invoked from the parent component.
 *
 * @author Kohsuke Kawaguchi
 */
final class BindBlue extends ColorBinder {

    public void complexType(XSComplexType ct) {
        // TODO: implement this method later
        throw new UnsupportedOperationException();
    }

    public void elementDecl(XSElementDecl e) {
        // TODO: implement this method later
        throw new UnsupportedOperationException();
    }




    public void wildcard(XSWildcard xsWildcard) {
        // TODO: implement this method later
        throw new UnsupportedOperationException();
    }

    public void attGroupDecl(XSAttGroupDecl xsAttGroupDecl) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void attributeDecl(XSAttributeDecl xsAttributeDecl) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void attributeUse(XSAttributeUse use) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void modelGroupDecl(XSModelGroupDecl xsModelGroupDecl) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void modelGroup(XSModelGroup xsModelGroup) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void particle(XSParticle xsParticle) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void empty(XSContentType xsContentType) {
        // TODO
        throw new UnsupportedOperationException();
    }


/**
 * Components that always map to a type
 */
    public void simpleType(XSSimpleType type) {
        throw new IllegalStateException();
    }
}
