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
package com.sun.tools.internal.xjc.model;

import javax.xml.namespace.QName;

import com.sun.tools.internal.xjc.model.nav.NClass;
import com.sun.tools.internal.xjc.model.nav.NType;
import com.sun.xml.internal.bind.v2.model.core.AttributePropertyInfo;
import com.sun.xml.internal.bind.v2.model.core.PropertyKind;
import com.sun.xml.internal.xsom.XSComponent;
import com.sun.istack.internal.Nullable;

import org.xml.sax.Locator;

/**
 * {@link AttributePropertyInfo} for the compiler.
 *
 * @author Kohsuke Kawaguchi
 */
public final class CAttributePropertyInfo extends CSingleTypePropertyInfo implements AttributePropertyInfo<NType,NClass> {

    private final QName attName;
    private final boolean isRequired;

    /**
     * @param type
     *      Represents the bound type of this attribute.
     * @param typeName
     *      XML Schema type name of this attribute. Optional for other schema languages.
     */
    public CAttributePropertyInfo(String name, XSComponent source, CCustomizations customizations,
                                  Locator locator, QName attName, TypeUse type, @Nullable QName typeName,
                                  boolean required ) {
        super(name, type, typeName, source, customizations, locator);
        isRequired = required;
        this.attName = attName;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public QName getXmlName() {
        return attName;
    }

    /**
     * An optional attribute can never be unboxable,
     * for we need null to represent the absence.
     */
    public boolean isUnboxable() {
        if(!isRequired) return false;
        return super.isUnboxable();
    }

    @Override
    public boolean isOptionalPrimitive() {
        return !isRequired && super.isUnboxable();
    }

    public <V> V accept(CPropertyVisitor<V> visitor) {
        return visitor.onAttribute(this);
    }

    public final PropertyKind kind() {
        return  PropertyKind.ATTRIBUTE;
    }
}
