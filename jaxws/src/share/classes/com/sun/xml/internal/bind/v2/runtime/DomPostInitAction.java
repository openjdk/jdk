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

import java.util.HashSet;
import java.util.Set;

import javax.xml.XMLConstants;

import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Post-init action for {@link MarshallerImpl} that incorporate the in-scope namespace bindings
 * from a DOM node.
 *
 * TODO: do we really need this? think about a better way to put this logic back into marshaller.
 *
 * @author Kohsuke Kawaguchi
 */
final class DomPostInitAction implements Runnable {

    private final Node node;
    private final XMLSerializer serializer;

    DomPostInitAction(Node node, XMLSerializer serializer) {
        this.node = node;
        this.serializer = serializer;
    }

    // declare the currently in-scope namespace bindings
    public void run() {
        Set<String> declaredPrefixes = new HashSet<String>();
        for( Node n=node; n!=null && n.getNodeType()==Node.ELEMENT_NODE; n=n.getParentNode() ) {
            NamedNodeMap atts = n.getAttributes();
            if(atts==null)      continue; // broken DOM. but be graceful.
            for( int i=0; i<atts.getLength(); i++ ) {
                Attr a = (Attr)atts.item(i);
                String nsUri = a.getNamespaceURI();
                if(nsUri==null || !nsUri.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI))
                    continue;   // not a namespace declaration
                String prefix = a.getLocalName();
                if(prefix==null)
                    continue;   // broken DOM. skip to be safe
                if(prefix.equals("xmlns")) {
                    prefix = "";
                }
                String value = a.getValue();
                if(value==null)
                    continue;   // broken DOM. skip to be safe
                if(declaredPrefixes.add(prefix)) {
                    serializer.addInscopeBinding(value,prefix);
                }
            }
        }
    }
}
