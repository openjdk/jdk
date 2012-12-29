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

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.DomHandler;
import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.v2.ClassFactory;
import com.sun.xml.internal.bind.v2.model.core.PropertyKind;
import com.sun.xml.internal.bind.v2.model.core.WildcardMode;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeElement;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeReferencePropertyInfo;
import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.JaxBeanInfo;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.internal.bind.v2.runtime.reflect.ListIterator;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.ChildLoader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Loader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Receiver;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallingContext;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.WildcardLoader;
import com.sun.xml.internal.bind.v2.util.QNameMap;

import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
class ArrayReferenceNodeProperty<BeanT,ListT,ItemT> extends ArrayERProperty<BeanT,ListT,ItemT> {

    /**
     * Expected element names and what class to unmarshal.
     */
    private final QNameMap<JaxBeanInfo> expectedElements = new QNameMap<JaxBeanInfo>();

    private final boolean isMixed;

    private final DomHandler domHandler;
    private final WildcardMode wcMode;

    public ArrayReferenceNodeProperty(JAXBContextImpl p, RuntimeReferencePropertyInfo prop) {
        super(p, prop, prop.getXmlName(), prop.isCollectionNillable());

        for (RuntimeElement e : prop.getElements()) {
            JaxBeanInfo bi = p.getOrCreate(e);
            expectedElements.put( e.getElementName().getNamespaceURI(),e.getElementName().getLocalPart(), bi );
        }

        isMixed = prop.isMixed();

        if(prop.getWildcard()!=null) {
            domHandler = (DomHandler) ClassFactory.create(prop.getDOMHandler());
            wcMode = prop.getWildcard();
        } else {
            domHandler = null;
            wcMode = null;
        }
    }

    protected final void serializeListBody(BeanT o, XMLSerializer w, ListT list) throws IOException, XMLStreamException, SAXException {
        ListIterator<ItemT> itr = lister.iterator(list, w);

        while(itr.hasNext()) {
            try {
                ItemT item = itr.next();
                if (item != null) {
                    if(isMixed && item.getClass()==String.class) {
                        w.text((String)item,null);
                    } else {
                        JaxBeanInfo bi = w.grammar.getBeanInfo(item,true);
                        if(bi.jaxbType==Object.class && domHandler!=null)
                            // even if 'v' is a DOM node, it always derive from Object,
                            // so the getBeanInfo returns BeanInfo for Object
                            w.writeDom(item,domHandler,o,fieldName);
                        else
                            bi.serializeRoot(item,w);
                    }
                }
            } catch (JAXBException e) {
                w.reportError(fieldName,e);
                // recover by ignoring this item
            }
        }
    }

    public void createBodyUnmarshaller(UnmarshallerChain chain, QNameMap<ChildLoader> loaders) {
        final int offset = chain.allocateOffset();

        Receiver recv = new ReceiverImpl(offset);

        for( QNameMap.Entry<JaxBeanInfo> n : expectedElements.entrySet() ) {
            final JaxBeanInfo beanInfo = n.getValue();
            loaders.put(n.nsUri,n.localName,new ChildLoader(beanInfo.getLoader(chain.context,true),recv));
        }

        if(isMixed) {
            // handler for processing mixed contents.
            loaders.put(TEXT_HANDLER,
                new ChildLoader(new MixedTextLoader(recv),null));
        }

        if(domHandler!=null) {
            loaders.put(CATCH_ALL,
                new ChildLoader(new WildcardLoader(domHandler,wcMode),recv));
        }
    }

    private static final class MixedTextLoader extends Loader {

        private final Receiver recv;

        public MixedTextLoader(Receiver recv) {
            super(true);
            this.recv = recv;
        }

        public void text(UnmarshallingContext.State state, CharSequence text) throws SAXException {
            if(text.length()!=0) // length 0 text is pointless
                recv.receive(state,text.toString());
        }
    }


    public PropertyKind getKind() {
        return PropertyKind.REFERENCE;
    }

    @Override
    public Accessor getElementPropertyAccessor(String nsUri, String localName) {
        // TODO: unwrap JAXBElement
        if(wrapperTagName!=null) {
            if(wrapperTagName.equals(nsUri,localName))
                return acc;
        } else {
            if(expectedElements.containsKey(nsUri,localName))
                return acc;
        }
        return null;
    }
}
