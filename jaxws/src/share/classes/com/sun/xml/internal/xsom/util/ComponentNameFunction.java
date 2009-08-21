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
 * Extract the name of the components.
 *
 * @author <ul><li>Ryan Shoemaker, Sun Microsystems, Inc.</li></ul>
 */
public class ComponentNameFunction implements XSFunction<String> {

    // delegate to this object to get the localized name of the component type
    private NameGetter nameGetter = new NameGetter(null);

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#annotation(XSAnnotation)
     */
    public String annotation(XSAnnotation ann) {
        // unnamed component
        return nameGetter.annotation( ann );
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#attGroupDecl(XSAttGroupDecl)
     */
    public String attGroupDecl(XSAttGroupDecl decl) {
        String name = decl.getName();
        if( name == null ) name = "";
        return name + " " + nameGetter.attGroupDecl( decl );
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#attributeDecl(XSAttributeDecl)
     */
    public String attributeDecl(XSAttributeDecl decl) {
        String name = decl.getName();
        if( name == null ) name = "";
        return name + " " + nameGetter.attributeDecl( decl );
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#attributeUse(XSAttributeUse)
     */
    public String attributeUse(XSAttributeUse use) {
        // unnamed component
        return nameGetter.attributeUse( use );
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#complexType(XSComplexType)
     */
    public String complexType(XSComplexType type) {
        String name = type.getName();
        if( name == null ) name = "anonymous";
        return name + " " + nameGetter.complexType( type );
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#schema(XSSchema)
     */
    public String schema(XSSchema schema) {
        return nameGetter.schema( schema ) + " \"" + schema.getTargetNamespace()+"\"";
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#facet(XSFacet)
     */
    public String facet(XSFacet facet) {
        String name = facet.getName();
        if( name == null ) name = "";
        return name + " " + nameGetter.facet( facet );
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSFunction#notation(XSNotation)
     */
    public String notation(XSNotation notation) {
        String name = notation.getName();
        if( name == null ) name = "";
        return name + " " + nameGetter.notation( notation );
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSContentTypeFunction#simpleType(XSSimpleType)
     */
    public String simpleType(XSSimpleType simpleType) {
        String name = simpleType.getName();
        if( name == null ) name = "anonymous";
        return name + " " + nameGetter.simpleType( simpleType );
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSContentTypeFunction#particle(XSParticle)
     */
    public String particle(XSParticle particle) {
        // unnamed component
        return nameGetter.particle( particle );
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSContentTypeFunction#empty(XSContentType)
     */
    public String empty(XSContentType empty) {
        // unnamed component
        return nameGetter.empty( empty );
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSTermFunction#wildcard(XSWildcard)
     */
    public String wildcard(XSWildcard wc) {
        // unnamed component
        return nameGetter.wildcard( wc );
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSTermFunction#modelGroupDecl(XSModelGroupDecl)
     */
    public String modelGroupDecl(XSModelGroupDecl decl) {
        String name = decl.getName();
        if( name == null ) name = "";
        return name + " " + nameGetter.modelGroupDecl( decl );
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSTermFunction#modelGroup(XSModelGroup)
     */
    public String modelGroup(XSModelGroup group) {
        // unnamed component
        return nameGetter.modelGroup( group );
    }

    /**
     * @see com.sun.xml.internal.xsom.visitor.XSTermFunction#elementDecl(XSElementDecl)
     */
    public String elementDecl(XSElementDecl decl) {
        String name = decl.getName();
        if( name == null ) name = "";
        return name + " " + nameGetter.elementDecl( decl );
    }

    public String identityConstraint(XSIdentityConstraint decl) {
        return decl.getName()+" "+nameGetter.identityConstraint(decl);
    }

    public String xpath(XSXPath xpath) {
        return nameGetter.xpath(xpath);
    }
}
