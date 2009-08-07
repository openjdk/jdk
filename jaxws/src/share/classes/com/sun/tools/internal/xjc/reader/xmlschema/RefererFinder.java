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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

import com.sun.xml.internal.xsom.XSAnnotation;
import com.sun.xml.internal.xsom.XSAttGroupDecl;
import com.sun.xml.internal.xsom.XSAttributeDecl;
import com.sun.xml.internal.xsom.XSAttributeUse;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSComponent;
import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSFacet;
import com.sun.xml.internal.xsom.XSIdentityConstraint;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSNotation;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSSchema;
import com.sun.xml.internal.xsom.XSSchemaSet;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSType;
import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.XSXPath;
import com.sun.xml.internal.xsom.visitor.XSVisitor;

/**
 * Finds which {@link XSComponent}s refer to which {@link XSComplexType}s.
 *
 * @author Kohsuke Kawaguchi
 */
final class RefererFinder implements XSVisitor {
    private final Set<Object> visited = new HashSet<Object>();

    private final Map<XSComponent,Set<XSComponent>> referers = new HashMap<XSComponent,Set<XSComponent>>();

    public Set<XSComponent> getReferer(XSComponent src) {
        Set<XSComponent> r = referers.get(src);
        if(r==null) return Collections.emptySet();
        return r;
    }


    public void schemaSet(XSSchemaSet xss) {
        if(!visited.add(xss))       return;

        for (XSSchema xs : xss.getSchemas()) {
            schema(xs);
        }
    }

    public void schema(XSSchema xs) {
        if(!visited.add(xs))       return;

        for (XSComplexType ct : xs.getComplexTypes().values()) {
            complexType(ct);
        }

        for (XSElementDecl e : xs.getElementDecls().values()) {
            elementDecl(e);
        }
    }

    public void elementDecl(XSElementDecl e) {
        if(!visited.add(e))       return;

        refer(e,e.getType());
        e.getType().visit(this);
    }

    public void complexType(XSComplexType ct) {
        if(!visited.add(ct))       return;

        refer(ct,ct.getBaseType());
        ct.getBaseType().visit(this);
        ct.getContentType().visit(this);
    }

    public void modelGroupDecl(XSModelGroupDecl decl) {
        if(!visited.add(decl))  return;

        modelGroup(decl.getModelGroup());
    }

    public void modelGroup(XSModelGroup group) {
        if(!visited.add(group))  return;

        for (XSParticle p : group.getChildren()) {
            particle(p);
        }
    }

    public void particle(XSParticle particle) {
        // since the particle method is side-effect free, no need to check for double-visit.
        particle.getTerm().visit(this);
    }


    // things we don't care
    public void simpleType(XSSimpleType simpleType) {}
    public void annotation(XSAnnotation ann) {}
    public void attGroupDecl(XSAttGroupDecl decl) {}
    public void attributeDecl(XSAttributeDecl decl) {}
    public void attributeUse(XSAttributeUse use) {}
    public void facet(XSFacet facet) {}
    public void notation(XSNotation notation) {}
    public void identityConstraint(XSIdentityConstraint decl) {}
    public void xpath(XSXPath xp) {}
    public void wildcard(XSWildcard wc) {}
    public void empty(XSContentType empty) {}

    /**
     * Called for each reference to record the fact.
     *
     * So far we only care about references to types.
     */
    private void refer(XSComponent source, XSType target) {
        Set<XSComponent> r = referers.get(target);
        if(r==null) {
            r = new HashSet<XSComponent>();
            referers.put(target,r);
        }
        r.add(source);
    }
}
