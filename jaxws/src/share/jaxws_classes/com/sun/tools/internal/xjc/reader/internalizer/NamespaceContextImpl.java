/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */

package com.sun.tools.internal.xjc.reader.internalizer;

import java.util.Iterator;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Implements {@link NamespaceContext} by looking at the in-scope
 * namespace binding of a DOM element.
 *
 * @author Kohsuke Kawaguchi
 */
final class NamespaceContextImpl implements NamespaceContext {
    private final Element e;

    public NamespaceContextImpl(Element e) {
        this.e = e;
    }

    public String getNamespaceURI(String prefix) {
        Node parent = e;
        String namespace = null;
        final String prefixColon = prefix + ':';

        if (prefix.equals("xml")) {
            namespace = XMLConstants.XML_NS_URI;
        } else {
            int type;

            while ((null != parent) && (null == namespace)
                    && (((type = parent.getNodeType()) == Node.ELEMENT_NODE)
                    || (type == Node.ENTITY_REFERENCE_NODE))) {
                if (type == Node.ELEMENT_NODE) {
                    if (parent.getNodeName().startsWith(prefixColon))
                        return parent.getNamespaceURI();
                    NamedNodeMap nnm = parent.getAttributes();

                    for (int i = 0; i < nnm.getLength(); i++) {
                        Node attr = nnm.item(i);
                        String aname = attr.getNodeName();
                        boolean isPrefix = aname.startsWith("xmlns:");

                        if (isPrefix || aname.equals("xmlns")) {
                            int index = aname.indexOf(':');
                            String p = isPrefix ? aname.substring(index + 1) : "";

                            if (p.equals(prefix)) {
                                namespace = attr.getNodeValue();

                                break;
                            }
                        }
                    }
                }

                parent = parent.getParentNode();
            }
        }

        if(prefix.equals(""))
            return "";  // default namespace
        return namespace;
    }

    public String getPrefix(String namespaceURI) {
        throw new UnsupportedOperationException();
    }

    public Iterator getPrefixes(String namespaceURI) {
        throw new UnsupportedOperationException();
    }
}
