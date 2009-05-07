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
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.util.QNameMap;
import com.sun.xml.internal.bind.v2.model.core.PropertyKind;
import com.sun.xml.internal.bind.v2.model.core.TypeRef;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeElementPropertyInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeTypeInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeTypeRef;
import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.JaxBeanInfo;
import com.sun.xml.internal.bind.v2.runtime.Name;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Loader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.ChildLoader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.XsiNilLoader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.DefaultValueLoaderDecorator;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;

import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
final class SingleElementNodeProperty<BeanT,ValueT> extends PropertyImpl<BeanT> {

    private final Accessor<BeanT,ValueT> acc;

    private final boolean nillable;

    private final QName[] acceptedElements;

    private final Map<Class,TagAndType> typeNames = new HashMap<Class,TagAndType>();

    private RuntimeElementPropertyInfo prop;

    /**
     * The tag name used to produce xsi:nil. The first one in the list.
     */
    private final Name nullTagName;

    public SingleElementNodeProperty(JAXBContextImpl context, RuntimeElementPropertyInfo prop) {
        super(context,prop);
        acc = prop.getAccessor().optimize(context);
        this.prop = prop;

        QName nt = null;
        boolean nil = false;

        acceptedElements = new QName[prop.getTypes().size()];
        for( int i=0; i<acceptedElements.length; i++ )
            acceptedElements[i] = prop.getTypes().get(i).getTagName();

        for (RuntimeTypeRef e : prop.getTypes()) {
            JaxBeanInfo beanInfo = context.getOrCreate(e.getTarget());
            if(nt==null)    nt = e.getTagName();
            typeNames.put( beanInfo.jaxbType, new TagAndType(
                context.nameBuilder.createElementName(e.getTagName()),beanInfo) );
            nil |= e.isNillable();
        }

        nullTagName = context.nameBuilder.createElementName(nt);

        nillable = nil;
    }

    public void wrapUp() {
        super.wrapUp();
        prop = null;
    }

    public void reset(BeanT bean) throws AccessorException {
        acc.set(bean,null);
    }

    public String getIdValue(BeanT beanT) {
        return null;
    }

    public void serializeBody(BeanT o, XMLSerializer w, Object outerPeer) throws SAXException, AccessorException, IOException, XMLStreamException {
        ValueT v = acc.get(o);
        if(v!=null) {
            Class vtype = v.getClass();
            TagAndType tt=typeNames.get(vtype); // quick way that usually works

            if(tt==null) {// slow way that always works
                for (Map.Entry<Class,TagAndType> e : typeNames.entrySet()) {
                    if(e.getKey().isAssignableFrom(vtype)) {
                        tt = e.getValue();
                        break;
                    }
                }
            }

            if(tt==null) {
                // actually this is an error, because the actual type was not a sub-type
                // of any of the types specified in the annotations,
                // but for the purpose of experimenting with simple type substitution,
                // it's convenient to marshal this anyway (for example so that classes
                // generated from simple types like String can be marshalled as expected.)
                w.startElement(typeNames.values().iterator().next().tagName,null);
                w.childAsXsiType(v,fieldName,w.grammar.getBeanInfo(Object.class));
            } else {
                w.startElement(tt.tagName,null);
                w.childAsXsiType(v,fieldName,tt.beanInfo);
            }
            w.endElement();
        } else
        if(nillable) {
            w.startElement(nullTagName,null);
            w.writeXsiNilTrue();
            w.endElement();
        }
    }

    public void buildChildElementUnmarshallers(UnmarshallerChain chain, QNameMap<ChildLoader> handlers) {
        JAXBContextImpl context = chain.context;

        for (TypeRef<Type,Class> e : prop.getTypes()) {
            JaxBeanInfo bi = context.getOrCreate((RuntimeTypeInfo) e.getTarget());
            Loader l = bi.getLoader(context,true);
            if(e.getDefaultValue()!=null)
                l = new DefaultValueLoaderDecorator(l,e.getDefaultValue());
            if(nillable || chain.context.allNillable)
                l = new XsiNilLoader.Single(l,acc);
            handlers.put( e.getTagName(), new ChildLoader(l,acc));
        }
    }

    public PropertyKind getKind() {
        return PropertyKind.ELEMENT;
    }

    @Override
    public Accessor getElementPropertyAccessor(String nsUri, String localName) {
        for( QName n : acceptedElements) {
            if(n.getNamespaceURI().equals(nsUri) && n.getLocalPart().equals(localName))
                return acc;
        }
        return null;
    }

}
