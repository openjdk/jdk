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

package com.sun.xml.internal.bind.v2.runtime.property;


import java.io.IOException;

import javax.xml.bind.annotation.XmlValue;
import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.model.core.PropertyKind;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeValuePropertyInfo;
import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.internal.bind.v2.runtime.reflect.TransducedAccessor;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.ChildLoader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.ValuePropertyLoader;
import com.sun.xml.internal.bind.v2.util.QNameMap;

import org.xml.sax.SAXException;

/**
 * {@link Property} implementation for {@link XmlValue} properties.
 *
 * <p>
 * This one works for both leaves and nodes, scalars and arrays.
 *
 * @author Bhakti Mehta (bhakti.mehta@sun.com)
 */
public final class ValueProperty<BeanT> extends PropertyImpl<BeanT> {

    /**
     * Heart of the conversion logic.
     */
    private final TransducedAccessor<BeanT> xacc;
    private final Accessor<BeanT,?> acc;


    public ValueProperty(JAXBContextImpl context, RuntimeValuePropertyInfo prop) {
        super(context,prop);
        xacc = TransducedAccessor.get(context,prop);
        acc = prop.getAccessor();   // we only use this for binder, so don't waste memory by optimizing
    }

    public final void serializeBody(BeanT o, XMLSerializer w, Object outerPeer) throws SAXException, AccessorException, IOException, XMLStreamException {
        if(xacc.hasValue(o))
            xacc.writeText(w,o,fieldName);
    }

    public void serializeURIs(BeanT o, XMLSerializer w) throws SAXException, AccessorException {
        xacc.declareNamespace(o,w);
    }

    public boolean hasSerializeURIAction() {
        return xacc.useNamespace();
    }

    public void buildChildElementUnmarshallers(UnmarshallerChain chainElem, QNameMap<ChildLoader> handlers) {
        handlers.put(StructureLoaderBuilder.TEXT_HANDLER,
                new ChildLoader(new ValuePropertyLoader(xacc),null));
    }

    public PropertyKind getKind() {
        return PropertyKind.VALUE;
    }

    public void reset(BeanT o) throws AccessorException {
        acc.set(o,null);
    }

    public String getIdValue(BeanT bean) throws AccessorException, SAXException {
        return xacc.print(bean).toString();
    }

}
