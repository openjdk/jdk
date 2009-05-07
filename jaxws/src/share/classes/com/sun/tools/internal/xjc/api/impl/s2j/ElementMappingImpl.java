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
package com.sun.tools.internal.xjc.api.impl.s2j;

import java.util.List;

import com.sun.tools.internal.xjc.api.Property;
import com.sun.tools.internal.xjc.api.TypeAndAnnotation;
import com.sun.tools.internal.xjc.model.CAdapter;
import com.sun.tools.internal.xjc.model.CClassInfo;
import com.sun.tools.internal.xjc.model.CElementInfo;
import com.sun.tools.internal.xjc.model.CElementPropertyInfo;
import com.sun.tools.internal.xjc.model.CTypeInfo;
import com.sun.tools.internal.xjc.model.TypeUse;
import com.sun.tools.internal.xjc.model.TypeUseFactory;

/**
 * @author Kohsuke Kawaguchi
 */
final class ElementMappingImpl extends AbstractMappingImpl<CElementInfo> {

    private final TypeAndAnnotation taa;

    protected ElementMappingImpl(JAXBModelImpl parent, CElementInfo elementInfo) {
        super(parent,elementInfo);

        TypeUse t = clazz.getContentType();
        if(clazz.getProperty().isCollection())
            t = TypeUseFactory.makeCollection(t);
        CAdapter a = clazz.getProperty().getAdapter();
        if(a!=null)
            t = TypeUseFactory.adapt(t,a);
        taa = new TypeAndAnnotationImpl(parent.outline,t);
    }

    public TypeAndAnnotation getType() {
        return taa;
    }

    public final List<Property> calcDrilldown() {
        CElementPropertyInfo p = clazz.getProperty();

        if(p.getAdapter()!=null)
            return null;    // if adapted, avoid drill down

        if(p.isCollection())
        // things like <xs:element name="foo" type="xs:NMTOKENS" /> is not eligible.
            return null;

        CTypeInfo typeClass = p.ref().get(0);

        if(!(typeClass instanceof CClassInfo))
            // things like <xs:element name="foo" type="xs:string" /> is not eligible.
            return null;

        CClassInfo ci = (CClassInfo)typeClass;

        // if the type is abstract we can't use it.
        if(ci.isAbstract())
            return null;

        // the 'all' compositor doesn't qualify
        if(!ci.isOrdered())
            return null;

        return buildDrilldown(ci);
    }
}
