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

package com.sun.xml.internal.ws.message;

import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.api.BridgeContext;
import com.sun.xml.internal.bind.unmarshaller.DOMScanner;
import com.sun.xml.internal.messaging.saaj.packaging.mime.Header;
import com.sun.xml.internal.ws.streaming.DOMStreamReader;
import com.sun.xml.internal.ws.util.DOMUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/**
 * {@link Header} implementation for a DOM.
 *
 * @author Kohsuke Kawaguchi
 */
public class DOMHeader<N extends Element> extends AbstractHeaderImpl {
    protected final N node;

    private final String nsUri;
    private final String localName;

    public DOMHeader(N node) {
        assert node!=null;
        this.node = node;

        this.nsUri = fixNull(node.getNamespaceURI());
        this.localName = node.getLocalName();
    }


    public String getNamespaceURI() {
        return nsUri;
    }

    public String getLocalPart() {
        return localName;
    }

    public XMLStreamReader readHeader() throws XMLStreamException {
        DOMStreamReader r = new DOMStreamReader(node);
        r.nextTag();    // move ahead to the start tag
        return r;
    }

    public <T> T readAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
        return (T) unmarshaller.unmarshal(node);
    }

    public <T> T readAsJAXB(Bridge<T> bridge) throws JAXBException {
        return bridge.unmarshal(node);
    }

    public void writeTo(XMLStreamWriter w) throws XMLStreamException {
        DOMUtil.serializeNode(node, w);
    }

    private static String fixNull(String s) {
        if(s!=null)     return s;
        else            return "";
    }

    public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException {
        DOMScanner ds = new DOMScanner();
        ds.setContentHandler(contentHandler);
        ds.scan(node);
    }

    public String getAttribute(String nsUri, String localName) {
        if(nsUri.length()==0)   nsUri=null; // DOM wants null, not "".
        return node.getAttributeNS(nsUri,localName);
    }

    public void writeTo(SOAPMessage saaj) throws SOAPException {
        SOAPHeader header = saaj.getSOAPHeader();
        Node clone = header.getOwnerDocument().importNode(node,true);
        header.appendChild(clone);
    }

    @Override
    public String getStringContent() {
        return node.getTextContent();
    }
}
