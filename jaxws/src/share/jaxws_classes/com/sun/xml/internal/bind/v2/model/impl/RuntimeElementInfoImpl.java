/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.sun.xml.internal.bind.v2.model.core.Adapter;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeClassInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeElementInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeElementPropertyInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeNonElement;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimePropertyInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeTypeRef;
import com.sun.xml.internal.bind.v2.runtime.IllegalAnnotationException;
import com.sun.xml.internal.bind.v2.runtime.Transducer;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;

/**
 * @author Kohsuke Kawaguchi
 */
final class RuntimeElementInfoImpl extends ElementInfoImpl<Type,Class,Field,Method>
    implements RuntimeElementInfo {

    public RuntimeElementInfoImpl(RuntimeModelBuilder modelBuilder, RegistryInfoImpl registry, Method method) throws IllegalAnnotationException {
        super(modelBuilder, registry, method);

        Adapter<Type,Class> a = getProperty().getAdapter();

        if(a!=null)
            adapterType = a.adapterType;
        else
            adapterType = null;
    }

    @Override
    protected PropertyImpl createPropertyImpl() {
        return new RuntimePropertyImpl();
    }

    class RuntimePropertyImpl extends PropertyImpl implements RuntimeElementPropertyInfo, RuntimeTypeRef {
        public Accessor getAccessor() {
            if(adapterType==null)
                return Accessor.JAXB_ELEMENT_VALUE;
            else
                return Accessor.JAXB_ELEMENT_VALUE.adapt(
                        (Class)getAdapter().defaultType,(Class)adapterType);
        }

        public Type getRawType() {
            return Collection.class;
        }

        public Type getIndividualType() {
             return getContentType().getType();
        }


        public boolean elementOnlyContent() {
            return false;   // this method doesn't make sense here
        }

        public List<? extends RuntimeTypeRef> getTypes() {
            return Collections.singletonList(this);
        }

        public List<? extends RuntimeNonElement> ref() {
            return (List<? extends RuntimeNonElement>)super.ref();
        }

        public RuntimeNonElement getTarget() {
            return (RuntimeNonElement)super.getTarget();
        }

        public RuntimePropertyInfo getSource() {
            return this;
        }

        public Transducer getTransducer() {
            return RuntimeModelBuilder.createTransducer(this);
        }
    }

    /**
     * The adapter specified by <code>getProperty().getAdapter()</code>.
     */
    private final Class<? extends XmlAdapter> adapterType;

    public RuntimeElementPropertyInfo getProperty() {
        return (RuntimeElementPropertyInfo)super.getProperty();
    }

    public Class<? extends JAXBElement> getType() {
        //noinspection unchecked
        return (Class<? extends JAXBElement>) Utils.REFLECTION_NAVIGATOR.erasure(super.getType());
    }

    public RuntimeClassInfo getScope() {
        return (RuntimeClassInfo)super.getScope();
    }

    public RuntimeNonElement getContentType() {
        return (RuntimeNonElement)super.getContentType();
    }
}
