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
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.model.core.PropertyKind;
import com.sun.xml.internal.bind.v2.model.core.TypeRef;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeElementPropertyInfo;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeTypeRef;
import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.JaxBeanInfo;
import com.sun.xml.internal.bind.v2.runtime.Name;
import com.sun.xml.internal.bind.v2.runtime.RuntimeUtil;
import com.sun.xml.internal.bind.v2.runtime.Transducer;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.internal.bind.v2.runtime.reflect.ListIterator;
import com.sun.xml.internal.bind.v2.runtime.reflect.Lister;
import com.sun.xml.internal.bind.v2.runtime.reflect.NullSafeAccessor;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.ChildLoader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.DefaultValueLoaderDecorator;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Loader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Receiver;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.TextLoader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.XsiNilLoader;
import com.sun.xml.internal.bind.v2.util.QNameMap;

import org.xml.sax.SAXException;

/**
 * {@link Property} implementation for multi-value property that maps to an element.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class ArrayElementProperty<BeanT,ListT,ItemT> extends ArrayERProperty<BeanT,ListT,ItemT> {

    private final Map<Class,TagAndType> typeMap  = new HashMap<Class,TagAndType>();
    /**
     * Set by the constructor and reset in the {@link #wrapUp()} method.
     */
    private Map<TypeRef<Type,Class>,JaxBeanInfo> refs = new HashMap<TypeRef<Type, Class>, JaxBeanInfo>();
    /**
     * Set by the constructor and reset in the {@link #wrapUp()} method.
     */
    protected RuntimeElementPropertyInfo prop;

    /**
     * Tag name used when we see null in the collection. Can be null.
     */
    private final Name nillableTagName;

    protected ArrayElementProperty(JAXBContextImpl grammar, RuntimeElementPropertyInfo prop) {
        super(grammar, prop, prop.getXmlName(), prop.isCollectionNillable());
        this.prop = prop;

        List<? extends RuntimeTypeRef> types = prop.getTypes();

        Name n = null;

        for (RuntimeTypeRef typeRef : types) {
            Class type = (Class)typeRef.getTarget().getType();
            if(type.isPrimitive())
                type = RuntimeUtil.primitiveToBox.get(type);

            JaxBeanInfo beanInfo = grammar.getOrCreate(typeRef.getTarget());
            TagAndType tt = new TagAndType(
                                grammar.nameBuilder.createElementName(typeRef.getTagName()),
                                beanInfo);
            typeMap.put(type,tt);
            refs.put(typeRef,beanInfo);
            if(typeRef.isNillable() && n==null)
                n = tt.tagName;
        }

        nillableTagName = n;
    }

    @Override
    public void wrapUp() {
        super.wrapUp();
        refs = null;
        prop = null;    // avoid keeping model objects live
    }

    protected void serializeListBody(BeanT beanT, XMLSerializer w, ListT list) throws IOException, XMLStreamException, SAXException, AccessorException {
        ListIterator<ItemT> itr = lister.iterator(list, w);

        boolean isIdref = itr instanceof Lister.IDREFSIterator; // UGLY

        while(itr.hasNext()) {
            try {
                ItemT item = itr.next();
                if (item != null) {
                    Class itemType = item.getClass();
                    if(isIdref)
                        // This should be the only place where we need to be aware
                        // that the iterator is iterating IDREFS.
                        itemType = ((Lister.IDREFSIterator)itr).last().getClass();

                    // normally, this returns non-null
                    TagAndType tt = typeMap.get(itemType);
                    while(tt==null && itemType!=null) {
                        // otherwise we'll just have to try the slow way
                        itemType = itemType.getSuperclass();
                        tt = typeMap.get(itemType);
                    }

                    if(tt==null) {
                        // item is not of the expected type.
//                        w.reportError(new ValidationEventImpl(ValidationEvent.ERROR,
//                            Messages.UNEXPECTED_JAVA_TYPE.format(
//                                item.getClass().getName(),
//                                getExpectedClassNameList()
//                            ),
//                            w.getCurrentLocation(fieldName)));
//                        continue;

                        // see the similar code in SingleElementNodeProperty.
                        // for the purpose of simple type substitution, make it a non-error

                        w.startElement(typeMap.values().iterator().next().tagName,null);
                        w.childAsXsiType(item,fieldName,w.grammar.getBeanInfo(Object.class));
                    } else {
                        w.startElement(tt.tagName,null);
                        serializeItem(tt.beanInfo,item,w);
                    }

                    w.endElement();
                } else {
                    if(nillableTagName!=null) {
                        w.startElement(nillableTagName,null);
                        w.writeXsiNilTrue();
                        w.endElement();
                    }
                }
            } catch (JAXBException e) {
                w.reportError(fieldName,e);
                // recover by ignoring this item
            }
        }
    }

    /**
     * Serializes one item of the property.
     */
    protected abstract void serializeItem(JaxBeanInfo expected, ItemT item, XMLSerializer w) throws SAXException, AccessorException, IOException, XMLStreamException;


    public void createBodyUnmarshaller(UnmarshallerChain chain, QNameMap<ChildLoader> loaders) {

        // all items go to the same lister,
        // so they should share the same offset.
        int offset = chain.allocateOffset();
        Receiver recv = new ReceiverImpl(offset);

        for (RuntimeTypeRef typeRef : prop.getTypes()) {

            Name tagName = chain.context.nameBuilder.createElementName(typeRef.getTagName());
            Loader item = createItemUnmarshaller(chain,typeRef);

            if(typeRef.isNillable() || chain.context.allNillable)
                item = new XsiNilLoader.Array(item);
            if(typeRef.getDefaultValue()!=null)
                item = new DefaultValueLoaderDecorator(item,typeRef.getDefaultValue());

            loaders.put(tagName,new ChildLoader(item,recv));
        }
    }

    public final PropertyKind getKind() {
        return PropertyKind.ELEMENT;
    }

    /**
     * Creates a loader handler that unmarshals the body of the item.
     *
     * <p>
     * This will be sandwiched into <item> ... </item>.
     *
     * <p>
     * When unmarshalling the body of item, the Pack of {@link Lister} is available
     * as the handler state.
     *
     * @param chain
     * @param typeRef
     */
    private Loader createItemUnmarshaller(UnmarshallerChain chain, RuntimeTypeRef typeRef) {
        if(PropertyFactory.isLeaf(typeRef.getSource())) {
            final Transducer xducer = typeRef.getTransducer();
            return new TextLoader(xducer);
        } else {
            return refs.get(typeRef).getLoader(chain.context,true);
        }
    }

    public Accessor getElementPropertyAccessor(String nsUri, String localName) {
        if(wrapperTagName!=null) {
            if(wrapperTagName.equals(nsUri,localName))
                return acc;
        } else {
            for (TagAndType tt : typeMap.values()) {
                if(tt.tagName.equals(nsUri,localName))
                    // when we can't distinguish null and empty list, JAX-WS doesn't want to see
                    // null (just like any user apps), but since we are providing a raw accessor,
                    // which just grabs the value from the field, we wrap it so that it won't return
                    // null.
                    return new NullSafeAccessor<BeanT,ListT,Object>(acc,lister);
            }
        }
        return null;
    }
}
