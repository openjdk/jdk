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


package com.sun.xml.internal.xsom.util;

import com.sun.xml.internal.xsom.XSAnnotation;
import com.sun.xml.internal.xsom.XSAttGroupDecl;
import com.sun.xml.internal.xsom.XSAttributeDecl;
import com.sun.xml.internal.xsom.XSAttributeUse;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSComponent;
import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSFacet;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSNotation;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSSchema;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.XSIdentityConstraint;
import com.sun.xml.internal.xsom.XSXPath;
import com.sun.xml.internal.xsom.visitor.XSFunction;

/**
 * Utility implementation of {@link XSFunction} that returns
 * {@link Boolean} to find something from schema objects.
 *
 * <p>
 * This implementation returns <code>Boolean.FALSE</code> from
 * all of the methods. The derived class is expected to override
 * some of the methods to actually look for something.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class XSFinder implements XSFunction<Boolean> {

    /**
     * Invokes this object as a visitor with the specified component.
     */
    public final boolean find( XSComponent c ) {
        return c.apply(this);
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#annotation(com.sun.xml.internal.xsom.XSAnnotation)
     */
    public Boolean annotation(XSAnnotation ann) {
        return Boolean.FALSE;
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#attGroupDecl(com.sun.xml.internal.xsom.XSAttGroupDecl)
     */
    public Boolean attGroupDecl(XSAttGroupDecl decl) {
        return Boolean.FALSE;
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#attributeDecl(com.sun.xml.internal.xsom.XSAttributeDecl)
     */
    public Boolean attributeDecl(XSAttributeDecl decl) {
        return Boolean.FALSE;
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#attributeUse(com.sun.xml.internal.xsom.XSAttributeUse)
     */
    public Boolean attributeUse(XSAttributeUse use) {
        return Boolean.FALSE;
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#complexType(com.sun.xml.internal.xsom.XSComplexType)
     */
    public Boolean complexType(XSComplexType type) {
        return Boolean.FALSE;
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#schema(com.sun.xml.internal.xsom.XSSchema)
     */
    public Boolean schema(XSSchema schema) {
        return Boolean.FALSE;
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#facet(com.sun.xml.internal.xsom.XSFacet)
     */
    public Boolean facet(XSFacet facet) {
        return Boolean.FALSE;
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#notation(com.sun.xml.internal.xsom.XSNotation)
     */
    public Boolean notation(XSNotation notation) {
        return Boolean.FALSE;
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSContentTypeFunction#simpleType(com.sun.xml.internal.xsom.XSSimpleType)
     */
    public Boolean simpleType(XSSimpleType simpleType) {
        return Boolean.FALSE;
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSContentTypeFunction#particle(com.sun.xml.internal.xsom.XSParticle)
     */
    public Boolean particle(XSParticle particle) {
        return Boolean.FALSE;
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSContentTypeFunction#empty(com.sun.xml.internal.xsom.XSContentType)
     */
    public Boolean empty(XSContentType empty) {
        return Boolean.FALSE;
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSTermFunction#wildcard(com.sun.xml.internal.xsom.XSWildcard)
     */
    public Boolean wildcard(XSWildcard wc) {
        return Boolean.FALSE;
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSTermFunction#modelGroupDecl(com.sun.xml.internal.xsom.XSModelGroupDecl)
     */
    public Boolean modelGroupDecl(XSModelGroupDecl decl) {
        return Boolean.FALSE;
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSTermFunction#modelGroup(com.sun.xml.internal.xsom.XSModelGroup)
     */
    public Boolean modelGroup(XSModelGroup group) {
        return Boolean.FALSE;
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSTermFunction#elementDecl(com.sun.xml.internal.xsom.XSElementDecl)
     */
    public Boolean elementDecl(XSElementDecl decl) {
        return Boolean.FALSE;
    }

    public Boolean identityConstraint(XSIdentityConstraint decl) {
        return Boolean.FALSE;
    }

    public Boolean xpath(XSXPath xpath) {
        return Boolean.FALSE;
    }
}
