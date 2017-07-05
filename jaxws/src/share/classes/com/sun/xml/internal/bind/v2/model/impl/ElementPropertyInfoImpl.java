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

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlList;
import javax.xml.namespace.QName;

import com.sun.istack.internal.FinalArrayList;
import com.sun.xml.internal.bind.v2.model.core.ElementPropertyInfo;
import com.sun.xml.internal.bind.v2.model.core.ID;
import com.sun.xml.internal.bind.v2.model.core.PropertyKind;
import com.sun.xml.internal.bind.v2.model.core.TypeInfo;
import com.sun.xml.internal.bind.v2.model.core.TypeRef;
import com.sun.xml.internal.bind.v2.runtime.IllegalAnnotationException;

/**
 * Common {@link ElementPropertyInfo} implementation used for both
 * APT and runtime.
 *
 * @author Kohsuke Kawaguchi
 */
class ElementPropertyInfoImpl<TypeT,ClassDeclT,FieldT,MethodT>
    extends ERPropertyInfoImpl<TypeT,ClassDeclT,FieldT,MethodT>
    implements ElementPropertyInfo<TypeT,ClassDeclT>
{
    /**
     * Lazily computed.
     * @see #getTypes()
     */
    private List<TypeRefImpl<TypeT,ClassDeclT>> types;

    private final List<TypeInfo<TypeT,ClassDeclT>> ref = new AbstractList<TypeInfo<TypeT,ClassDeclT>>() {
        public TypeInfo<TypeT,ClassDeclT> get(int index) {
            return getTypes().get(index).getTarget();
        }

        public int size() {
            return getTypes().size();
        }
    };

    /**
     * Lazily computed.
     * @see #isRequired()
     */
    private Boolean isRequired;

    /**
     * @see #isValueList()
     */
    private final boolean isValueList;

    ElementPropertyInfoImpl(
        ClassInfoImpl<TypeT,ClassDeclT,FieldT,MethodT> parent,
        PropertySeed<TypeT,ClassDeclT,FieldT,MethodT> propertySeed) {
        super(parent, propertySeed);

        isValueList = seed.hasAnnotation(XmlList.class);

    }

    public List<? extends TypeRefImpl<TypeT,ClassDeclT>> getTypes() {
        if(types==null) {
            types = new FinalArrayList<TypeRefImpl<TypeT,ClassDeclT>>();
            XmlElement[] ann=null;

            XmlElement xe = seed.readAnnotation(XmlElement.class);
            XmlElements xes = seed.readAnnotation(XmlElements.class);

            if(xe!=null && xes!=null) {
                parent.builder.reportError(new IllegalAnnotationException(
                        Messages.MUTUALLY_EXCLUSIVE_ANNOTATIONS.format(
                                nav().getClassName(parent.getClazz())+'#'+seed.getName(),
                                xe.annotationType().getName(), xes.annotationType().getName()),
                        xe, xes ));
            }

            isRequired = true;

            if(xe!=null)
                ann = new XmlElement[]{xe};
            else
            if(xes!=null)
                ann = xes.value();

            if(ann==null) {
                // default
                TypeT t = getIndividualType();
                if(!nav().isPrimitive(t) || isCollection())
                    isRequired = false;
                // nillableness defaults to true if it's collection
                types.add(createTypeRef(calcXmlName((XmlElement)null),t,isCollection(),null));
            } else {
                for( XmlElement item : ann ) {
                    // TODO: handle defaulting in names.
                    QName name = calcXmlName(item);
                    TypeT type = reader().getClassValue(item, "type");
                    if(type.equals(nav().ref(XmlElement.DEFAULT.class))) type = getIndividualType();
                    if((!nav().isPrimitive(type) || isCollection()) && !item.required())
                        isRequired = false;
                    types.add(createTypeRef(name, type, item.nillable(), getDefaultValue(item.defaultValue()) ));
                }
            }
            types = Collections.unmodifiableList(types);
            assert !types.contains(null);
        }
        return types;
    }

    private String getDefaultValue(String value) {
        if(value.equals("\u0000"))
            return null;
        else
            return value;
    }

    /**
     * Used by {@link PropertyInfoImpl} to create new instances of {@link TypeRef}
     */
    protected TypeRefImpl<TypeT,ClassDeclT> createTypeRef(QName name,TypeT type,boolean isNillable,String defaultValue) {
        return new TypeRefImpl<TypeT,ClassDeclT>(this,name,type,isNillable,defaultValue);
    }

    public boolean isValueList() {
        return isValueList;
    }

    public boolean isRequired() {
        if(isRequired==null)
            getTypes(); // compute the value
        return isRequired;
    }

    public List<? extends TypeInfo<TypeT,ClassDeclT>> ref() {
        return ref;
    }

    public final PropertyKind kind() {
        return PropertyKind.ELEMENT;
    }

    protected void link() {
        super.link();
        for (TypeRefImpl<TypeT, ClassDeclT> ref : getTypes() ) {
            ref.link();
        }

        if(isValueList()) {
            // ugly test, because IDREF's are represented as text on the wire,
            // it's OK to be a value list in that case.
            if(id()!= ID.IDREF) {
                // check if all the item types are simple types
                // this can't be done when we compute types because
                // not all TypeInfos are available yet
                for (TypeRefImpl<TypeT,ClassDeclT> ref : types) {
                    if(!ref.getTarget().isSimpleType()) {
                        parent.builder.reportError(new IllegalAnnotationException(
                        Messages.XMLLIST_NEEDS_SIMPLETYPE.format(
                            nav().getTypeName(ref.getTarget().getType())), this ));
                        break;
                    }
                }
            }

            if(!isCollection())
                parent.builder.reportError(new IllegalAnnotationException(
                    Messages.XMLLIST_ON_SINGLE_PROPERTY.format(), this
                ));
        }
    }
}
