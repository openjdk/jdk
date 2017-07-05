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

package com.sun.tools.internal.xjc.reader.relaxng;

import javax.xml.namespace.QName;

import com.sun.tools.internal.xjc.model.CAttributePropertyInfo;
import com.sun.tools.internal.xjc.model.CClassInfo;
import com.sun.tools.internal.xjc.model.CElementPropertyInfo;
import com.sun.tools.internal.xjc.model.CReferencePropertyInfo;
import com.sun.tools.internal.xjc.model.Multiplicity;
import com.sun.tools.internal.xjc.reader.RawTypeSet;
import com.sun.xml.internal.bind.v2.model.core.ID;

import com.sun.xml.internal.rngom.digested.DAttributePattern;
import com.sun.xml.internal.rngom.digested.DChoicePattern;
import com.sun.xml.internal.rngom.digested.DMixedPattern;
import com.sun.xml.internal.rngom.digested.DOneOrMorePattern;
import com.sun.xml.internal.rngom.digested.DOptionalPattern;
import com.sun.xml.internal.rngom.digested.DPattern;
import com.sun.xml.internal.rngom.digested.DPatternWalker;
import com.sun.xml.internal.rngom.digested.DZeroOrMorePattern;

import static com.sun.tools.internal.xjc.model.CElementPropertyInfo.CollectionMode.REPEATED_ELEMENT;

/**
 * Recursively visits {@link DPattern} and
 * decides which patterns to map to properties.
 *
 * @author Kohsuke Kawaguchi
 */
final class ContentModelBinder extends DPatternWalker {
    private final RELAXNGCompiler compiler;
    private final CClassInfo clazz;

    private boolean insideOptional = false;
    private int iota=1;

    public ContentModelBinder(RELAXNGCompiler compiler,CClassInfo clazz) {
        this.compiler = compiler;
        this.clazz = clazz;
    }

    public Void onMixed(DMixedPattern p) {
        throw new UnsupportedOperationException();
    }

    public Void onChoice(DChoicePattern p) {
        boolean old = insideOptional;
        insideOptional = true;
        super.onChoice(p);
        insideOptional = old;
        return null;
    }

    public Void onOptional(DOptionalPattern p) {
        boolean old = insideOptional;
        insideOptional = true;
        super.onOptional(p);
        insideOptional = old;
        return null;
    }

    public Void onZeroOrMore(DZeroOrMorePattern p) {
        return onRepeated(p,true);
    }

    public Void onOneOrMore(DOneOrMorePattern p) {
        return onRepeated(p,insideOptional);

    }

    private Void onRepeated(DPattern p,boolean optional) {
        RawTypeSet rts = RawTypeSetBuilder.build(compiler, p, optional? Multiplicity.STAR : Multiplicity.PLUS);
        if(rts.canBeTypeRefs==RawTypeSet.Mode.SHOULD_BE_TYPEREF) {
            CElementPropertyInfo prop = new CElementPropertyInfo(
                    calcName(p),REPEATED_ELEMENT,ID.NONE,null,null,null,p.getLocation(),!optional);
            rts.addTo(prop);
            clazz.addProperty(prop);
        } else {
            CReferencePropertyInfo prop = new CReferencePropertyInfo(
                    calcName(p),true,!optional,false/*TODO*/,null,null,p.getLocation(), false, false, false);
            rts.addTo(prop);
            clazz.addProperty(prop);
        }

        return null;
    }

    public Void onAttribute(DAttributePattern p) {
        // TODO: support multiple names
        QName name = p.getName().listNames().iterator().next();

        CAttributePropertyInfo ap = new CAttributePropertyInfo(
           calcName(p), null,null/*TODO*/, p.getLocation(), name,
                p.getChild().accept(compiler.typeUseBinder), null,
                !insideOptional);
        clazz.addProperty(ap);

        return null;
    }

    private String calcName(DPattern p) {
        // TODO
        return "field"+(iota++);
    }
}
