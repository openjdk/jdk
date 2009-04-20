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
package com.sun.tools.internal.xjc.reader.dtd.bindinfo;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Kohsuke Kawaguchi
 */
public final class DOMUtil {
    final static String getAttribute(Element e,String attName) {
        if(e.getAttributeNode(attName)==null)   return null;
        return e.getAttribute(attName);
    }

    public static String getAttribute(Element e, String nsUri, String local) {
        if(e.getAttributeNodeNS(nsUri,local)==null) return null;
        return e.getAttributeNS(nsUri,local);
    }

    public static Element getElement(Element e, String nsUri, String localName) {
        NodeList l = e.getChildNodes();
        for(int i=0;i<l.getLength();i++) {
            Node n = l.item(i);
            if(n.getNodeType()==Node.ELEMENT_NODE) {
                Element r = (Element)n;
                if(equals(r.getLocalName(),localName) && equals(fixNull(r.getNamespaceURI()),nsUri))
                    return r;
            }
        }
        return null;
    }

    /**
     * Used for defensive string comparisons, as many DOM methods often return null
     * depending on how they are created.
     */
    private static boolean equals(String a,String b) {
        if(a==b)    return true;
        if(a==null || b==null)  return false;
        return a.equals(b);
    }

    /**
     * DOM API returns null for the default namespace whereas it should return "".
     */
    private static String fixNull(String s) {
        if(s==null) return "";
        else        return s;
    }

    public static Element getElement(Element e, String localName) {
        return getElement(e,"",localName);
    }

    public static List<Element> getChildElements(Element e) {
        List<Element> r = new ArrayList<Element>();
        NodeList l = e.getChildNodes();
        for(int i=0;i<l.getLength();i++) {
            Node n = l.item(i);
            if(n.getNodeType()==Node.ELEMENT_NODE)
                r.add((Element)n);
        }
        return r;
    }

    public static List<Element> getChildElements(Element e,String localName) {
        List<Element> r = new ArrayList<Element>();
        NodeList l = e.getChildNodes();
        for(int i=0;i<l.getLength();i++) {
            Node n = l.item(i);
            if(n.getNodeType()==Node.ELEMENT_NODE) {
                Element c = (Element)n;
                if(c.getLocalName().equals(localName))
                    r.add(c);
            }
        }
        return r;
    }
}
