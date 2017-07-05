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

package com.sun.xml.internal.bind.v2.runtime.reflect;

import javax.xml.bind.JAXBException;

import com.sun.xml.internal.bind.WhiteSpaceProcessor;
import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.runtime.Transducer;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;

import org.xml.sax.SAXException;

/**
 * {@link TransducedAccessor} for a list simple type.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ListTransducedAccessorImpl<BeanT,ListT,ItemT,PackT> extends DefaultTransducedAccessor<BeanT> {
    /**
     * {@link Transducer} for each item type.
     */
    private final Transducer<ItemT> xducer;
    /**
     * {@link Lister} for handling list of tokens.
     */
    private final Lister<BeanT,ListT,ItemT,PackT> lister;
    /**
     * {@link Accessor} to get/set the list.
     */
    private final Accessor<BeanT,ListT> acc;

    public ListTransducedAccessorImpl(Transducer<ItemT> xducer, Accessor<BeanT,ListT> acc, Lister<BeanT,ListT,ItemT,PackT> lister) {
        this.xducer = xducer;
        this.lister = lister;
        this.acc = acc;
    }

    public boolean useNamespace() {
        return xducer.useNamespace();
    }

    public void declareNamespace(BeanT bean, XMLSerializer w) throws AccessorException, SAXException {
        ListT list = acc.get(bean);

        if(list!=null) {
           ListIterator<ItemT> itr = lister.iterator(list, w);

            while(itr.hasNext()) {
                try {
                    ItemT item = itr.next();
                    if (item != null) {
                        xducer.declareNamespace(item,w);
                    }
                } catch (JAXBException e) {
                    w.reportError(null,e);
                }
            }
        }
    }

    // TODO: this is inefficient, consider a redesign
    // perhaps we should directly write to XMLSerializer,
    // or maybe add more methods like writeLeafElement.
    public String print(BeanT o) throws AccessorException, SAXException {
        ListT list = acc.get(o);

        if(list==null)
            return null;

        StringBuilder buf = new StringBuilder();
        XMLSerializer w = XMLSerializer.getInstance();
        ListIterator<ItemT> itr = lister.iterator(list, w);

        while(itr.hasNext()) {
            try {
                ItemT item = itr.next();
                if (item != null) {
                    if(buf.length()>0)  buf.append(' ');
                    buf.append(xducer.print(item));
                }
            } catch (JAXBException e) {
                w.reportError(null,e);
            }
        }
        return buf.toString();
    }

    private void processValue(BeanT bean, CharSequence s) throws AccessorException, SAXException {
        PackT pack = lister.startPacking(bean,acc);

        int idx = 0;
        int len = s.length();

        while(true) {
            int p = idx;
            while( p<len && !WhiteSpaceProcessor.isWhiteSpace(s.charAt(p)) )
                p++;

            CharSequence token = s.subSequence(idx,p);
            if (!token.equals(""))
                lister.addToPack(pack,xducer.parse(token));

            if(p==len)      break;  // done

            while( p<len && WhiteSpaceProcessor.isWhiteSpace(s.charAt(p)) )
                p++;
            if(p==len)      break;  // done

            idx = p;
        }

        lister.endPacking(pack,bean,acc);
    }

    public void parse(BeanT bean, CharSequence lexical) throws AccessorException, SAXException {
        processValue(bean,lexical);
    }

    public boolean hasValue(BeanT bean) throws AccessorException {
        return acc.get(bean)!=null;
    }
}
