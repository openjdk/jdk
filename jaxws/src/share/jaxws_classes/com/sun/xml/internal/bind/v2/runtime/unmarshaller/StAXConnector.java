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

package com.sun.xml.internal.bind.v2.runtime.unmarshaller;

import javax.xml.bind.ValidationEventLocator;
import javax.xml.bind.helpers.ValidationEventLocatorImpl;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
abstract class StAXConnector {
    public abstract void bridge() throws XMLStreamException;


    // event sink
    protected final XmlVisitor visitor;

    protected final UnmarshallingContext context;
    protected final XmlVisitor.TextPredictor predictor;

    private final class TagNameImpl extends TagName {
        public String getQname() {
            return StAXConnector.this.getCurrentQName();
        }
    }

    protected final TagName tagName = new TagNameImpl();

    protected StAXConnector(XmlVisitor visitor) {
        this.visitor = visitor;
        context = visitor.getContext();
        predictor = visitor.getPredictor();
    }

    /**
     * Gets the {@link Location}. Used for implementing the line number information.
     * @return must not null.
     */
    protected abstract Location getCurrentLocation();

    /**
     * Gets the QName of the current element.
     */
    protected abstract String getCurrentQName();

    protected final void handleStartDocument(NamespaceContext nsc) throws SAXException {
        visitor.startDocument(new LocatorEx() {
            public ValidationEventLocator getLocation() {
                return new ValidationEventLocatorImpl(this);
            }
            public int getColumnNumber() {
                return getCurrentLocation().getColumnNumber();
            }
            public int getLineNumber() {
                return getCurrentLocation().getLineNumber();
            }
            public String getPublicId() {
                return getCurrentLocation().getPublicId();
            }
            public String getSystemId() {
                return getCurrentLocation().getSystemId();
            }
        },nsc);
    }

    protected final void handleEndDocument() throws SAXException {
        visitor.endDocument();
    }

    protected static String fixNull(String s) {
        if(s==null) return "";
        else        return s;
    }

    protected final String getQName(String prefix, String localName) {
        if(prefix==null || prefix.length()==0)
            return localName;
        else
            return prefix + ':' + localName;
    }
}
