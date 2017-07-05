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
import java.util.Collection;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.util.QNameMap;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimePropertyInfo;
import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.Name;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;
import com.sun.xml.internal.bind.v2.runtime.reflect.Lister;
import com.sun.xml.internal.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.ChildLoader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.TagName;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Loader;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Receiver;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Scope;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.UnmarshallingContext;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.XsiNilLoader;

import org.xml.sax.SAXException;

/**
 * Commonality between {@link ArrayElementProperty} and {@link ArrayReferenceNodeProperty}.
 *
 * Mostly handles the unmarshalling of the wrapper element.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class ArrayERProperty<BeanT,ListT,ItemT> extends ArrayProperty<BeanT,ListT,ItemT> {

    /**
     * Wrapper tag name if any, or null.
     */
    protected final Name wrapperTagName;

    /**
     * True if the wrapper tag name is nillable.
     * Always false if {@link #wrapperTagName}==null.
     */
    protected final boolean isWrapperNillable;

    protected ArrayERProperty(JAXBContextImpl grammar, RuntimePropertyInfo prop, QName tagName, boolean isWrapperNillable) {
        super(grammar,prop);
        if(tagName==null)
            this.wrapperTagName = null;
        else
            this.wrapperTagName = grammar.nameBuilder.createElementName(tagName);
        this.isWrapperNillable = isWrapperNillable;
    }

    /**
     * Used to handle the collection wrapper element.
     */
    private static final class ItemsLoader extends Loader {

        private final Accessor acc;
        private final Lister lister;

        public ItemsLoader(Accessor acc, Lister lister, QNameMap<ChildLoader> children) {
            super(false);
            this.acc = acc;
            this.lister = lister;
            this.children = children;
        }

        @Override
        public void startElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
            UnmarshallingContext context = state.getContext();
            context.startScope(1);
            // inherit the target so that our children can access its target
            state.target = state.prev.target;

            // start it now, so that even if there's no children we can still return empty collection
            context.getScope(0).start(acc,lister);
        }

        private final QNameMap<ChildLoader> children;

        @Override
        public void childElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
            ChildLoader child = children.get(ea.uri,ea.local);
            if(child!=null) {
                state.loader = child.loader;
                state.receiver = child.receiver;
            } else {
                super.childElement(state,ea);
            }
        }

        @Override
        public void leaveElement(UnmarshallingContext.State state, TagName ea) throws SAXException {
            state.getContext().endScope(1);
        }

        @Override
        public Collection<QName> getExpectedChildElements() {
            return children.keySet();
        }
    }

    public final void serializeBody(BeanT o, XMLSerializer w, Object outerPeer) throws SAXException, AccessorException, IOException, XMLStreamException {
        ListT list = acc.get(o);

        if(list!=null) {
            if(wrapperTagName!=null) {
                w.startElement(wrapperTagName,null);
                w.endNamespaceDecls(list);
                w.endAttributes();
            }

            serializeListBody(o,w,list);

            if(wrapperTagName!=null)
                w.endElement();
        } else {
            // list is null
            if(isWrapperNillable) {
                w.startElement(wrapperTagName,null);
                w.writeXsiNilTrue();
                w.endElement();
            } // otherwise don't print the wrapper tag name
        }
    }

    /**
     * Serializses the items of the list.
     * This method is invoked after the necessary wrapper tag is produced (if necessary.)
     *
     * @param list
     *      always non-null.
     */
    protected abstract void serializeListBody(BeanT o, XMLSerializer w, ListT list) throws IOException, XMLStreamException, SAXException, AccessorException;

    /**
     * Creates the unmarshaller to unmarshal the body.
     */
    protected abstract void createBodyUnmarshaller(UnmarshallerChain chain, QNameMap<ChildLoader> loaders);


    public final void buildChildElementUnmarshallers(UnmarshallerChain chain, QNameMap<ChildLoader> loaders) {
        if(wrapperTagName!=null) {
            UnmarshallerChain c = new UnmarshallerChain(chain.context);
            QNameMap<ChildLoader> m = new QNameMap<ChildLoader>();
            createBodyUnmarshaller(c,m);
            Loader loader = new ItemsLoader(acc, lister, m);
            if(isWrapperNillable || chain.context.allNillable)
                loader = new XsiNilLoader(loader);
            loaders.put(wrapperTagName,new ChildLoader(loader,null));
        } else {
            createBodyUnmarshaller(chain,loaders);
        }
    }

    /**
     * {@link Receiver} that puts the child object into the {@link Scope} object.
     */
    protected final class ReceiverImpl implements Receiver {
        private final int offset;

        protected ReceiverImpl(int offset) {
            this.offset = offset;
        }

        public void receive(UnmarshallingContext.State state, Object o) throws SAXException {
            state.getContext().getScope(offset).add(acc,lister,o);
        }
    }}
