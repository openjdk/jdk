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

package com.sun.xml.internal.ws.message;

import com.sun.xml.internal.ws.encoding.soap.SOAP12Constants;
import com.sun.xml.internal.ws.encoding.soap.SOAPConstants;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.namespace.QName;

/**
 * Parses the SOAP message in order to get {@link QName} of a payload element.
 * It parses message until it
 *
 * @author Miroslav Kos (miroslav.kos at oracle.com)
 */
public class PayloadElementSniffer extends DefaultHandler {

    // flag if the last element was SOAP body
    private boolean bodyStarted;

    // payloadQName used as a return value
    private QName payloadQName;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {

        if (bodyStarted) {
            payloadQName = new QName(uri, localName);
            // we have what we wanted - let's skip rest of parsing ...
            throw new SAXException("Payload element found, interrupting the parsing process.");
        }

        // check for both SOAP 1.1/1.2
        if (equalsQName(uri, localName, SOAPConstants.QNAME_SOAP_BODY) ||
                equalsQName(uri, localName, SOAP12Constants.QNAME_SOAP_BODY)) {
            bodyStarted = true;
        }

    }

    private boolean equalsQName(String uri, String localName, QName qname) {
        return qname.getLocalPart().equals(localName) &&
                qname.getNamespaceURI().equals(uri);
    }

    public QName getPayloadQName() {
        return payloadQName;
    }
}
