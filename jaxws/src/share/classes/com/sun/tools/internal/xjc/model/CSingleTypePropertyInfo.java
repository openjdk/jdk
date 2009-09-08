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

import java.util.Collections;
import java.util.List;

import javax.activation.MimeType;
import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.v2.model.core.ID;
import com.sun.xml.internal.xsom.XSComponent;

import org.xml.sax.Locator;

/**
 * {@link CPropertyInfo} backed by a single {@link TypeUse}.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class CSingleTypePropertyInfo extends CPropertyInfo {
    protected final TypeUse type;

    private final QName schemaType;

    /**
     *
     * @param typeName
     *      XML Schema type name of this property's single value. Optional
     *      for other schema languages. This is used to determine if we should
     *      generate {@link @XmlSchemaType} annotation to improve the roundtrip.
     */
    protected CSingleTypePropertyInfo(String name, TypeUse type, QName typeName, XSComponent source, CCustomizations customizations, Locator locator) {
        super(name, type.isCollection(), source, customizations, locator);
        this.type = type;

        if(needsExplicitTypeName(type,typeName))
            schemaType = typeName;
        else
            schemaType = null;
    }

    public QName getSchemaType() {
        return schemaType;
    }

    public final ID id() {
        return type.idUse();
    }

    public final MimeType getExpectedMimeType() {
        return type.getExpectedMimeType();
    }

    public final List<? extends CTypeInfo> ref() {
        return Collections.singletonList(getTarget());
    }

    public final CNonElement getTarget() {
        CNonElement r = type.getInfo();
        assert r!=null;
        return r;
    }

    public final CAdapter getAdapter() {
        return type.getAdapterUse();
    }

    public final CSingleTypePropertyInfo getSource() {
        return this;
    }
}
