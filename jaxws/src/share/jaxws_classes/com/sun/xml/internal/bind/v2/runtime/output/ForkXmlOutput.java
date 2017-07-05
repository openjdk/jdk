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
 */

package com.sun.xml.internal.bind.v2.runtime.output;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;
import com.sun.xml.internal.bind.v2.runtime.Name;

import org.xml.sax.SAXException;

/**
 * {@link XmlOutput} that writes to two {@link XmlOutput}s.
 * @author Kohsuke Kawaguchi
 */
public final class ForkXmlOutput extends XmlOutputAbstractImpl {
    private final XmlOutput lhs;
    private final XmlOutput rhs;

    public ForkXmlOutput(XmlOutput lhs, XmlOutput rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public void startDocument(XMLSerializer serializer, boolean fragment, int[] nsUriIndex2prefixIndex, NamespaceContextImpl nsContext) throws IOException, SAXException, XMLStreamException {
        lhs.startDocument(serializer, fragment, nsUriIndex2prefixIndex, nsContext);
        rhs.startDocument(serializer, fragment, nsUriIndex2prefixIndex, nsContext);
    }

    @Override
    public void endDocument(boolean fragment) throws IOException, SAXException, XMLStreamException {
        lhs.endDocument(fragment);
        rhs.endDocument(fragment);
    }

    @Override
    public void beginStartTag(Name name) throws IOException, XMLStreamException {
        lhs.beginStartTag(name);
        rhs.beginStartTag(name);
    }

    @Override
    public void attribute(Name name, String value) throws IOException, XMLStreamException {
        lhs.attribute(name, value);
        rhs.attribute(name, value);
    }

    @Override
    public void endTag(Name name) throws IOException, SAXException, XMLStreamException {
        lhs.endTag(name);
        rhs.endTag(name);
    }

    public void beginStartTag(int prefix, String localName) throws IOException, XMLStreamException {
        lhs.beginStartTag(prefix,localName);
        rhs.beginStartTag(prefix,localName);
    }

    public void attribute(int prefix, String localName, String value) throws IOException, XMLStreamException {
        lhs.attribute(prefix,localName,value);
        rhs.attribute(prefix,localName,value);
    }

    public void endStartTag() throws IOException, SAXException {
        lhs.endStartTag();
        rhs.endStartTag();
    }

    public void endTag(int prefix, String localName) throws IOException, SAXException, XMLStreamException {
        lhs.endTag(prefix,localName);
        rhs.endTag(prefix,localName);
    }

    public void text(String value, boolean needsSeparatingWhitespace) throws IOException, SAXException, XMLStreamException {
        lhs.text(value,needsSeparatingWhitespace);
        rhs.text(value,needsSeparatingWhitespace);
    }

    public void text(Pcdata value, boolean needsSeparatingWhitespace) throws IOException, SAXException, XMLStreamException {
        lhs.text(value,needsSeparatingWhitespace);
        rhs.text(value,needsSeparatingWhitespace);
    }
}
