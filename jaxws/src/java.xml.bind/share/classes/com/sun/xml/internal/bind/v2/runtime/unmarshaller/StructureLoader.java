/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import com.sun.xml.internal.bind.Util;
import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import com.sun.xml.internal.bind.v2.runtime.ClassBeanInfoImpl;
import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.JaxBeanInfo;
import com.sun.xml.internal.bind.v2.runtime.property.AttributeProperty;
import com.sun.xml.internal.bind.v2.runtime.property.Property;
import com.sun.xml.internal.bind.v2.runtime.property.StructureLoaderBuilder;
import com.sun.xml.internal.bind.v2.runtime.property.UnmarshallerChain;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.internal.bind.v2.runtime.reflect.TransducedAccessor;
import com.sun.xml.internal.bind.v2.util.QNameMap;

import java.util.Iterator;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Loads children of an element.
 *
 * <p>
 * This loader works with a single {@link JaxBeanInfo} and handles
 * attributes, child elements, or child text.
 *
 * @author Kohsuke Kawaguchi
 */
public final class StructureLoader extends Loader {
    /**
     * This map statically stores information of the
     * unmarshaller loader and can be used while unmarshalling
     * Since creating new QNames is expensive use this optimized
     * version of the map
     */
    private final QNameMap<ChildLoader> childUnmarshallers = new QNameMap<ChildLoader>();

    /**
     * Loader that processes elements that didn't match anf of the {@link #childUnmarshallers}.
     * Can be null.
     */
    private /*final*/ ChildLoader catchAll;

    /**
     * If we have a loader for processing text. Otherwise null.
     */
    private /*final*/ ChildLoader textHandler;

    /**
     * Unmarshallers for attribute values.
     * May be null if no attribute is expected and {@link #attCatchAll}==null.
     */
    private /*final*/ QNameMap<TransducedAccessor> attUnmarshallers;

    /**
     * This will receive all the attributes
     * that were not processed. Never be null.
     */
    private /*final*/ Accessor<Object,Map<QName,String>> attCatchAll;

    private final JaxBeanInfo beanInfo;

    /**
     * The number of scopes this dispatcher needs to keep active.
     */
    private /*final*/ int frameSize;

    // this class is potentially useful for general audience, not just for ClassBeanInfoImpl,
    // but since right now that is the only user, we make the construction code very specific
    // to ClassBeanInfoImpl. See rev.1.5 of this file for the original general purpose definition.
    public StructureLoader(ClassBeanInfoImpl beanInfo) {
        super(true);
        this.beanInfo = beanInfo;
    }

    /**
     * Completes the initialization.
     *
     * <p>
     * To fix the cyclic reference issue, the main part of the initialization needs to be done
     * after a {@link StructureLoader} is set to {@link ClassBeanInfoImpl#loader}.
     */
    public void init( JAXBContextImpl context, ClassBeanInfoImpl beanInfo, Accessor<?,Map<QName,String>> attWildcard) {
        UnmarshallerChain chain = new UnmarshallerChain(context);
        for (ClassBeanInfoImpl bi = beanInfo; bi != null; bi = bi.superClazz) {
            for (int i = bi.properties.length - 1; i >= 0; i--) {
                Property p = bi.properties[i];

                switch(p.getKind()) {
                case ATTRIBUTE:
                    if(attUnmarshallers==null)
                        attUnmarshallers = new QNameMap<TransducedAccessor>();
                    AttributeProperty ap = (AttributeProperty) p;
                    attUnmarshallers.put(ap.attName.toQName(),ap.xacc);
                    break;
                case ELEMENT:
                case REFERENCE:
                case MAP:
                case VALUE:
                    p.buildChildElementUnmarshallers(chain,childUnmarshallers);
                    break;
                }
            }
        }

        this.frameSize = chain.getScopeSize();

        textHandler = childUnmarshallers.get(StructureLoaderBuilder.TEXT_HANDLER);
        catchAll = childUnmarshallers.get(StructureLoaderBuilder.CATCH_ALL);

        if(attWildcard!=null) {
            attCatchAll = (Accessor<Object,Map<QName,String>>) attWildcard;
            // we use attUnmarshallers==null as a sign to skip the attribute processing
            // altogether, so if we have an att wildcard we need to have an empty qname map.
            if(attUnmarshallers==null)
                attUnmarshallers = EMPTY;
        } else {
            attCatchAll = null;
        }
    }

    @Override
    public void startElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
        UnmarshallingContext context = state.getContext();

