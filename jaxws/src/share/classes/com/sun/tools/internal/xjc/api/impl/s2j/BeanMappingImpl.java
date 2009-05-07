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

import com.sun.tools.internal.xjc.api.Mapping;
import com.sun.tools.internal.xjc.api.Property;
import com.sun.tools.internal.xjc.api.TypeAndAnnotation;
import com.sun.tools.internal.xjc.model.CClassInfo;

/**
 * Partial implementation of {@link Mapping}
 * for bean classes.
 *
 * @author Kohsuke Kawaguchi
 */
final class BeanMappingImpl extends AbstractMappingImpl<CClassInfo> {

    private final TypeAndAnnotationImpl taa = new TypeAndAnnotationImpl(parent.outline,clazz);

    BeanMappingImpl(JAXBModelImpl parent, CClassInfo classInfo) {
        super(parent,classInfo);
        assert classInfo.isElement();
    }

    public TypeAndAnnotation getType() {
        return taa;
    }

    public final String getTypeClass() {
        return getClazz();
    }

    public List<Property> calcDrilldown() {
        if(!clazz.isOrdered())
            return null;    // all is not eligible for the wrapper style
        return buildDrilldown(clazz);
    }
}
