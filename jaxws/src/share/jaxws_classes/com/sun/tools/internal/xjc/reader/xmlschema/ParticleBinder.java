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

import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;

import com.sun.codemodel.internal.JJavaName;
import com.sun.tools.internal.xjc.model.CClassInfo;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.reader.Ring;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIDeclaration;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIProperty;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSTerm;
import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.visitor.XSTermVisitor;

/**
 * Binds the content models of {@link XSParticle} as properties of the class that's being built.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public abstract class ParticleBinder {

    protected final BGMBuilder builder = Ring.get(BGMBuilder.class);

    protected ParticleBinder() {
        // make sure that this object is available as ParticleBinder, not as their actual implementation classes
        Ring.add(ParticleBinder.class,this);
    }

    /**
     * Builds the {@link CPropertyInfo}s from the given particle
     * (and its descendants), and set them to the class returned by
     * {@link ClassSelector#getCurrentBean()}.
     */
    public final void build( XSParticle p ) {
        build(p, Collections.<XSParticle>emptySet());
    }

    /**
     * The version of the build method that forces a specified set of particles
     * to become a property.
     */
    public abstract void build( XSParticle p, Collection<XSParticle> forcedProps );

    /**
     * Similar to the build method but this method only checks if
     * the BGM that will be built by the build method will
     * do the fallback (map all the properties into one list) or not.
     *
     * @return
     *      false if the fallback will not happen.
     */
    public abstract boolean checkFallback( XSParticle p );


//
//
// convenient utility methods
//
//

    protected final CClassInfo getCurrentBean() {
        return getClassSelector().getCurrentBean();
    }


    /**
     * Gets the BIProperty object that applies to the given particle.
     */
    protected final BIProperty getLocalPropCustomization( XSParticle p ) {
        return getLocalCustomization(p,BIProperty.class);
    }

    protected final <T extends BIDeclaration> T getLocalCustomization( XSParticle p, Class<T> type ) {
        // check the property customization of this component first
        T cust = builder.getBindInfo(p).get(type);
        if(cust!=null)  return cust;

        // if not, the term might have one.
        cust = builder.getBindInfo(p.getTerm()).get(type);
        if(cust!=null)  return cust;

        return null;
    }

    /**
     * Computes the label of a given particle.
     * Usually, the getLabel method should be used instead.
     */
    protected final String computeLabel( XSParticle p ) {
        // if the particle carries a customization, use that value.
        // since we are binding content models, it's always non-constant properties.
        BIProperty cust = getLocalPropCustomization(p);
        if(cust!=null && cust.getPropertyName(false)!=null)
            return cust.getPropertyName(false);

        // no explicit property name is given. Compute one.

        XSTerm t = p.getTerm();

//        // first, check if a term is going to be a class, if so, use that name.
//        ClassItem ci = owner.selector.select(t);
//        if(ci!=null) {
//            return makeJavaName(ci.getTypeAsDefined().name());
//        }

        // if it fails, compute the default name according to the spec.
        if(t.isElementDecl())
            // for element, take the element name.
            return makeJavaName(p,t.asElementDecl().getName());
        if(t.isModelGroupDecl())
            // for named model groups, take that name
            return makeJavaName(p,t.asModelGroupDecl().getName());
        if(t.isWildcard())
            // the spec says it will map to "any" by default.
            return makeJavaName(p,"Any");
        if(t.isModelGroup()) {
            try {
                return getSpecDefaultName(t.asModelGroup(),p.isRepeated());
            } catch( ParseException e ) {
                // unable to generate a name.
                getErrorReporter().error(t.getLocator(),
                    Messages.ERR_UNABLE_TO_GENERATE_NAME_FROM_MODELGROUP);
                return "undefined"; // recover from error by assuming something
            }
        }

        // there are only four types of XSTerm.
        throw new AssertionError();
    }

    /** Converts an XML name to the corresponding Java name. */
    protected final String makeJavaName( boolean isRepeated, String xmlName ) {
        String name = builder.getNameConverter().toPropertyName(xmlName);
        if(builder.getGlobalBinding().isSimpleMode() && isRepeated )
            name = JJavaName.getPluralForm(name);
        return name;
    }

    protected final String makeJavaName( XSParticle p, String xmlName ) {
        return makeJavaName(p.isRepeated(),xmlName);
    }

    /**
     * Computes a name from unnamed model group by following the spec.
     *
     * Taking first three elements and combine them.
     *
     * @param repeated
     *      if the said model group is repeated more than once
     *
     * @exception ParseException
     *      If the method cannot generate a name. For example, when
     *      a model group doesn't contain any element reference/declaration
     *      at all.
     */
    protected final String getSpecDefaultName( XSModelGroup mg, final boolean repeated ) throws ParseException {

        final StringBuilder name = new StringBuilder();

        mg.visit(new XSTermVisitor() {
            /**
             * Count the number of tokens we combined.
             * We will concat up to 3.
             */
            private int count=0;

            /**
             * Is the current particple/term repeated?
             */
            private boolean rep = repeated;

            public void wildcard(XSWildcard wc) {
                append("any");
            }

            public void modelGroupDecl(XSModelGroupDecl mgd) {
                modelGroup(mgd.getModelGroup());
            }

            public void modelGroup(XSModelGroup mg) {
                String operator;
                if(mg.getCompositor()==XSModelGroup.CHOICE)     operator = "Or";
                else                                            operator = "And";

                int size = mg.getSize();
                for( int i=0; i<size; i++ ) {
                    XSParticle p = mg.getChild(i);
                    boolean oldRep = rep;
                    rep |= p.isRepeated();
                    p.getTerm().visit(this);
                    rep = oldRep;

                    if(count==3)    return; // we have enough
                    if(i!=size-1)   name.append(operator);
                }
            }

            public void elementDecl(XSElementDecl ed) {
                append(ed.getName());
            }

            private void append(String token) {
                if( count<3 ) {
                    name.append(makeJavaName(rep,token));
                    count++;
                }
            }
        });

        if(name.length()==0) throw new ParseException("no element",-1);

        return name.toString();
    }



    protected final ErrorReporter getErrorReporter() {
        return Ring.get(ErrorReporter.class);
    }
    protected final ClassSelector getClassSelector() {
        return Ring.get(ClassSelector.class);
    }
}
