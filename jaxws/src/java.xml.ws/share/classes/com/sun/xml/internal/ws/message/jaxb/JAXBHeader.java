/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.message.jaxb;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.XMLStreamException2;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.stream.buffer.MutableXMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBufferResult;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.encoding.SOAPBindingCodec;
import com.sun.xml.internal.ws.message.AbstractHeaderImpl;
import com.sun.xml.internal.ws.message.RootElementSniffer;
import com.sun.xml.internal.ws.spi.db.BindingContext;
import com.sun.xml.internal.ws.spi.db.XMLBridge;
import com.sun.xml.internal.ws.streaming.XMLStreamWriterUtil;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.util.JAXBResult;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPHeader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.OutputStream;

/**
 * {@link Header} whose physical data representation is a JAXB bean.
 *
 * @author Kohsuke Kawaguchi
 */
public final class JAXBHeader extends AbstractHeaderImpl {

    /**
     * The JAXB object that represents the header.
     */
    private final Object jaxbObject;

    private final XMLBridge bridge;

    // information about this header. lazily obtained.
    private String nsUri;
    private String localName;
    private Attributes atts;

    /**
     * Once the header is turned into infoset,
     * this buffer keeps it.
     */
    private XMLStreamBuffer infoset;

    public JAXBHeader(BindingContext context, Object jaxbObject) {
        this.jaxbObject = jaxbObject;
//        this.bridge = new MarshallerBridge(context);
        this.bridge = context.createFragmentBridge();

        if (jaxbObject instanceof JAXBElement) {
            JAXBElement e = (JAXBElement) jaxbObject;
            this.nsUri = e.getName().getNamespaceURI();
            this.localName = e.getName().getLocalPart();
        }
    }

    public JAXBHeader(XMLBridge bridge, Object jaxbObject) {
        this.jaxbObject = jaxbObject;
        this.bridge = bridge;

        QName tagName = bridge.getTypeInfo().tagName;
        this.nsUri = tagName.getNamespaceURI();
        this.localName = tagName.getLocalPart();
    }

    /**
     * Lazily parse the first element to obtain attribute values on it.
     */
    private void parse() {
        RootElementSniffer sniffer = new RootElementSniffer();
        try {
            bridge.marshal(jaxbObject,sniffer,null);
        } catch (JAXBException e) {
            // if it's due to us aborting the processing after the first element,
            // we can safely ignore this exception.
            //
            // if it's due to error in the object, the same error will be reported
            // when the readHeader() method is used, so we don't have to report
            // an error right now.
            nsUri = sniffer.getNsUri();
            localName = sniffer.getLocalName();
            atts = sniffer.getAttributes();
        }
    }


    public @NotNull String getNamespaceURI() {
        if(nsUri==null)
            parse();
        return nsUri;
    }

    public @NotNull String getLocalPart() {
        if(localName==null)
            parse();
        return localName;
    }

    public String getAttribute(String nsUri, String localName) {
        if(atts==null)
            parse();
        return atts.getValue(nsUri,localName);
    }

    public XMLStreamReader readHeader() throws XMLStreamException {
        if(infoset==null) {
            MutableXMLStreamBuffer buffer = new MutableXMLStreamBuffer();
            writeTo(buffer.createFromXMLStreamWriter());
            infoset = buffer;
        }
        return infoset.readAsXMLStreamReader();
    }

    public <T> T readAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
        try {
            JAXBResult r = new JAXBResult(unmarshaller);
            // bridge marshals a fragment, so we need to add start/endDocument by ourselves
            r.getHandler().startDocument();
            bridge.marshal(jaxbObject,r);
            r.getHandler().endDocument();
            return (T)r.getResult();
        } catch (SAXException e) {
            throw new JAXBException(e);
        }
    }
    /** @deprecated */
    public <T> T readAsJAXB(Bridge<T> bridge) throws JAXBException {
        return bridge.unmarshal(new JAXBBridgeSource(this.bridge,jaxbObject));
    }

        public <T> T readAsJAXB(XMLBridge<T> bond) throws JAXBException {
        return bond.unmarshal(new JAXBBridgeSource(this.bridge,jaxbObject),null);
        }

    public void writeTo(XMLStreamWriter sw) throws XMLStreamException {
        try {
            // Get the encoding of the writer
            String encoding = XMLStreamWriterUtil.getEncoding(sw);

            // Get output stream and use JAXB UTF-8 writer
            OutputStream os = bridge.supportOutputStream() ? XMLStreamWriterUtil.getOutputStream(sw) : null;
            if (os != null && encoding != null && encoding.equalsIgnoreCase(SOAPBindingCodec.UTF8_ENCODING)) {
                bridge.marshal(jaxbObject, os, sw.getNamespaceContext(), null);
            } else {
                bridge.marshal(jaxbObject,sw, null);
            }
        } catch (JAXBException e) {
            throw new XMLStreamException2(e);
        }
    }

    public void writeTo(SOAPMessage saaj) throws SOAPException {
        try {
            SOAPHeader header = saaj.getSOAPHeader();
            if (header == null)
                header = saaj.getSOAPPart().getEnvelope().addHeader();
            bridge.marshal(jaxbObject,header);
        } catch (JAXBException e) {
            throw new SOAPException(e);
        }
    }

    public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException {
        try {
            bridge.marshal(jaxbObject,contentHandler,null);
        } catch (JAXBException e) {
            SAXParseException x = new SAXParseException(e.getMessage(),null,null,-1,-1,e);
            errorHandler.fatalError(x);
            throw x;
        }
    }
}
