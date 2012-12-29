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

package com.sun.xml.internal.bind.v2.runtime;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import javax.xml.bind.ValidationEvent;
import javax.xml.bind.helpers.ValidationEventImpl;
import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.api.CompositeStructure;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Loader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallingContext;

import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
public class CompositeStructureBeanInfo extends JaxBeanInfo<CompositeStructure> {
    public CompositeStructureBeanInfo(JAXBContextImpl context) {
        super(context,null, CompositeStructure.class,false,true,false);
    }

    public String getElementNamespaceURI(CompositeStructure o) {
        throw new UnsupportedOperationException();
    }

    public String getElementLocalName(CompositeStructure o) {
        throw new UnsupportedOperationException();
    }

    public CompositeStructure createInstance(UnmarshallingContext context) throws IllegalAccessException, InvocationTargetException, InstantiationException, SAXException {
        throw new UnsupportedOperationException();
    }

    public boolean reset(CompositeStructure o, UnmarshallingContext context) throws SAXException {
        throw new UnsupportedOperationException();
    }

    public String getId(CompositeStructure o, XMLSerializer target) throws SAXException {
        return null;
    }

    public Loader getLoader(JAXBContextImpl context, boolean typeSubstitutionCapable) {
        // no unmarshaller support for this.
        throw new UnsupportedOperationException();
    }

    public void serializeRoot(CompositeStructure o, XMLSerializer target) throws SAXException, IOException, XMLStreamException {
        target.reportError(
                new ValidationEventImpl(
                        ValidationEvent.ERROR,
                        Messages.UNABLE_TO_MARSHAL_NON_ELEMENT.format(o.getClass().getName()),
                        null,
                        null));
    }

    public void serializeURIs(CompositeStructure o, XMLSerializer target) throws SAXException {
        // noop
    }

    public void serializeAttributes(CompositeStructure o, XMLSerializer target) throws SAXException, IOException, XMLStreamException {
        // noop
    }

    public void serializeBody(CompositeStructure o, XMLSerializer target) throws SAXException, IOException, XMLStreamException {
        int len = o.bridges.length;
        for( int i=0; i<len; i++ ) {
            Object value = o.values[i];
            InternalBridge bi = (InternalBridge)o.bridges[i];
            bi.marshal( value, target );
        }
    }

    public Transducer<CompositeStructure> getTransducer() {
        return null;
    }
}
