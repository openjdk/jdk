/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlList;

import com.sun.xml.internal.bind.v2.model.core.ID;
import com.sun.xml.internal.bind.v2.model.core.NonElement;
import com.sun.xml.internal.bind.v2.model.core.PropertyInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeNonElementRef;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimePropertyInfo;
import com.sun.xml.internal.bind.v2.runtime.IllegalAnnotationException;
import com.sun.xml.internal.bind.v2.runtime.Transducer;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;

/**
 * {@link PropertyInfoImpl} that can only have one type.
 *
 * Specifically, {@link AttributePropertyInfoImpl} and {@link ValuePropertyInfoImpl}.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class SingleTypePropertyInfoImpl<T,C,F,M>
    extends PropertyInfoImpl<T,C,F,M> {

    /**
     * Computed lazily.
     *
     * @see {@link #getTarget()}.
     */
    private NonElement<T,C> type;

    public SingleTypePropertyInfoImpl(ClassInfoImpl<T,C,F,M> classInfo, PropertySeed<T,C,F,M> seed) {
        super(classInfo, seed);
        if(this instanceof RuntimePropertyInfo) {
            Accessor rawAcc = ((RuntimeClassInfoImpl.RuntimePropertySeed)seed).getAccessor();
            if(getAdapter()!=null && !isCollection())
                // adapter for a single-value property is handled by accessor.
                // adapter for a collection property is handled by lister.
                rawAcc = rawAcc.adapt(((RuntimePropertyInfo)this).getAdapter());
            this.acc = rawAcc;
        } else
            this.acc = null;
    }

    public List<? extends NonElement<T,C>> ref() {
        return Collections.singletonList(getTarget());
    }

    public NonElement<T,C> getTarget() {
        if(type==null) {
            assert parent.builder!=null : "this method must be called during the build stage";
            type = parent.builder.getTypeInfo(getIndividualType(),this);
        }
        return type;
    }

    public PropertyInfo<T,C> getSource() {
        return this;
    }

    public void link() {
        super.link();

        if (!(NonElement.ANYTYPE_NAME.equals(type.getTypeName()) || type.isSimpleType() || id()==ID.IDREF)) {
                parent.builder.reportError(new IllegalAnnotationException(
                Messages.SIMPLE_TYPE_IS_REQUIRED.format(),
                seed
            ));
        }

        if(!isCollection() && seed.hasAnnotation(XmlList.class)) {
            parent.builder.reportError(new IllegalAnnotationException(
                Messages.XMLLIST_ON_SINGLE_PROPERTY.format(), this
            ));
        }
    }

//
//
// technically these code belong to runtime implementation, but moving the code up here
// allows this to be shared between RuntimeValuePropertyInfoImpl and RuntimeAttributePropertyInfoImpl
//
//

    private final Accessor acc;
    /**
     * Lazily created.
     */
    private Transducer xducer;

    public Accessor getAccessor() {
        return acc;
    }


    public Transducer getTransducer() {
        if(xducer==null) {
            xducer = RuntimeModelBuilder.createTransducer((RuntimeNonElementRef)this);
            if(xducer==null) {
                // this situation is checked by by the link method.
                // avoid repeating the same error by silently recovering
                xducer = RuntimeBuiltinLeafInfoImpl.STRING;
            }
        }
        return xducer;
    }
}
