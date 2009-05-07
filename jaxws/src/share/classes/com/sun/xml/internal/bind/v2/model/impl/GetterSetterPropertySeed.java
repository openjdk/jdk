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
import java.beans.Introspector;

import com.sun.xml.internal.bind.v2.model.annotation.Locatable;
import com.sun.xml.internal.bind.v2.model.core.PropertyInfo;
import com.sun.xml.internal.bind.v2.runtime.Location;

/**
 * {@link PropertyInfo} implementation backed by a getter and a setter.
 *
 * We allow the getter or setter to be null, in which case the bean
 * can only participate in unmarshalling (or marshalling)
 */
class GetterSetterPropertySeed<TypeT,ClassDeclT,FieldT,MethodT> implements
        PropertySeed<TypeT,ClassDeclT,FieldT,MethodT> {

    protected final MethodT getter;
    protected final MethodT setter;
    private ClassInfoImpl<TypeT,ClassDeclT,FieldT,MethodT> parent;

    GetterSetterPropertySeed(ClassInfoImpl<TypeT,ClassDeclT,FieldT,MethodT> parent, MethodT getter, MethodT setter) {
        this.parent = parent;
        this.getter = getter;
        this.setter = setter;

        if(getter==null && setter==null)
            throw new IllegalArgumentException();
    }

    public TypeT getRawType() {
        if(getter!=null)
            return parent.nav().getReturnType(getter);
        else
            return parent.nav().getMethodParameters(setter)[0];
    }

    public <A extends Annotation> A readAnnotation(Class<A> annotation) {
        return parent.reader().getMethodAnnotation(annotation, getter,setter,this);
    }

    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return parent.reader().hasMethodAnnotation(annotationType,getName(),getter,setter,this);
    }

    public String getName() {
        if(getter!=null)
            return getName(getter);
        else
            return getName(setter);
    }

    private String getName(MethodT m) {
        String seed = parent.nav().getMethodName(m);
        String lseed = seed.toLowerCase();
        if(lseed.startsWith("get") || lseed.startsWith("set"))
            return camelize(seed.substring(3));
        if(lseed.startsWith("is"))
            return camelize(seed.substring(2));
        return seed;
    }


    private static String camelize(String s) {
        return Introspector.decapitalize(s);
    }

    /**
     * Use the enclosing class as the upsream {@link Location}.
     */
    public Locatable getUpstream() {
        return parent;
    }

    public Location getLocation() {
        if(getter!=null)
            return parent.nav().getMethodLocation(getter);
        else
            return parent.nav().getMethodLocation(setter);
    }
}
