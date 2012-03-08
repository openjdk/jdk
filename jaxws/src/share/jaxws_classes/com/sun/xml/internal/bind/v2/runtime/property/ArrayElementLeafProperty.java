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

package com.sun.xml.internal.bind.v2.runtime.property;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.v2.model.runtime.RuntimeElementPropertyInfo;
import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.JaxBeanInfo;
import com.sun.xml.internal.bind.v2.runtime.Transducer;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;

import org.xml.sax.SAXException;

/**
 * {@link ArrayProperty} that contains only one leaf type.
 *
 * <p>
 * This class is optimized for the case where there's only one kind of types
 * in the array and that type is a leaf type.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
final class ArrayElementLeafProperty<BeanT,ListT,ItemT> extends ArrayElementProperty<BeanT,ListT,ItemT> {

    private final Transducer<ItemT> xducer;

    public ArrayElementLeafProperty(JAXBContextImpl p, RuntimeElementPropertyInfo prop) {
        super(p, prop);

        // unless those are true, use the ArrayElementNodeProperty.
        assert prop.getTypes().size()==1;
//        assert prop.getTypes().get(0).getType().isLeaf(); // this assertion is incorrect in case it's IDREF

        xducer = prop.getTypes().get(0).getTransducer();
        assert xducer!=null;
    }

    public void serializeItem(JaxBeanInfo bi, ItemT item, XMLSerializer w) throws SAXException, AccessorException, IOException, XMLStreamException {
        xducer.declareNamespace(item,w);
        w.endNamespaceDecls(item);
        w.endAttributes();
        // this is leaf, so by definition there's no type substitution
        // if there's, we'll be using ArrayElementNodeProperty
        xducer.writeText(w,item,fieldName);
    }
}
