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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.sun.tools.internal.xjc.reader.Const;
import com.sun.tools.internal.xjc.reader.Ring;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIDeclaration;
import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import com.sun.xml.internal.xsom.XSAnnotation;
import com.sun.xml.internal.xsom.XSAttContainer;
import com.sun.xml.internal.xsom.XSAttGroupDecl;
import com.sun.xml.internal.xsom.XSAttributeDecl;
import com.sun.xml.internal.xsom.XSAttributeUse;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSComponent;
import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSFacet;
import com.sun.xml.internal.xsom.XSIdentityConstraint;
import com.sun.xml.internal.xsom.XSListSimpleType;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSNotation;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSRestrictionSimpleType;
import com.sun.xml.internal.xsom.XSSchema;
import com.sun.xml.internal.xsom.XSSchemaSet;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSUnionSimpleType;
import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.XSXPath;
import com.sun.xml.internal.xsom.visitor.XSSimpleTypeVisitor;
import com.sun.xml.internal.xsom.visitor.XSVisitor;

/**
 * Reports all unacknowledged customizations as errors.
 *
 * <p>
 * Since we scan the whole content tree, we use this to check for unused
 * <tt>xmime:expectedContentTypes</tt> attributes. TODO: if we find this kind of error checks more
 * common, use the visitors so that we don't have to mix everything in one class.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
class UnusedCustomizationChecker extends BindingComponent implements XSVisitor, XSSimpleTypeVisitor {
    private final BGMBuilder builder = Ring.get(BGMBuilder.class);
    private final SimpleTypeBuilder stb = Ring.get(SimpleTypeBuilder.class);

    private final Set<XSComponent> visitedComponents = new HashSet<XSComponent>();

    /**
     * Runs the check.
     */
    void run() {
        for( XSSchema s : Ring.get(XSSchemaSet.class).getSchemas() ) {
            schema(s);
            run( s.getAttGroupDecls() );
            run( s.getAttributeDecls() );
            run( s.getComplexTypes() );
            run( s.getElementDecls() );
            run( s.getModelGroupDecls() );
            run( s.getNotations() );
            run( s.getSimpleTypes() );
        }
    }

    private void run( Map<String,? extends XSComponent> col ) {
        for( XSComponent c : col.values() )
            c.visit(this);
    }


    /**
     * Checks unused customizations on this component
     * and returns true if this is the first time this
     * component is checked.
     */
    private boolean check( XSComponent c ) {
        if( !visitedComponents.add(c) )
            return false;   // already processed

        for( BIDeclaration decl : builder.getBindInfo(c).getDecls() )
            check(decl, c);

        checkExpectedContentTypes(c);

        return true;
    }

    private void checkExpectedContentTypes(XSComponent c) {
        if(c.getForeignAttribute(WellKnownNamespace.XML_MIME_URI, Const.EXPECTED_CONTENT_TYPES)==null)
            return; // no such attribute
        if(c instanceof XSParticle)
            return; // particles get the same foreign attributes as local element decls,
                    // so we need to skip them

        if(!stb.isAcknowledgedXmimeContentTypes(c)) {
            // this is not used
            getErrorReporter().warning(c.getLocator(),Messages.WARN_UNUSED_EXPECTED_CONTENT_TYPES);
        }
    }

    private void check(BIDeclaration decl, XSComponent c) {
        if( !decl.isAcknowledged() ) {
            getErrorReporter().error(
                decl.getLocation(),
                Messages.ERR_UNACKNOWLEDGED_CUSTOMIZATION,
                decl.getName().getLocalPart()
                );
            getErrorReporter().error(
                c.getLocator(),
                Messages.ERR_UNACKNOWLEDGED_CUSTOMIZATION_LOCATION);
            // mark it as acknowledged to avoid
            // duplicated error messages.
            decl.markAsAcknowledged();
        }
        for (BIDeclaration d : decl.getChildren())
            check(d,c);
    }


    public void annotation(XSAnnotation ann) {}

    public void attGroupDecl(XSAttGroupDecl decl) {
        if(check(decl))
            attContainer(decl);
    }

    public void attributeDecl(XSAttributeDecl decl) {
        if(check(decl))
            decl.getType().visit((XSSimpleTypeVisitor)this);
    }

    public void attributeUse(XSAttributeUse use) {
        if(check(use))
            use.getDecl().visit(this);
    }

    public void complexType(XSComplexType type) {
        if(check(type)) {
            // don't need to check the base type -- it must be global, thus
            // it is covered already
            type.getContentType().visit(this);
            attContainer(type);
        }
    }

    private void attContainer( XSAttContainer cont ) {
        for( Iterator itr = cont.iterateAttGroups(); itr.hasNext(); )
            ((XSAttGroupDecl)itr.next()).visit(this);

        for( Iterator itr = cont.iterateDeclaredAttributeUses(); itr.hasNext(); )
            ((XSAttributeUse)itr.next()).visit(this);

        XSWildcard wc = cont.getAttributeWildcard();
        if(wc!=null)        wc.visit(this);
    }

    public void schema(XSSchema schema) {
        check(schema);
    }

    public void facet(XSFacet facet) {
        check(facet);
    }

    public void notation(XSNotation notation) {
        check(notation);
    }

    public void wildcard(XSWildcard wc) {
        check(wc);
    }

    public void modelGroupDecl(XSModelGroupDecl decl) {
        if(check(decl))
            decl.getModelGroup().visit(this);
    }

    public void modelGroup(XSModelGroup group) {
        if(check(group)) {
            for( int i=0; i<group.getSize(); i++ )
                group.getChild(i).visit(this);
        }
    }

    public void elementDecl(XSElementDecl decl) {
        if(check(decl)) {
            decl.getType().visit(this);
            for( XSIdentityConstraint id : decl.getIdentityConstraints() )
                id.visit(this);
        }
    }

    public void simpleType(XSSimpleType simpleType) {
        if(check(simpleType))
            simpleType.visit( (XSSimpleTypeVisitor)this );
    }

    public void particle(XSParticle particle) {
        if(check(particle))
            particle.getTerm().visit(this);
    }

    public void empty(XSContentType empty) {
        check(empty);
    }

    public void listSimpleType(XSListSimpleType type) {
        if(check(type))
            type.getItemType().visit((XSSimpleTypeVisitor)this);
    }

    public void restrictionSimpleType(XSRestrictionSimpleType type) {
        if(check(type))
            type.getBaseType().visit(this);
    }

    public void unionSimpleType(XSUnionSimpleType type) {
        if(check(type)) {
            for( int i=0; i<type.getMemberSize(); i++ )
                type.getMember(i).visit((XSSimpleTypeVisitor)this);
        }
    }

    public void identityConstraint(XSIdentityConstraint id) {
        if(check(id)) {
            id.getSelector().visit(this);
            for( XSXPath xp : id.getFields() )
                xp.visit(this);
        }
    }

    public void xpath(XSXPath xp) {
        check(xp);
    }

}
