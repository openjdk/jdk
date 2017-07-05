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

package com.sun.tools.internal.xjc.util;

import org.xml.sax.helpers.XMLFilterImpl;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.XMLFilter;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.Attributes;

/**
 * {@link XMLFilter} that can cut sub-trees.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class SubtreeCutter extends XMLFilterImpl {
    /**
     * When we are pruning a sub tree, this field holds the depth of
     * elements that are being cut. Used to resume event forwarding.
     *
     * As long as this value is 0, we will pass through data.
     */
    private int cutDepth=0;


    /**
     * This object will receive SAX events while a sub tree is being
     * pruned.
     */
    private static final ContentHandler stub = new DefaultHandler();

    /**
     * This field remembers the user-specified ContentHandler.
     * So that we can restore it once the sub tree is completely pruned.
     */
    private ContentHandler next;


    public void startDocument() throws SAXException {
        cutDepth=0;
        super.startDocument();
    }

    public boolean isCutting() {
        return cutDepth>0;
    }

    /**
     * Starts cutting a sub-tree. Should be called from within the
     * {@link #startElement(String, String, String, Attributes)} implementation
     * before the execution is passed to {@link SubtreeCutter#startElement(String, String, String, Attributes)} .
     * The current element will be cut.
     */
    public void startCutting() {
        super.setContentHandler(stub);
        cutDepth=1;
    }

    public void setContentHandler(ContentHandler handler) {
        next = handler;
        // changes take effect immediately unless the sub-tree is being pruned
        if(getContentHandler()!=stub)
            super.setContentHandler(handler);
    }

    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if(cutDepth>0)
            cutDepth++;
        super.startElement(uri, localName, qName, atts);
    }

    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        super.endElement(namespaceURI, localName, qName);

        if( cutDepth!=0 ) {
            cutDepth--;
            if( cutDepth == 1 ) {
                // pruning completed. restore the user handler
                super.setContentHandler(next);
                cutDepth=0;
            }
        }
    }
}