        // create the object to unmarshal
        Object child;
        assert !beanInfo.isImmutable();

        // let's see if we can reuse the existing peer object
        child = context.getInnerPeer();

        if(child != null && beanInfo.jaxbType!=child.getClass())
            child = null;   // unexpected type.

        if(child != null)
            beanInfo.reset(child,context);

        if(child == null)
            child = context.createInstance(beanInfo);

        context.recordInnerPeer(child);

        state.setTarget(child);

        fireBeforeUnmarshal(beanInfo, child, state);


        context.startScope(frameSize);

        if(attUnmarshallers!=null) {
            Attributes atts = ea.atts;
            for (int i = 0; i < atts.getLength(); i ++){
                String auri = atts.getURI(i);
                // may be empty string based on parser settings
                String alocal = atts.getLocalName(i);
                if ("".equals(alocal)) {
                    alocal = atts.getQName(i);
                }
                String avalue = atts.getValue(i);
                TransducedAccessor xacc = attUnmarshallers.get(auri, alocal);
                try {
                    if(xacc!=null) {
                        xacc.parse(child,avalue);
                    } else if (attCatchAll!=null) {
                        String qname = atts.getQName(i);
                        if(atts.getURI(i).equals(WellKnownNamespace.XML_SCHEMA_INSTANCE))
                            continue;   // xsi:* attributes are meant to be processed by us, not by user apps.
                        Object o = state.getTarget();
                        Map<QName,String> map = attCatchAll.get(o);
                        if(map==null) {
                            // TODO: use  ClassFactory.inferImplClass(sig,knownImplClasses)

                            // if null, create a new map.
                            if(attCatchAll.valueType.isAssignableFrom(HashMap.class))
                                map = new HashMap<QName,String>();
                            else {
                                // we don't know how to create a map for this.
                                // report an error and back out
                                context.handleError(Messages.UNABLE_TO_CREATE_MAP.format(attCatchAll.valueType));
                                return;
                            }
                            attCatchAll.set(o,map);
                        }

                        String prefix;
                        int idx = qname.indexOf(':');
                        if(idx<0)   prefix="";
                        else        prefix=qname.substring(0,idx);

                        map.put(new QName(auri,alocal,prefix),avalue);
                    }
                } catch (AccessorException e) {
                   handleGenericException(e,true);
                }
            }
        }
    }

    @Override
    public void childElement(UnmarshallingContext.State state, TagName arg) throws SAXException {
        ChildLoader child = childUnmarshallers.get(arg.uri,arg.local);
        if(child == null) {
            Boolean backupWithParentNamespace = ((JAXBContextImpl) state.getContext().getJAXBContext()).backupWithParentNamespace;
                        backupWithParentNamespace = backupWithParentNamespace != null
                                        ? backupWithParentNamespace
                                        : Boolean.parseBoolean(Util.getSystemProperty(JAXBRIContext.BACKUP_WITH_PARENT_NAMESPACE));
            if ((beanInfo != null) && (beanInfo.getTypeNames() != null) && backupWithParentNamespace) {
                Iterator<?> typeNamesIt = beanInfo.getTypeNames().iterator();
                QName parentQName = null;
                if ((typeNamesIt != null) && (typeNamesIt.hasNext()) && (catchAll == null)) {
                    parentQName = (QName) typeNamesIt.next();
                    String parentUri = parentQName.getNamespaceURI();
                    child = childUnmarshallers.get(parentUri, arg.local);
                }
            }
            if (child == null) {
                child = catchAll;
                if(child==null) {
                    super.childElement(state,arg);
                    return;
                }
            }
        }

        state.setLoader(child.loader);
        state.setReceiver(child.receiver);
    }

    @Override
    public Collection<QName> getExpectedChildElements() {
        return childUnmarshallers.keySet();
    }

    @Override
    public Collection<QName> getExpectedAttributes() {
        return attUnmarshallers.keySet();
    }

    @Override
    public void text(UnmarshallingContext.State state, CharSequence text) throws SAXException {
        if(textHandler!=null)
            textHandler.loader.text(state,text);
    }

    @Override
    public void leaveElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
        state.getContext().endScope(frameSize);
        fireAfterUnmarshal(beanInfo, state.getTarget(), state.getPrev());
    }

    private static final QNameMap<TransducedAccessor> EMPTY = new QNameMap<TransducedAccessor>();

    public JaxBeanInfo getBeanInfo() {
        return beanInfo;
    }
}
