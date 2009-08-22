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

package com.sun.xml.internal.bind.v2.runtime.property;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Arrays;
import java.util.Set;

import javax.xml.stream.XMLStreamException;
import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.ClassFactory;
import com.sun.xml.internal.bind.v2.util.QNameMap;
import com.sun.xml.internal.bind.v2.model.core.PropertyKind;
import com.sun.xml.internal.bind.v2.model.nav.ReflectionNavigator;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeMapPropertyInfo;
import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.JaxBeanInfo;
import com.sun.xml.internal.bind.v2.runtime.Name;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.ChildLoader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.TagName;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Loader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Receiver;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallingContext;

import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
final class SingleMapNodeProperty<BeanT,ValueT extends Map> extends PropertyImpl<BeanT> {

    private final Accessor<BeanT,ValueT> acc;
    /**
     * The tag name that surrounds the whole property.
     */
    private final Name tagName;
    /**
     * The tag name that corresponds to the 'entry' element.
     */
    private final Name entryTag;
    private final Name keyTag;
    private final Name valueTag;

    private final boolean nillable;

    private JaxBeanInfo keyBeanInfo;
    private JaxBeanInfo valueBeanInfo;

    /**
     * The implementation class for this property.
     * If the property is null, we create an instance of this class.
     */
    private final Class<? extends ValueT> mapImplClass;

    public SingleMapNodeProperty(JAXBContextImpl context, RuntimeMapPropertyInfo prop) {
        super(context, prop);
        acc = prop.getAccessor().optimize(context);
        this.tagName = context.nameBuilder.createElementName(prop.getXmlName());
        this.entryTag = context.nameBuilder.createElementName("","entry");
        this.keyTag = context.nameBuilder.createElementName("","key");
        this.valueTag = context.nameBuilder.createElementName("","value");
        this.nillable = prop.isCollectionNillable();
        this.keyBeanInfo = context.getOrCreate(prop.getKeyType());
        this.valueBeanInfo = context.getOrCreate(prop.getValueType());

        // infer the implementation class
        Class<ValueT> sig = ReflectionNavigator.REFLECTION.erasure(prop.getRawType());
        mapImplClass = ClassFactory.inferImplClass(sig,knownImplClasses);
        // TODO: error check for mapImplClass==null
        // what is the error reporting path for this part of the code?
    }

    private static final Class[] knownImplClasses = {
        HashMap.class, TreeMap.class, LinkedHashMap.class
    };

    public void reset(BeanT bean) throws AccessorException {
        acc.set(bean,null);
    }


    /**
     * A Map property can never be ID.
     */
    public String getIdValue(BeanT bean) {
        return null;
    }

    public PropertyKind getKind() {
        return PropertyKind.MAP;
    }

    public void buildChildElementUnmarshallers(UnmarshallerChain chain, QNameMap<ChildLoader> handlers) {
        keyLoader = keyBeanInfo.getLoader(chain.context,true);
        valueLoader = valueBeanInfo.getLoader(chain.context,true);
        handlers.put(tagName,new ChildLoader(itemsLoader,null));
    }

    private Loader keyLoader;
    private Loader valueLoader;

    /**
     * Handles &lt;items> and &lt;/items>.
     *
     * The target will be set to a {@link Map}.
     */
    private final Loader itemsLoader = new Loader(false) {
        @Override
        public void startElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
            // create or obtain the Map object
            try {
                BeanT target = (BeanT)state.prev.target;
                ValueT map = acc.get(target);
                if(map==null) {
                    map = ClassFactory.create(mapImplClass);
                    acc.set(target,map);
                }
                map.clear();
                state.target = map;
            } catch (AccessorException e) {
                // recover from error by setting a dummy Map that receives and discards the values
                handleGenericException(e,true);
                state.target = new HashMap();
            }
        }

        @Override
        public void childElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
            if(ea.matches(entryTag)) {
                state.loader = entryLoader;
            } else {
                super.childElement(state,ea);
            }
        }

        @Override
        public Collection<QName> getExpectedChildElements() {
            return Collections.singleton(entryTag.toQName());
        }
    };

    /**
     * Handles &lt;entry> and &lt;/entry>.
     *
     * The target will be set to a {@link Map}.
     */
    private final Loader entryLoader = new Loader(false) {
        @Override
        public void startElement(UnmarshallingContext.State state, TagName ea) {
            state.target = new Object[2];  // this is inefficient
        }

        @Override
        public void leaveElement(UnmarshallingContext.State state, TagName ea) {
            Object[] keyValue = (Object[])state.target;
            Map map = (Map) state.prev.target;
            map.put(keyValue[0],keyValue[1]);
        }

        @Override
        public void childElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
            if(ea.matches(keyTag)) {
                state.loader = keyLoader;
                state.receiver = keyReceiver;
                return;
            }
            if(ea.matches(valueTag)) {
                state.loader = valueLoader;
                state.receiver = valueReceiver;
                return;
            }
            super.childElement(state,ea);
        }

        @Override
        public Collection<QName> getExpectedChildElements() {
            return Arrays.asList(keyTag.toQName(),valueTag.toQName());
        }
    };

    private static final class ReceiverImpl implements Receiver {
        private final int index;
        public ReceiverImpl(int index) {
            this.index = index;
        }
        public void receive(UnmarshallingContext.State state, Object o) {
            ((Object[])state.target)[index] = o;
        }
    }

    private static final Receiver keyReceiver = new ReceiverImpl(0);
    private static final Receiver valueReceiver = new ReceiverImpl(1);




    public void serializeBody(BeanT o, XMLSerializer w, Object outerPeer) throws SAXException, AccessorException, IOException, XMLStreamException {
        ValueT v = acc.get(o);
        if(v!=null) {
            bareStartTag(w,tagName,v);
            for( Map.Entry e : (Set<Map.Entry>)v.entrySet() ) {
                bareStartTag(w,entryTag,null);

                Object key = e.getKey();
                if(key!=null) {
                    w.startElement(keyTag,key);
                    w.childAsXsiType(key,fieldName,keyBeanInfo, false);
                    w.endElement();
                }

                Object value = e.getValue();
                if(value!=null) {
                    w.startElement(valueTag,value);
                    w.childAsXsiType(value,fieldName,valueBeanInfo, false);
                    w.endElement();
                }

                w.endElement();
            }
            w.endElement();
        } else
        if(nillable) {
            w.startElement(tagName,null);
            w.writeXsiNilTrue();
            w.endElement();
        }
    }

    private void bareStartTag(XMLSerializer w, Name tagName, Object peer) throws IOException, XMLStreamException, SAXException {
        w.startElement(tagName,peer);
        w.endNamespaceDecls(peer);
        w.endAttributes();
    }

    @Override
    public Accessor getElementPropertyAccessor(String nsUri, String localName) {
        if(tagName.equals(nsUri,localName))
            return acc;
        return null;
    }
}
