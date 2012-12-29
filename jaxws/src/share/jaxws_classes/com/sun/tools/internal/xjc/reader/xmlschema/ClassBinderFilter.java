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

package com.sun.tools.internal.xjc.reader.xmlschema;

import com.sun.tools.internal.xjc.model.CElement;
import com.sun.xml.internal.xsom.XSAnnotation;
import com.sun.xml.internal.xsom.XSAttGroupDecl;
import com.sun.xml.internal.xsom.XSAttributeDecl;
import com.sun.xml.internal.xsom.XSAttributeUse;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSFacet;
import com.sun.xml.internal.xsom.XSIdentityConstraint;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSNotation;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSSchema;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.XSXPath;

/**
 * {@link ClassBinder} that delegates the call to another {@link ClassBinder}.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
abstract class ClassBinderFilter implements ClassBinder {
    private final ClassBinder core;

    protected ClassBinderFilter(ClassBinder core) {
        this.core = core;
    }

    public CElement annotation(XSAnnotation xsAnnotation) {
        return core.annotation(xsAnnotation);
    }

    public CElement attGroupDecl(XSAttGroupDecl xsAttGroupDecl) {
        return core.attGroupDecl(xsAttGroupDecl);
    }

    public CElement attributeDecl(XSAttributeDecl xsAttributeDecl) {
        return core.attributeDecl(xsAttributeDecl);
    }

    public CElement attributeUse(XSAttributeUse xsAttributeUse) {
        return core.attributeUse(xsAttributeUse);
    }

    public CElement complexType(XSComplexType xsComplexType) {
        return core.complexType(xsComplexType);
    }

    public CElement schema(XSSchema xsSchema) {
        return core.schema(xsSchema);
    }

    public CElement facet(XSFacet xsFacet) {
        return core.facet(xsFacet);
    }

    public CElement notation(XSNotation xsNotation) {
        return core.notation(xsNotation);
    }

    public CElement simpleType(XSSimpleType xsSimpleType) {
        return core.simpleType(xsSimpleType);
    }

    public CElement particle(XSParticle xsParticle) {
        return core.particle(xsParticle);
    }

    public CElement empty(XSContentType xsContentType) {
        return core.empty(xsContentType);
    }

    public CElement wildcard(XSWildcard xsWildcard) {
        return core.wildcard(xsWildcard);
    }

    public CElement modelGroupDecl(XSModelGroupDecl xsModelGroupDecl) {
        return core.modelGroupDecl(xsModelGroupDecl);
    }

    public CElement modelGroup(XSModelGroup xsModelGroup) {
        return core.modelGroup(xsModelGroup);
    }

    public CElement elementDecl(XSElementDecl xsElementDecl) {
        return core.elementDecl(xsElementDecl);
    }

    public CElement identityConstraint(XSIdentityConstraint xsIdentityConstraint) {
        return core.identityConstraint(xsIdentityConstraint);
    }

    public CElement xpath(XSXPath xsxPath) {
        return core.xpath(xsxPath);
    }
}
