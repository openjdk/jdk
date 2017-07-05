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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import com.sun.tools.internal.xjc.model.CClassInfo;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.model.CReferencePropertyInfo;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIProperty;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSTerm;
import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.visitor.XSTermVisitor;
import java.math.BigInteger;

/**
 * {@link ParticleBinder} that follows the JAXB spec.
 *
 * @author Kohsuke Kawaguchi
 */
final class DefaultParticleBinder extends ParticleBinder {

    @Override
    public void build( XSParticle p, Collection<XSParticle> forcedProps ) {
        Checker checker = checkCollision(p,forcedProps);

        if(checker.hasNameCollision()) {
            CReferencePropertyInfo prop = new CReferencePropertyInfo(
                getCurrentBean().getBaseClass()==null?"Content":"Rest",
                true, false, false, p,
                builder.getBindInfo(p).toCustomizationList(),
                p.getLocator(), false, false, false);
            RawTypeSetBuilder.build(p,false).addTo(prop);
            prop.javadoc = Messages.format( Messages.MSG_FALLBACK_JAVADOC,
                    checker.getCollisionInfo().toString() );

            getCurrentBean().addProperty(prop);
        } else {
            new Builder(checker.markedParticles).particle(p);
        }
    }

    @Override
    public boolean checkFallback( XSParticle p ) {
        return checkCollision(p,Collections.<XSParticle>emptyList()).hasNameCollision();
    }

    private Checker checkCollision( XSParticle p, Collection<XSParticle> forcedProps ) {
        // scan the tree by a checker.
        Checker checker = new Checker(forcedProps);

        CClassInfo superClass = getCurrentBean().getBaseClass();

        if(superClass!=null)
            checker.readSuperClass(superClass);
        checker.particle(p);

        return checker;
    }










    /**
     * Marks particles that need to be mapped to properties,
     * by reading customization info.
     */
    private final class Checker implements XSTermVisitor {

        Checker(Collection<XSParticle> forcedProps) {
            this.forcedProps = forcedProps;
        }

        boolean hasNameCollision() {
            return collisionInfo!=null;
        }

        CollisionInfo getCollisionInfo() {
            return collisionInfo;
        }

        /**
         * If a collision is found, this field will be non-null.
         */
        private CollisionInfo collisionInfo = null;

        /** Used to check name collision. */
        private final NameCollisionChecker cchecker = new NameCollisionChecker();

        /**
         * @see DefaultParticleBinder#build(XSParticle, Collection<com.sun.xml.internal.xsom.XSParticle>)
         */
        private final Collection<XSParticle> forcedProps;

        public void particle( XSParticle p ) {

            if(getLocalPropCustomization(p)!=null
            || builder.getLocalDomCustomization(p)!=null) {
                // if a property customization is specfied,
                // check that value and turn around.
                check(p);
                mark(p);
                return;
            }

            XSTerm t = p.getTerm();

            if(p.isRepeated() && (t.isModelGroup() || t.isModelGroupDecl())) {
                // a repeated model group gets its own property
                mark(p);
                return;
            }

            if(forcedProps.contains(p)) {
                // this particle must become a property
                mark(p);
                return;
            }

            outerParticle = p;
            t.visit(this);
        }

        /**
         * This field points to the parent XSParticle.
         * The value is only valid when we are processing XSTerm.
         */
        private XSParticle outerParticle;

        public void elementDecl(XSElementDecl decl) {
            check(outerParticle);
            mark(outerParticle);
        }

        public void modelGroup(XSModelGroup mg) {
            // choice gets mapped to a property
            if(mg.getCompositor()== XSModelGroup.Compositor.CHOICE && builder.getGlobalBinding().isChoiceContentPropertyEnabled()) {
                mark(outerParticle);
                return;
            }

            for( XSParticle child : mg.getChildren() )
                particle(child);
        }

        public void modelGroupDecl(XSModelGroupDecl decl) {
            modelGroup(decl.getModelGroup());
        }

        public void wildcard(XSWildcard wc) {
            mark(outerParticle);
        }

        void readSuperClass( CClassInfo ci ) {
            cchecker.readSuperClass(ci);
        }




        /**
         * Checks the name collision of a newly found particle.
         */
        private void check( XSParticle p ) {
            if( collisionInfo==null )
                collisionInfo = cchecker.check(p);
        }

        /**
         * Marks a particle that it's going to be mapped to a property.
         */
        private void mark( XSParticle p ) {
            markedParticles.put(p,computeLabel(p));
        }

