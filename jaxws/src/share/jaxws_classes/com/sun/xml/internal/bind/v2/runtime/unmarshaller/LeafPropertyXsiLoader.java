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

package com.sun.xml.internal.bind.v2.runtime.unmarshaller;

import java.util.Collection;

import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.DatatypeConverterImpl;
import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import com.sun.xml.internal.bind.v2.runtime.ClassBeanInfoImpl;
import com.sun.xml.internal.bind.v2.runtime.JaxBeanInfo;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.internal.bind.v2.runtime.reflect.TransducedAccessor;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public final class LeafPropertyXsiLoader extends Loader {

    private final Loader defaultLoader;
    private final TransducedAccessor xacc;
    private final Accessor acc;

    public LeafPropertyXsiLoader(Loader defaultLoader, TransducedAccessor xacc, Accessor acc) {
        this.defaultLoader = defaultLoader;
        this.expectText = true;
        this.xacc = xacc;
        this.acc = acc;
    }


    @Override
    public void startElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
        state.loader = selectLoader(state, ea);

        state.loader.startElement(state, ea);
    }

    protected Loader selectLoader(UnmarshallingContext.State state, TagName ea) throws SAXException {

        UnmarshallingContext context = state.getContext();
        JaxBeanInfo beanInfo = null;

        // look for @xsi:type
        Attributes atts = ea.atts;
        int idx = atts.getIndex(WellKnownNamespace.XML_SCHEMA_INSTANCE, "type");

        if (idx >= 0) {
            String value = atts.getValue(idx);

            QName type = DatatypeConverterImpl._parseQName(value, context);

            if (type == null)
                return defaultLoader;

            beanInfo = context.getJAXBContext().getGlobalType(type);
            if (beanInfo == null)
                return defaultLoader;
            ClassBeanInfoImpl cbii;
            try {
                cbii = (ClassBeanInfoImpl) beanInfo;
            } catch (ClassCastException cce) {
                return defaultLoader;
            }

            if (null == cbii.getTransducer()) {
                return defaultLoader;
            }

            return new LeafPropertyLoader(
                    new TransducedAccessor.CompositeTransducedAccessorImpl(
                            state.getContext().getJAXBContext(),
                            cbii.getTransducer(),
                            acc));
        }

        return defaultLoader;
    }

    @Override
    public Collection<QName> getExpectedChildElements() {
        return defaultLoader.getExpectedChildElements();
    }

    @Override
    public Collection<QName> getExpectedAttributes() {
        return defaultLoader.getExpectedAttributes();
    }
}
