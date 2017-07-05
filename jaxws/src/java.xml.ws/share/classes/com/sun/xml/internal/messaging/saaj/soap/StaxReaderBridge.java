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
import javax.xml.stream.XMLStreamReader;

import com.sun.xml.internal.org.jvnet.staxex.util.XMLStreamReaderToXMLStreamWriter;

/**
 * StaxBridge builds Envelope using a XMLStreamReaderToXMLStreamWriter
 *
 * @author shih-chang.chen@oracle.com
 */
public class StaxReaderBridge extends StaxBridge {
        private XMLStreamReader in;

        public StaxReaderBridge(XMLStreamReader reader, SOAPPartImpl soapPart) throws SOAPException {
                super(soapPart);
                in = reader;
                final String soapEnvNS = soapPart.getSOAPNamespace();
                breakpoint =  new XMLStreamReaderToXMLStreamWriter.Breakpoint(reader, saajWriter) {
                        boolean seenBody = false;
                        boolean stopedAtBody = false;
                    public boolean proceedBeforeStartElement()  {
                        if (stopedAtBody) return true;
                        if (seenBody) {
                                stopedAtBody = true;
                                return false;
                        }
                            if ("Body".equals(reader.getLocalName()) && soapEnvNS.equals(reader.getNamespaceURI()) ){
                                seenBody = true;
                            }
                            return true;
                    }
                };
        }

    public XMLStreamReader getPayloadReader() {
        return in;
    }

    public QName getPayloadQName() {
        return (in.getEventType() == XMLStreamConstants.START_ELEMENT) ? in.getName() : null;
    }

    public String getPayloadAttributeValue(String attName) {
        return (in.getEventType() == XMLStreamConstants.START_ELEMENT) ? in.getAttributeValue(null, attName) : null;
    }

    public String getPayloadAttributeValue(QName attName) {
        return (in.getEventType() == XMLStreamConstants.START_ELEMENT) ? in.getAttributeValue(attName.getNamespaceURI(), attName.getLocalPart()) : null;
    }
}
