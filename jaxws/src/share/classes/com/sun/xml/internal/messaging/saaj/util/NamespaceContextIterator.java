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



/**
*
* @author SAAJ RI Development Team
*/
package com.sun.xml.internal.messaging.saaj.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.w3c.dom.*;

public class NamespaceContextIterator implements Iterator {
    Node context;
    NamedNodeMap attributes = null;
    int attributesLength;
    int attributeIndex;
    Attr next = null;
    Attr last = null;
    boolean traverseStack = true;

    public NamespaceContextIterator(Node context) {
        this.context = context;
        findContextAttributes();
    }

    public NamespaceContextIterator(Node context, boolean traverseStack) {
        this(context);
        this.traverseStack = traverseStack;
    }

    protected void findContextAttributes() {
        while (context != null) {
            int type = context.getNodeType();
            if (type == Node.ELEMENT_NODE) {
                attributes = context.getAttributes();
                attributesLength = attributes.getLength();
                attributeIndex = 0;
                return;
            } else {
                context = null;
            }
        }
    }

    protected void findNext() {
        while (next == null && context != null) {
            for (; attributeIndex < attributesLength; ++attributeIndex) {
                Node currentAttribute = attributes.item(attributeIndex);
                String attributeName = currentAttribute.getNodeName();
                if (attributeName.startsWith("xmlns")
                    && (attributeName.length() == 5
                        || attributeName.charAt(5) == ':')) {
                    next = (Attr) currentAttribute;
                    ++attributeIndex;
                    return;
                }
            }
            if (traverseStack) {
                context = context.getParentNode();
                findContextAttributes();
            } else {
                context = null;
            }
        }
    }

    public boolean hasNext() {
        findNext();
        return next != null;
    }

    public Object next() {
        return getNext();
    }

    public Attr nextNamespaceAttr() {
        return getNext();
    }

    protected Attr getNext() {
        findNext();
        if (next == null) {
            throw new NoSuchElementException();
        }
        last = next;
        next = null;
        return last;
    }

    public void remove() {
        if (last == null) {
            throw new IllegalStateException();
        }
        ((Element) context).removeAttributeNode(last);
    }

}
