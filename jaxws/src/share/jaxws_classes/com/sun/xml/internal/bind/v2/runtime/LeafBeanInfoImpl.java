/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

import javax.xml.bind.ValidationEvent;
import javax.xml.bind.helpers.ValidationEventImpl;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeLeafInfo;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Loader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.TextLoader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallingContext;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.XsiTypeLoader;

import org.xml.sax.SAXException;

/**
 * {@link JaxBeanInfo} implementation for immutable leaf classes.
 *
 * <p>
 * Leaf classes are always bound to a text and they are often immutable.
 * The JAXB spec allows this binding for a few special Java classes plus
 * type-safe enums.
 *
 * <p>
 * This implementation obtains necessary information from {@link RuntimeLeafInfo}.
 *
 * @author Kohsuke Kawaguchi
 */
final class LeafBeanInfoImpl<BeanT> extends JaxBeanInfo<BeanT> {

    private final Loader loader;
    private final Loader loaderWithSubst;

    private final Transducer<BeanT> xducer;

    /**
     * Non-null only if the leaf is also an element.
     */
    private final Name tagName;

    public LeafBeanInfoImpl(JAXBContextImpl grammar, RuntimeLeafInfo li) {
        super(grammar,li,li.getClazz(),li.getTypeNames(),li.isElement(),true,false);

        xducer = li.getTransducer();
        loader = new TextLoader(xducer);
        loaderWithSubst = new XsiTypeLoader(this);

        if(isElement())
            tagName = grammar.nameBuilder.createElementName(li.getElementName());
        else
            tagName = null;
    }

    @Override
    public QName getTypeName(BeanT instance) {
        QName tn = xducer.getTypeName(instance);
        if(tn!=null)    return tn;
        // rely on default
        return super.getTypeName(instance);
    }

    public final String getElementNamespaceURI(BeanT t) {
        return tagName.nsUri;
    }

    public final String getElementLocalName(BeanT t) {
        return tagName.localName;
    }

    public BeanT createInstance(UnmarshallingContext context) {
        throw new UnsupportedOperationException();
    }

    public final boolean reset(BeanT bean, UnmarshallingContext context) {
        return false;
    }

    public final String getId(BeanT bean, XMLSerializer target) {
        return null;
    }

    public final void serializeBody(BeanT bean, XMLSerializer w) throws SAXException, IOException, XMLStreamException {
        // most of the times leaves are printed as leaf element/attribute property,
        // so this code is only used for example when you have multiple XmlElement on a property
        // and some of them are leaves. Hence this doesn't need to be super-fast.
        try {
            xducer.writeText(w,bean,null);
        } catch (AccessorException e) {
            w.reportError(null,e);
        }
    }

    public final void serializeAttributes(BeanT bean, XMLSerializer target) {
        // noop
    }

    public final void serializeRoot(BeanT bean, XMLSerializer target) throws SAXException, IOException, XMLStreamException {
        if(tagName==null) {
            target.reportError(
                new ValidationEventImpl(
                    ValidationEvent.ERROR,
                    Messages.UNABLE_TO_MARSHAL_NON_ELEMENT.format(bean.getClass().getName()),
                    null,
                    null));
        }
        else {
            target.startElement(tagName,bean);
            target.childAsSoleContent(bean,null);
            target.endElement();
        }
    }

    public final void serializeURIs(BeanT bean, XMLSerializer target) throws SAXException {
        // TODO: maybe we should create another LeafBeanInfoImpl class for
        // context-dependent xducers?
        if(xducer.useNamespace()) {
            try {
                xducer.declareNamespace(bean,target);
            } catch (AccessorException e) {
                target.reportError(null,e);
            }
        }
    }

    public final Loader getLoader(JAXBContextImpl context, boolean typeSubstitutionCapable) {
        if(typeSubstitutionCapable)
            return loaderWithSubst;
        else
            return loader;
    }

    public Transducer<BeanT> getTransducer() {
        return xducer;
    }
}
