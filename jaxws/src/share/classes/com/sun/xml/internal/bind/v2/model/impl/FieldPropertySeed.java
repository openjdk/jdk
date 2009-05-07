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
package com.sun.xml.internal.bind.v2.model.impl;

import java.lang.annotation.Annotation;

import com.sun.xml.internal.bind.v2.model.annotation.Locatable;
import com.sun.xml.internal.bind.v2.model.core.PropertyInfo;
import com.sun.xml.internal.bind.v2.runtime.Location;

/**
 * {@link PropertyInfo} implementation backed by a field.
 */
class FieldPropertySeed<TypeT,ClassDeclT,FieldT,MethodT> implements
        PropertySeed<TypeT,ClassDeclT,FieldT,MethodT> {

    protected final FieldT field;
    private ClassInfoImpl<TypeT,ClassDeclT,FieldT,MethodT> parent;

    FieldPropertySeed(ClassInfoImpl<TypeT,ClassDeclT,FieldT,MethodT> classInfo, FieldT field) {
        this.parent = classInfo;
        this.field = field;
    }

    public <A extends Annotation> A readAnnotation(Class<A> a) {
        return parent.reader().getFieldAnnotation(a, field,this);
    }

    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return parent.reader().hasFieldAnnotation(annotationType,field);
    }

    public String getName() {
        // according to the spec team, the BeanIntrospector.decapitalize does not apply
        // to the fields. Don't call Introspector.decapitalize
        return parent.nav().getFieldName(field);
    }

    public TypeT getRawType() {
        return parent.nav().getFieldType(field);
    }

    /**
     * Use the enclosing class as the upsream {@link Location}.
     */
    public Locatable getUpstream() {
        return parent;
    }

    public Location getLocation() {
        return parent.nav().getFieldLocation(field);
    }
}
