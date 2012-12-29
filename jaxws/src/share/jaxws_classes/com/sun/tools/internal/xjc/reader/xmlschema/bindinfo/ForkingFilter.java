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

package com.sun.tools.internal.xjc.reader.xmlschema.bindinfo;

import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import java.util.ArrayList;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * {@link XMLFilter} that can fork an event to another {@link ContentHandler}
 * in the middle.
 *
 * <p>
 * The side handler receives SAX events before the next handler in the filter chain does.
 *
 * @author Kohsuke Kawaguchi
 */
public class ForkingFilter extends XMLFilterImpl {

    /**
     * Non-null if we are also forking events to this handler.
     */
    private ContentHandler side;

    /**
     * The depth of the current element that the {@link #side} handler
     * is seeing.
     */
    private int depth;

    /**
     * In-scope namespace mapping.
     * namespaces[2n  ] := prefix
     * namespaces[2n+1] := namespace URI
     */
    private final ArrayList<String> namespaces = new ArrayList<String>();

    private Locator loc;

    public ForkingFilter() {
    }

    public ForkingFilter(ContentHandler next) {
        setContentHandler(next);
    }

    public ContentHandler getSideHandler() {
        return side;
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        super.setDocumentLocator(locator);
        this.loc = locator;
    }

    public Locator getDocumentLocator() {
        return loc;
    }

    @Override
    public void startDocument() throws SAXException {
        reset();
        super.startDocument();
    }

    private void reset() {
        namespaces.clear();
        side = null;
        depth = 0;
    }

    @Override
    public void endDocument() throws SAXException {
        loc = null;
        reset();
        super.endDocument();
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        if (WellKnownNamespace.XML_NAMESPACE_URI.equals(uri)) return; //xml prefix shall not be declared based on jdk api javadoc
        if(side!=null)
            side.startPrefixMapping(prefix,uri);
        namespaces.add(prefix);
        namespaces.add(uri);
        super.startPrefixMapping(prefix,uri);
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        if ("xml".equals(prefix)) return; //xml prefix shall not be declared based on jdk api javadoc
        if(side!=null)
            side.endPrefixMapping(prefix);
        super.endPrefixMapping(prefix);
        namespaces.remove(namespaces.size()-1);
        namespaces.remove(namespaces.size()-1);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if(side!=null) {
            side.startElement(uri,localName,qName,atts);
            depth++;
        }
        super.startElement(uri, localName, qName, atts);
    }

    /**
     * Starts the event forking.
     */
    public void startForking(String uri, String localName, String qName, Attributes atts, ContentHandler side) throws SAXException {
        if(this.side!=null)     throw new IllegalStateException();  // can't fork to two handlers

        this.side = side;
        depth = 1;
        side.setDocumentLocator(loc);
        side.startDocument();
        for( int i=0; i<namespaces.size(); i+=2 )
            side.startPrefixMapping(namespaces.get(i),namespaces.get(i+1));
        side.startElement(uri,localName,qName,atts);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if(side!=null) {
            side.endElement(uri,localName,qName);
            depth--;
            if(depth==0) {
                for( int i=namespaces.size()-2; i>=0; i-=2 )
                    side.endPrefixMapping(namespaces.get(i));
                side.endDocument();
                side = null;
            }
        }
        super.endElement(uri, localName, qName);
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        if(side!=null)
            side.characters(ch, start, length);
        super.characters(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(char ch[], int start, int length) throws SAXException {
        if(side!=null)
            side.ignorableWhitespace(ch, start, length);
        super.ignorableWhitespace(ch, start, length);
    }
}
