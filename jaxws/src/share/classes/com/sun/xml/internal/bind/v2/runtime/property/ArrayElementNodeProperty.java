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

import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.v2.model.runtime.RuntimeElementPropertyInfo;
import com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.internal.bind.v2.runtime.JaxBeanInfo;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;

import org.xml.sax.SAXException;

/**
 * {@link ArrayProperty} that contains node values.
 *
 * @author Kohsuke Kawaguchi
 */
final class ArrayElementNodeProperty<BeanT,ListT,ItemT> extends ArrayElementProperty<BeanT,ListT,ItemT> {

    public ArrayElementNodeProperty(JAXBContextImpl p, RuntimeElementPropertyInfo prop) {
        super(p, prop);
    }

    public void serializeItem(JaxBeanInfo expected, ItemT item, XMLSerializer w) throws SAXException, IOException, XMLStreamException {
        if(item==null) {
            w.writeXsiNilTrue();
        } else {
            w.childAsXsiType(item,fieldName,expected);
        }
    }
}
