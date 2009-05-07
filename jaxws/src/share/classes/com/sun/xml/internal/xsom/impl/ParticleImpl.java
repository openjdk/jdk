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
package com.sun.xml.internal.xsom.impl;

import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSTerm;
import com.sun.xml.internal.xsom.impl.parser.DelayedRef;
import com.sun.xml.internal.xsom.impl.parser.SchemaDocumentImpl;
import com.sun.xml.internal.xsom.parser.SchemaDocument;
import com.sun.xml.internal.xsom.visitor.XSContentTypeFunction;
import com.sun.xml.internal.xsom.visitor.XSContentTypeVisitor;
import com.sun.xml.internal.xsom.visitor.XSFunction;
import com.sun.xml.internal.xsom.visitor.XSVisitor;
import org.xml.sax.Locator;

import java.util.List;

public class ParticleImpl extends ComponentImpl implements XSParticle, ContentTypeImpl
{
    public ParticleImpl( SchemaDocumentImpl owner, AnnotationImpl _ann,
        Ref.Term _term, Locator _loc, int _maxOccurs, int _minOccurs ) {

        super(owner,_ann,_loc,null);
        this.term = _term;
        this.maxOccurs = _maxOccurs;
        this.minOccurs = _minOccurs;
    }
    public ParticleImpl( SchemaDocumentImpl owner, AnnotationImpl _ann, Ref.Term _term, Locator _loc ) {
        this(owner,_ann,_term,_loc,1,1);
    }

    private Ref.Term term;
    public XSTerm getTerm() { return term.getTerm(); }

    private int maxOccurs;
    public int getMaxOccurs() { return maxOccurs; }

    public boolean isRepeated() {
        return maxOccurs!=0 && maxOccurs!=1;
    }

    private int minOccurs;
    public int getMinOccurs() { return minOccurs; }


    public void redefine(ModelGroupDeclImpl oldMG) {
        if( term instanceof ModelGroupImpl ) {
            ((ModelGroupImpl)term).redefine(oldMG);
            return;
        }
        if( term instanceof DelayedRef.ModelGroup ) {
            ((DelayedRef)term).redefine(oldMG);
        }
    }


    public XSSimpleType asSimpleType()  { return null; }
    public XSParticle asParticle()      { return this; }
    public XSContentType asEmpty()      { return null; }


    public final Object apply( XSFunction function ) {
        return function.particle(this);
    }
    public final Object apply( XSContentTypeFunction function ) {
        return function.particle(this);
    }
    public final void visit( XSVisitor visitor ) {
        visitor.particle(this);
    }
    public final void visit( XSContentTypeVisitor visitor ) {
        visitor.particle(this);
    }

    // Ref.ContentType implementation
    public XSContentType getContentType() { return this; }

    /**
     * Foreign attribuets are considered to be on terms.
     *
     * REVISIT: is this a good design?
     */
    public List getForeignAttributes() {
        return getTerm().getForeignAttributes();
    }
}
