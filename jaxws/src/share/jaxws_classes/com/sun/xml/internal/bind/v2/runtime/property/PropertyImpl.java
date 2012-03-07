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

package com.sun.xml.internal.bind.v2.runtime.property;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimePropertyInfo;
import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;

import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
abstract class PropertyImpl<BeanT> implements Property<BeanT> {
    /**
     * Name of this field.
     */
    protected final String fieldName;
    private RuntimePropertyInfo propertyInfo = null;
    private boolean hiddenByOverride = false;

    public PropertyImpl(JAXBContextImpl context, RuntimePropertyInfo prop) {
        fieldName = prop.getName();
        if (context.retainPropertyInfo) {
            propertyInfo = prop;
        }
    }

    public RuntimePropertyInfo getInfo() {
        return propertyInfo;
    }

    public void serializeBody(BeanT o, XMLSerializer w, Object outerPeer) throws SAXException, AccessorException, IOException, XMLStreamException {
    }

    public void serializeURIs(BeanT o, XMLSerializer w) throws SAXException, AccessorException {
    }

    public boolean hasSerializeURIAction() {
        return false;
    }

    public Accessor getElementPropertyAccessor(String nsUri, String localName) {
        // default implementation. should be overrided
        return null;
    }

    public void wrapUp() {/*noop*/}

    public boolean isHiddenByOverride() {
        return hiddenByOverride;
    }

    public void setHiddenByOverride(boolean hidden) {
        this.hiddenByOverride = hidden;
    }

    public String getFieldName() {
        return fieldName;
    }
}
