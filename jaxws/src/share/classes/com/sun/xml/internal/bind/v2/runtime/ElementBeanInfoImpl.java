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
package com.sun.xml.internal.bind.v2.runtime;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.model.core.PropertyKind;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeElementInfo;
import com.sun.xml.internal.bind.v2.runtime.property.Property;
import com.sun.xml.internal.bind.v2.runtime.property.PropertyFactory;
import com.sun.xml.internal.bind.v2.runtime.property.UnmarshallerChain;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.ChildLoader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Discarder;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Intercepter;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Loader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.TagName;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallingContext;
import com.sun.xml.internal.bind.v2.util.QNameMap;

import org.xml.sax.SAXException;

/**
 * {@link JaxBeanInfo} implementation for {@link RuntimeElementInfo}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ElementBeanInfoImpl extends JaxBeanInfo<JAXBElement> {

    private Loader loader;

    private final Property property;

    // used to create new instances of JAXBElement.
    private final QName tagName;
    public final Class expectedType;
    private final Class scope;

    /**
     * If non-null, use this to create an instance.
     * It takes one value.
     */
    private final Constructor<? extends JAXBElement> constructor;

    ElementBeanInfoImpl(JAXBContextImpl grammar, RuntimeElementInfo rei) {
        super(grammar,rei,(Class<JAXBElement>)rei.getType(),true,false,true);

        this.property = PropertyFactory.create(grammar,rei.getProperty());

        tagName = rei.getElementName();
        expectedType = Navigator.REFLECTION.erasure(rei.getContentInMemoryType());
        scope = rei.getScope()==null ? JAXBElement.GlobalScope.class : rei.getScope().getClazz();

        Class type = Navigator.REFLECTION.erasure(rei.getType());
        if(type==JAXBElement.class)
            constructor = null;
        else {
            try {
                constructor = type.getConstructor(expectedType);
            } catch (NoSuchMethodException e) {
                NoSuchMethodError x = new NoSuchMethodError("Failed to find the constructor for " + type + " with " + expectedType);
                x.initCause(e);
                throw x;
            }
        }
    }

    /**
     * The constructor for the sole instanceof {@link JaxBeanInfo} for
     * handling user-created {@link JAXBElement}.
     *
     * Such {@link JaxBeanInfo} is used only for marshalling.
     *
     * This is a hack.
     */
    protected ElementBeanInfoImpl(final JAXBContextImpl grammar) {
        super(grammar,null,JAXBElement.class,true,false,true);
        tagName = null;
        expectedType = null;
        scope = null;
        constructor = null;

        this.property = new Property<JAXBElement>() {
            public void reset(JAXBElement o) {
                throw new UnsupportedOperationException();
            }

            public void serializeBody(JAXBElement e, XMLSerializer target, Object outerPeer) throws SAXException, IOException, XMLStreamException {
                Class scope = e.getScope();
                if(e.isGlobalScope())   scope = null;
                QName n = e.getName();
                ElementBeanInfoImpl bi = grammar.getElement(scope,n);
                if(bi==null) {
                    // infer what to do from the type
                    JaxBeanInfo tbi;
                    try {
                        tbi = grammar.getBeanInfo(e.getDeclaredType(),true);
                    } catch (JAXBException x) {
                        // if e.getDeclaredType() isn't known to this JAXBContext
                        target.reportError(null,x);
                        return;
                    }
                    Object value = e.getValue();
                    target.startElement(n.getNamespaceURI(),n.getLocalPart(),n.getPrefix(),null);
                    if(value==null) {
                        target.writeXsiNilTrue();
                    } else {
                        target.childAsXsiType(value,"value",tbi);
                    }
                    target.endElement();
                } else {
                    try {
                        bi.property.serializeBody(e,target,e);
                    } catch (AccessorException x) {
                        target.reportError(null,x);
                    }
                }
            }

            public void serializeURIs(JAXBElement o, XMLSerializer target) {
            }

            public boolean hasSerializeURIAction() {
                return false;
            }

            public String getIdValue(JAXBElement o) {
                return null;
            }

            public PropertyKind getKind() {
                return PropertyKind.ELEMENT;
            }

            public void buildChildElementUnmarshallers(UnmarshallerChain chain, QNameMap<ChildLoader> handlers) {
            }

            public Accessor getElementPropertyAccessor(String nsUri, String localName) {
                throw new UnsupportedOperationException();
            }

            public void wrapUp() {
            }
        };
    }

    /**
     * Use the previous {@link UnmarshallingContext.State}'s target to store
     * {@link JAXBElement} object to be unmarshalled. This allows the property {@link Loader}
     * to correctly find the parent object.
     * This is a hack.
     */
    private final class IntercepterLoader extends Loader implements Intercepter {
        private final Loader core;

        public IntercepterLoader(Loader core) {
            this.core = core;
        }

        public final void startElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
            state.loader = core;
            state.intercepter = this;

            // TODO: make sure there aren't too many duplicate of this code
            // create the object to unmarshal
            Object child;
            UnmarshallingContext context = state.getContext();

            // let's see if we can reuse the existing peer object
            child = context.getOuterPeer();

            if(child!=null && jaxbType!=child.getClass())
                child = null;   // unexpected type.

            if(child!=null)
                reset((JAXBElement)child,context);

            if(child==null)
                child = context.createInstance(ElementBeanInfoImpl.this);

            fireBeforeUnmarshal(ElementBeanInfoImpl.this, child, state);

            context.recordOuterPeer(child);
            UnmarshallingContext.State p = state.prev;
            p.backup = p.target;
            p.target = child;

            core.startElement(state,ea);
        }

        public Object intercept(UnmarshallingContext.State state, Object o) throws SAXException {
            JAXBElement e = (JAXBElement)state.target;
            state.target = state.backup;
            state.backup = null;

            if(o!=null)
                // if the value is a leaf type, it's often already set to the element
                // through Accessor.
                e.setValue(o);

            fireAfterUnmarshal(ElementBeanInfoImpl.this, e, state);

            return e;
        }
    }

    public String getElementNamespaceURI(JAXBElement e) {
        return e.getName().getNamespaceURI();
    }

    public String getElementLocalName(JAXBElement e) {
        return e.getName().getLocalPart();
    }

    public Loader getLoader(JAXBContextImpl context, boolean typeSubstitutionCapable) {
        if(loader==null) {
            // this has to be done lazily to avoid cyclic reference issue
            UnmarshallerChain c = new UnmarshallerChain(context);
            QNameMap<ChildLoader> result = new QNameMap<ChildLoader>();
            property.buildChildElementUnmarshallers(c,result);
            if(result.size()==1)
                // for ElementBeanInfoImpl created from RuntimeElementInfo
                this.loader = new IntercepterLoader(result.getOne().getValue().loader);
            else
                // for special ElementBeanInfoImpl only used for marshalling
                this.loader = Discarder.INSTANCE;
        }
        return loader;
    }

    public final JAXBElement createInstance(UnmarshallingContext context) throws IllegalAccessException, InvocationTargetException, InstantiationException {
        return createInstanceFromValue(null);
    }

    public final JAXBElement createInstanceFromValue(Object o) throws IllegalAccessException, InvocationTargetException, InstantiationException {
        if(constructor==null)
            return new JAXBElement(tagName,expectedType,scope,o);
        else
            return constructor.newInstance(o);
    }

    public boolean reset(JAXBElement e, UnmarshallingContext context) {
        e.setValue(null);
        return true;
    }

    public String getId(JAXBElement e, XMLSerializer target) {
        // TODO: is this OK? Should we be returning the ID value of the type property?
        /*
            There's one case where we JAXBElement needs to be designated as ID,
            and that is when there's a global element whose type is ID.
        */
        Object o = e.getValue();
        if(o instanceof String)
            return (String)o;
        else
            return null;
    }

    public void serializeBody(JAXBElement element, XMLSerializer target) throws SAXException, IOException, XMLStreamException {
        try {
            property.serializeBody(element,target,null);
        } catch (AccessorException x) {
            target.reportError(null,x);
        }
    }

    public void serializeRoot(JAXBElement e, XMLSerializer target) throws SAXException, IOException, XMLStreamException {
        serializeBody(e,target);
    }

    public void serializeAttributes(JAXBElement e, XMLSerializer target) {
        // noop
    }

    public void serializeURIs(JAXBElement e, XMLSerializer target) {
        // noop
    }

    public final Transducer<JAXBElement> getTransducer() {
        return null;
    }

    public void wrapUp() {
        super.wrapUp();
        property.wrapUp();
    }

    public void link(JAXBContextImpl grammar) {
        super.link(grammar);
        getLoader(grammar,true);    // make sure to build them, if we hadn't done so
    }
}
