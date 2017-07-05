/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.messaging.saaj.soap;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.sun.xml.internal.messaging.saaj.LazyEnvelopeSource;
import com.sun.xml.internal.org.jvnet.staxex.util.XMLStreamReaderToXMLStreamWriter;


/**
 * StaxBridge builds Envelope from LazyEnvelopeSource
 *
 * @author shih-chang.chen@oracle.com
 */
public class StaxLazySourceBridge extends StaxBridge {
        private LazyEnvelopeSource lazySource;

        public StaxLazySourceBridge(LazyEnvelopeSource src, SOAPPartImpl soapPart) throws SOAPException {
                super(soapPart);
                lazySource = src;
                final String soapEnvNS = soapPart.getSOAPNamespace();
                try {
                        breakpoint = new XMLStreamReaderToXMLStreamWriter.Breakpoint(src.readToBodyStarTag(), saajWriter) {
                                        public boolean proceedAfterStartElement()  {
                                                if ("Body".equals(reader.getLocalName()) && soapEnvNS.equals(reader.getNamespaceURI()) ){
                                                        return false;
                                                } else
                                                        return true;
                                        }
                                };
                } catch (XMLStreamException e) {
                        throw new SOAPException(e);
                }
        }

        @Override
    public XMLStreamReader getPayloadReader() {
        return lazySource.readPayload();
//              throw new UnsupportedOperationException();
    }

        @Override
    public QName getPayloadQName() {
        return lazySource.getPayloadQName();
    }

        @Override
    public String getPayloadAttributeValue(String attName) {
        if (lazySource.isPayloadStreamReader()) {
            XMLStreamReader reader = lazySource.readPayload();
            if (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
                return reader.getAttributeValue(null, attName);
            }
        }
        return null;
    }

        @Override
    public String getPayloadAttributeValue(QName attName) {
        if (lazySource.isPayloadStreamReader()) {
            XMLStreamReader reader = lazySource.readPayload();
            if (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
                return reader.getAttributeValue(attName.getNamespaceURI(), attName.getLocalPart());
            }
        }
        return null;
    }

        public void bridgePayload() throws XMLStreamException {
                //Assuming out is at Body
                writePayloadTo(saajWriter);
        }

        public void writePayloadTo(XMLStreamWriter writer) throws XMLStreamException {
        lazySource.writePayloadTo(writer);
    }
}
