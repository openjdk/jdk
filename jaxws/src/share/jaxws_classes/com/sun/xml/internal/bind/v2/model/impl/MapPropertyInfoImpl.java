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

package com.sun.xml.internal.bind.v2.model.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.v2.model.core.MapPropertyInfo;
import com.sun.xml.internal.bind.v2.model.core.NonElement;
import com.sun.xml.internal.bind.v2.model.core.PropertyKind;
import com.sun.xml.internal.bind.v2.model.core.TypeInfo;

/**
 * @author Kohsuke Kawaguchi
 */
class MapPropertyInfoImpl<T,C,F,M> extends PropertyInfoImpl<T,C,F,M> implements MapPropertyInfo<T,C> {

    private final QName xmlName;
    private boolean nil;
    private final T keyType;
    private final T valueType;

    // laziy computed to handle cyclic references
    private NonElement<T,C> keyTypeInfo;
    private NonElement<T,C> valueTypeInfo;


    public MapPropertyInfoImpl(ClassInfoImpl<T,C,F,M> ci, PropertySeed<T,C,F,M> seed) {
        super(ci, seed);

        XmlElementWrapper xe = seed.readAnnotation(XmlElementWrapper.class);
        xmlName = calcXmlName(xe);
        nil = xe!=null && xe.nillable();

        T raw = getRawType();
        T bt = nav().getBaseClass(raw, nav().asDecl(Map.class) );
        assert bt!=null;    // Map property is only for Maps

        if(nav().isParameterizedType(bt)) {
            keyType = nav().getTypeArgument(bt,0);
            valueType = nav().getTypeArgument(bt,1);
        } else {
            keyType = valueType = nav().ref(Object.class);
        }
    }

    public Collection<? extends TypeInfo<T,C>> ref() {
        return Arrays.asList(getKeyType(),getValueType());
    }

    public final PropertyKind kind() {
        return PropertyKind.MAP;
    }

    public QName getXmlName() {
        return xmlName;
    }

    public boolean isCollectionNillable() {
        return nil;
    }

    public NonElement<T,C> getKeyType() {
        if(keyTypeInfo==null)
            keyTypeInfo = getTarget(keyType);
        return keyTypeInfo;
    }

    public NonElement<T,C> getValueType() {
        if(valueTypeInfo==null)
            valueTypeInfo = getTarget(valueType);
        return valueTypeInfo;
    }

    public NonElement<T,C> getTarget(T type) {
        assert parent.builder!=null : "this method must be called during the build stage";
        return parent.builder.getTypeInfo(type,this);
    }
}
