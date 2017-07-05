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

package com.sun.xml.internal.ws.message;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Sniffs the root element name and its attributes from SAX events.
 *
 * @author Kohsuke Kawaguchi
 */
public final class RootElementSniffer extends DefaultHandler {
    private String nsUri = "##error";
    private String localName = "##error";
    private Attributes atts;

    private final boolean parseAttributes;

    public RootElementSniffer(boolean parseAttributes) {
        this.parseAttributes = parseAttributes;
    }

    public RootElementSniffer() {
        this(true);
    }

    public void startElement(String uri, String localName, String qName, Attributes a) throws SAXException {
        this.nsUri = uri;
        this.localName = localName;

        if(parseAttributes) {
            if(a.getLength()==0)    // often there's no attribute
                this.atts = EMPTY_ATTRIBUTES;
            else
                this.atts = new AttributesImpl(a);
        }

        // no need to parse any further.
        throw aSAXException;
    }

    public String getNsUri() {
        return nsUri;
    }

    public String getLocalName() {
        return localName;
    }

    public Attributes getAttributes() {
        return atts;
    }

    private static final SAXException aSAXException = new SAXException();
    private static final Attributes EMPTY_ATTRIBUTES = new AttributesImpl();
}