        /**
         * Marked particles.
         *
         * A map from XSParticle to its label.
         */
        public final Map<XSParticle,String> markedParticles = new HashMap<XSParticle,String>();


        /**
         * Checks name collisions among particles that belong to sequences.
         */
        private final class NameCollisionChecker {

            /**
             * Checks the label conflict of a particle.
             * This method shall be called for each marked particle.
             *
             * @return
             *      a description of a collision if a name collision is
             *      found. Otherwise null.
             */
            CollisionInfo check( XSParticle p ) {
                // this can be used for particles with a property customization,
                // which may not have element declaration as its term.
//                // we only check particles with element declarations.
//                _assert( p.getTerm().isElementDecl() );

                String label = computeLabel(p);
                if( occupiedLabels.containsKey(label) ) {
                    // collide with occupied labels
                    return new CollisionInfo(label,p.getLocator(),
                            occupiedLabels.get(label).locator);
                }

                for( XSParticle jp : particles ) {
                    if(!check( p, jp )) {
                        // problem was found. no need to check further
                        return new CollisionInfo( label, p.getLocator(), jp.getLocator() );
                    }
                }
                particles.add(p);
                return null;
            }

            /** List of particles reported through the check method. */
            private final List<XSParticle> particles = new ArrayList<XSParticle>();

            /**
             * Label names already used in the base type.
             */
            private final Map<String,CPropertyInfo> occupiedLabels = new HashMap<String,CPropertyInfo>();

            /**
             * Checks the conflict of two particles.
             * @return
             *      true if the check was successful.
             */
            private boolean check( XSParticle p1, XSParticle p2 ) {
                return !computeLabel(p1).equals(computeLabel(p2));
            }

            /**
             * Reads fields of the super class and includes them
             * to name collision tests.
             */
            void readSuperClass( CClassInfo base ) {
                for( ; base!=null; base=base.getBaseClass() ) {
                    for( CPropertyInfo p : base.getProperties() )
                        occupiedLabels.put(p.getName(true),p);
                }
            }
        }





        /** Keep the computed label names for particles. */
        private final Map<XSParticle,String> labelCache = new Hashtable<XSParticle,String>();

        /**
         * Hides the computeLabel method of the outer class
         * and adds caching.
         */
        private String computeLabel( XSParticle p ) {
            String label = labelCache.get(p);
            if(label==null)
                labelCache.put( p, label=DefaultParticleBinder.this.computeLabel(p) );
            return label;
        }
    }












    /**
     * Builds properties by using the result computed by Checker
     */
    private final class Builder implements XSTermVisitor {
        Builder( Map<XSParticle,String> markedParticles ) {
            this.markedParticles = markedParticles;
        }

        /** All marked particles. */
        private final Map<XSParticle,String/*label*/> markedParticles;

        /**
         * When we are visiting inside an optional particle, this flag
         * is set to true.
         *
         * <p>
         * This allows us to correctly generate primitive/boxed types.
         */
        private boolean insideOptionalParticle;


        /** Returns true if a particle is marked. */
        private boolean marked( XSParticle p ) {
            return markedParticles.containsKey(p);
        }
        /** Gets a label of a particle. */
        private String getLabel( XSParticle p ) {
            return markedParticles.get(p);
        }

        public void particle( XSParticle p ) {
            XSTerm t = p.getTerm();

            if(marked(p)) {
                BIProperty cust = BIProperty.getCustomization(p);
                CPropertyInfo prop = cust.createElementOrReferenceProperty(
                    getLabel(p), false, p, RawTypeSetBuilder.build(p,insideOptionalParticle));
                getCurrentBean().addProperty(prop);
            } else {
                // repeated model groups should have been marked already
                assert !p.isRepeated();

                boolean oldIOP = insideOptionalParticle;
                insideOptionalParticle |= BigInteger.ZERO.equals(p.getMinOccurs());
                // this is an unmarked particle
                t.visit(this);
                insideOptionalParticle = oldIOP;
            }
        }

        public void elementDecl( XSElementDecl e ) {
            // because the corresponding particle must be marked.
            assert false;
        }

        public void wildcard( XSWildcard wc ) {
            // because the corresponding particle must be marked.
            assert false;
        }

        public void modelGroupDecl( XSModelGroupDecl decl ) {
            modelGroup(decl.getModelGroup());
        }

        public void modelGroup( XSModelGroup mg ) {
            boolean oldIOP = insideOptionalParticle;
            insideOptionalParticle |= mg.getCompositor()==XSModelGroup.CHOICE;

            for( XSParticle p : mg.getChildren())
                particle(p);

            insideOptionalParticle = oldIOP;
        }
    }
}
