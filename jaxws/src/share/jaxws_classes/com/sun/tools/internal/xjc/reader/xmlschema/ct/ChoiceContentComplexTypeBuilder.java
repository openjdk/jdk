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

package com.sun.tools.internal.xjc.reader.xmlschema.ct;

import java.util.Collections;

import static com.sun.tools.internal.xjc.reader.xmlschema.ct.ComplexTypeBindingMode.NORMAL;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSParticle;

/**
 * Binds a complex type whose immediate child is a choice
 * model group to a choice content interface.
 *
 * @author Kohsuke Kawaguchi
 */
final class ChoiceContentComplexTypeBuilder extends CTBuilder {

    public boolean isApplicable(XSComplexType ct) {
        if( !bgmBuilder.getGlobalBinding().isChoiceContentPropertyEnabled() )
            return false;

        if( ct.getBaseType()!=schemas.getAnyType() )
            // My reading of the spec is that if a complex type is
            // derived from another complex type by extension,
            // its top level model group is always a sequence
            // that combines the base type content model and
            // the extension defined in the new complex type.
            return false;

        XSParticle p = ct.getContentType().asParticle();
        if(p==null)
            return false;

        XSModelGroup mg = getTopLevelModelGroup(p);

        if( mg.getCompositor()!=XSModelGroup.CHOICE )
            return false;

        if( p.isRepeated() )
            return false;

        return true;
    }



    private XSModelGroup getTopLevelModelGroup(XSParticle p) {
        XSModelGroup mg = p.getTerm().asModelGroup();
        if( p.getTerm().isModelGroupDecl() )
            mg = p.getTerm().asModelGroupDecl().getModelGroup();
        return mg;
    }

    public void build(XSComplexType ct) {
        XSParticle p = ct.getContentType().asParticle();

        builder.recordBindingMode(ct,NORMAL);

        bgmBuilder.getParticleBinder().build(p,Collections.singleton(p));

        green.attContainer(ct);
    }


}
