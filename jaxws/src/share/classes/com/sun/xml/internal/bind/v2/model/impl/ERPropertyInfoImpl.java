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

import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.v2.runtime.IllegalAnnotationException;

/**
 * Common part of {@link ElementPropertyInfoImpl} and {@link ReferencePropertyInfoImpl}.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class ERPropertyInfoImpl<TypeT,ClassDeclT,FieldT,MethodT>
    extends PropertyInfoImpl<TypeT,ClassDeclT,FieldT,MethodT> {

    public ERPropertyInfoImpl(ClassInfoImpl<TypeT, ClassDeclT, FieldT, MethodT> classInfo, PropertySeed<TypeT, ClassDeclT, FieldT, MethodT> propertySeed) {
        super(classInfo, propertySeed);

        XmlElementWrapper e = seed.readAnnotation(XmlElementWrapper.class);

        boolean nil = false;
        boolean required = false;
        if(!isCollection()) {
            xmlName = null;
            if(e!=null)
                classInfo.builder.reportError(new IllegalAnnotationException(
                    Messages.XML_ELEMENT_WRAPPER_ON_NON_COLLECTION.format(
                        nav().getClassName(parent.getClazz())+'.'+seed.getName()),
                    e
                ));
        } else {
            if(e!=null) {
                xmlName = calcXmlName(e);
                nil = e.nillable();
                required = e.required();
            } else
                xmlName = null;
        }

        wrapperNillable = nil;
        wrapperRequired = required;
    }

    private final QName xmlName;

    /**
     * True if the wrapper tag name is nillable.
     */
    private final boolean wrapperNillable;

    /**
     * True if the wrapper tag is required.
     */
    private final boolean wrapperRequired;

    /**
     * Gets the wrapper element name.
     */
    public final QName getXmlName() {
        return xmlName;
    }

    public final boolean isCollectionNillable() {
        return wrapperNillable;
    }

    public final boolean isCollectionRequired() {
        return wrapperRequired;
    }
}
