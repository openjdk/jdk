/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

/*
 * DOMUtils.java
 *
 * Created on May 7th 2002
 */

package com.sun.tools.internal.xjc.util;

import java.util.ArrayList;

import javax.xml.namespace.QName;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 *
 * @author  Vivek Pandey
 * @version 1.0
 *
 */
public class DOMUtils {
    /** Gets the fist child of the given name, or null. */
    public static Element getFirstChildElement( Element parent, String nsUri, String localPart ) {
        NodeList children = parent.getChildNodes();
        for( int i=0; i<children.getLength(); i++ ) {
            Node item = children.item(i);
            if(!(item instanceof Element ))     continue;

            if(nsUri.equals(item.getNamespaceURI())
            && localPart.equals(item.getLocalName()) )
                return (Element)item;
        }
        return null;
    }

    /** Gets the child elements of the given name. */
    public static Element[] getChildElements(Element parent, String nsUri, String localPart ) {
        ArrayList a = new ArrayList();
        NodeList children = parent.getChildNodes();
        for( int i=0; i<children.getLength(); i++ ) {
            Node item = children.item(i);
            if(!(item instanceof Element ))     continue;

            if(nsUri.equals(item.getNamespaceURI())
            && localPart.equals(item.getLocalName()) )
                a.add(item);
        }
        return (Element[]) a.toArray(new Element[a.size()]);
    }

    /** Gets all the child elements. */
    public static Element[] getChildElements( Element parent ) {
        ArrayList a = new ArrayList();
        NodeList children = parent.getChildNodes();
        for( int i=0; i<children.getLength(); i++ ) {
            Node item = children.item(i);
            if(!(item instanceof Element ))     continue;

            a.add(item);
        }
        return (Element[]) a.toArray(new Element[a.size()]);
    }


  public static String getElementText(Element element) throws DOMException{
    for (Node child = element.getFirstChild(); child != null;
     child = child.getNextSibling()) {
      if(child.getNodeType() == Node.TEXT_NODE)
    return child.getNodeValue();
    }
    return element.getNodeValue();
  }

  public static Element getElement(Document parent, String name){
    NodeList children = parent.getElementsByTagName(name);
    if(children.getLength() >= 1)
      return (Element)children.item(0);
    return null;
  }

  public static Element getElement(Document parent, QName qname){
    NodeList children = parent.getElementsByTagNameNS(qname.getNamespaceURI(), qname.getLocalPart());
    if(children.getLength() >= 1)
      return (Element)children.item(0);
    return null;
  }

  public static Element getElement(Document parent, String namespaceURI,
                       String localName) {
    NodeList children = parent.getElementsByTagNameNS(namespaceURI, localName);
    if(children.getLength() >= 1)
      return (Element)children.item(0);
    return null;
  }

    public static Element[] getElements(NodeList children) {
        Element[] elements = null;
        int len = 0;
        for (int i = 0; i < children.getLength(); ++i) {
            if (elements == null)
                elements = new Element[1];
            if (elements.length == len) {
                Element[] buf = new Element[elements.length + 1];
                System.arraycopy(elements, 0, buf, 0, elements.length);
                elements = buf;
            }
            elements[len++] = (Element)children.item(i);
        }
        return elements;
    }
}
