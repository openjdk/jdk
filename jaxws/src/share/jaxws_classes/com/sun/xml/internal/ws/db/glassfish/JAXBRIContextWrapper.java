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

package com.sun.xml.internal.ws.db.glassfish;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import com.sun.xml.internal.bind.api.TypeReference;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeTypeInfoSet;
import com.sun.xml.internal.ws.spi.db.BindingContext;
import com.sun.xml.internal.ws.spi.db.XMLBridge;
import com.sun.xml.internal.ws.spi.db.TypeInfo;
import com.sun.xml.internal.ws.spi.db.WrapperComposite;

class JAXBRIContextWrapper implements BindingContext {

    private Map<TypeInfo, TypeReference> typeRefs;
    private Map<TypeReference, TypeInfo> typeInfos;
    private JAXBRIContext context;

    JAXBRIContextWrapper(JAXBRIContext cxt, Map<TypeInfo, TypeReference> refs) {
        context = cxt;
        typeRefs = refs;
        if (refs != null) {
            typeInfos = new java.util.HashMap<TypeReference, TypeInfo>();
            for (TypeInfo ti : refs.keySet()) {
                typeInfos.put(typeRefs.get(ti), ti);
            }
        }
    }

    TypeReference typeReference(TypeInfo ti) {
        return (typeRefs != null) ? typeRefs.get(ti) : null;
    }

    TypeInfo typeInfo(TypeReference tr) {
        return (typeInfos != null) ? typeInfos.get(tr) : null;
    }

    @Override
    public Marshaller createMarshaller() throws JAXBException {
        return context.createMarshaller();
    }

    @Override
    public Unmarshaller createUnmarshaller() throws JAXBException {
        return context.createUnmarshaller();
    }

    @Override
    public void generateSchema(SchemaOutputResolver outputResolver)
            throws IOException {
        context.generateSchema(outputResolver);
    }

    @Override
    public String getBuildId() {
        return context.getBuildId();
    }

    @Override
    public QName getElementName(Class o) throws JAXBException {
        return context.getElementName(o);
    }

    @Override
    public QName getElementName(Object o) throws JAXBException {
        return context.getElementName(o);
    }

    @Override
    public <B, V> com.sun.xml.internal.ws.spi.db.PropertyAccessor<B, V> getElementPropertyAccessor(
            Class<B> wrapperBean, String nsUri, String localName)
            throws JAXBException {
        return new RawAccessorWrapper(context.getElementPropertyAccessor(wrapperBean, nsUri, localName));
    }

    @Override
    public List<String> getKnownNamespaceURIs() {
        return context.getKnownNamespaceURIs();
    }

    public RuntimeTypeInfoSet getRuntimeTypeInfoSet() {
        return context.getRuntimeTypeInfoSet();
    }

    public QName getTypeName(com.sun.xml.internal.bind.api.TypeReference tr) {
        return context.getTypeName(tr);
    }

    @Override
    public int hashCode() {
        return context.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final JAXBRIContextWrapper other = (JAXBRIContextWrapper) obj;
        if (this.context != other.context && (this.context == null || !this.context.equals(other.context))) {
            return false;
        }
        return true;
    }

    @Override
    public boolean hasSwaRef() {
        return context.hasSwaRef();
    }

    @Override
    public String toString() {
        return JAXBRIContextWrapper.class.getName() + " : " + context.toString();
    }

    @Override
    public XMLBridge createBridge(TypeInfo ti) {
        TypeReference tr = typeRefs.get(ti);
        com.sun.xml.internal.bind.api.Bridge b = context.createBridge(tr);
        return WrapperComposite.class.equals(ti.type)
                ? new WrapperBridge(this, b)
                : new BridgeWrapper(this, b);
    }

    @Override
    public JAXBContext getJAXBContext() {
        return context;
    }

    @Override
    public QName getTypeName(TypeInfo ti) {
        TypeReference tr = typeRefs.get(ti);
        return context.getTypeName(tr);
    }

    @Override
    public XMLBridge createFragmentBridge() {
        return new MarshallerBridge((com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl) context);
    }

    @Override
    public Object newWrapperInstace(Class<?> wrapperType)
            throws InstantiationException, IllegalAccessException {
        return wrapperType.newInstance();
    }
}
