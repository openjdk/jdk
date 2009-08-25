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
import java.util.Map;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.namespace.QName;

import com.sun.tools.internal.xjc.reader.gbind.Choice;
import com.sun.tools.internal.xjc.reader.gbind.Element;
import com.sun.tools.internal.xjc.reader.gbind.Expression;
import com.sun.tools.internal.xjc.reader.gbind.OneOrMore;
import com.sun.tools.internal.xjc.reader.gbind.Sequence;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.visitor.XSTermFunction;

/**
 * Visits {@link XSParticle} and creates a corresponding {@link Expression} tree.
 * @author Kohsuke Kawaguchi
 */
public final class ExpressionBuilder implements XSTermFunction<Expression> {

    public static Expression createTree(XSParticle p) {
        return new ExpressionBuilder().particle(p);
    }

    private ExpressionBuilder() {}

    /**
     * Wildcard instance needs to be consolidated to one,
     * and this is such instance (if any.)
     */
    private GWildcardElement wildcard = null;

    private final Map<QName,GElementImpl> decls = new HashMap<QName,GElementImpl>();

    private XSParticle current;

    /**
     * We can only have one {@link XmlAnyElement} property,
     * so all the wildcards need to be treated as one node.
     */
    public Expression wildcard(XSWildcard wc) {
        if(wildcard==null)
            wildcard = new GWildcardElement();
        wildcard.merge(wc);
        wildcard.particles.add(current);
        return wildcard;
    }

    public Expression modelGroupDecl(XSModelGroupDecl decl) {
        return modelGroup(decl.getModelGroup());
    }

    public Expression modelGroup(XSModelGroup group) {
        XSModelGroup.Compositor comp = group.getCompositor();
        if(comp==XSModelGroup.CHOICE) {
            // empty choice is not epsilon, but empty set,
            // so this initial value is incorrect. But this
            // kinda works.
            // properly handling empty set requires more work.
            Expression e = Expression.EPSILON;
            for (XSParticle p : group.getChildren()) {
                if(e==null)     e = particle(p);
                else            e = new Choice(e,particle(p));
            }
            return e;
        } else {
            Expression e = Expression.EPSILON;
            for (XSParticle p : group.getChildren()) {
                if(e==null)     e = particle(p);
                else            e = new Sequence(e,particle(p));
            }
            return e;
        }
    }

    public Element elementDecl(XSElementDecl decl) {
        QName n = BGMBuilder.getName(decl);

        GElementImpl e = decls.get(n);
        if(e==null)
            decls.put(n,e=new GElementImpl(n,decl));

        e.particles.add(current);
        assert current.getTerm()==decl;

        return e;
    }

    public Expression particle(XSParticle p) {
        current = p;
        Expression e = p.getTerm().apply(this);

        if(p.isRepeated())
            e = new OneOrMore(e);

        if(p.getMinOccurs()==0)
            e = new Choice(e,Expression.EPSILON);

        return e;
    }

}
