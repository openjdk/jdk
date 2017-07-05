/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.xml.internal.messaging.saaj.util.stax.SaajStaxWriter;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import com.sun.xml.internal.org.jvnet.staxex.util.XMLStreamReaderToXMLStreamWriter;


/**
 * StaxBridge builds Envelope using a XMLStreamReaderToXMLStreamWriter
 *
 * @author shih-chang.chen@oracle.com
 */
public abstract class StaxBridge {
        protected SaajStaxWriter saajWriter;
        protected XMLStreamReaderToXMLStreamWriter readerToWriter;
        protected XMLStreamReaderToXMLStreamWriter.Breakpoint breakpoint;


        public StaxBridge(SOAPPartImpl soapPart) throws SOAPException {
                readerToWriter = new XMLStreamReaderToXMLStreamWriter();
                saajWriter = new SaajStaxWriter(soapPart.message, soapPart.getSOAPNamespace());
        }

        public void bridgeEnvelopeAndHeaders() throws XMLStreamException {
                readerToWriter.bridge(breakpoint);
        }

        public void bridgePayload() throws XMLStreamException {
                readerToWriter.bridge(breakpoint);
        }

    abstract public XMLStreamReader getPayloadReader();

    abstract public QName getPayloadQName();

    abstract public String getPayloadAttributeValue(String attName) ;

    abstract public String getPayloadAttributeValue(QName attName) ;
}
