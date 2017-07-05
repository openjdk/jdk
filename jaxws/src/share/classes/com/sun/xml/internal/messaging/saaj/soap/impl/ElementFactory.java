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
/*
 * $Id: ElementFactory.java,v 1.16 2006/01/27 12:49:35 vj135062 Exp $
 * $Revision: 1.16 $
 * $Date: 2006/01/27 12:49:35 $
 */


package com.sun.xml.internal.messaging.saaj.soap.impl;

import javax.xml.namespace.QName;
import javax.xml.soap.*;

import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import com.sun.xml.internal.messaging.saaj.soap.ver1_1.*;
import com.sun.xml.internal.messaging.saaj.soap.ver1_2.*;


public class ElementFactory {
    public static SOAPElement createElement(
        SOAPDocumentImpl ownerDocument,
        Name name) {
        return createElement(
            ownerDocument,
            name.getLocalName(),
            name.getPrefix(),
            name.getURI());
    }
    public static SOAPElement createElement(
        SOAPDocumentImpl ownerDocument,
        QName name) {
        return createElement(
            ownerDocument,
            name.getLocalPart(),
            name.getPrefix(),
            name.getNamespaceURI());
    }

    public static SOAPElement createElement(
        SOAPDocumentImpl ownerDocument,
        String localName,
        String prefix,
        String namespaceUri) {


        if (ownerDocument == null) {
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                ownerDocument = new SOAPPart1_1Impl().getDocument();
            } else if (NameImpl.SOAP12_NAMESPACE.equals(namespaceUri)) {
                ownerDocument = new SOAPPart1_2Impl().getDocument();
            } else {
                ownerDocument = new SOAPDocumentImpl(null);
            }
        }

        SOAPElement newElement =
            createNamedElement(ownerDocument, localName, prefix, namespaceUri);

        return newElement != null
            ? newElement
            : new ElementImpl(
                ownerDocument,
                namespaceUri,
                NameImpl.createQName(prefix, localName));
    }

    public static SOAPElement createNamedElement(
        SOAPDocumentImpl ownerDocument,
        String localName,
        String prefix,
        String namespaceUri) {

        if (prefix == null) {
            prefix = NameImpl.SOAP_ENVELOPE_PREFIX;
        }

        if (localName.equalsIgnoreCase("Envelope")) {
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                return new Envelope1_1Impl(ownerDocument, prefix);
            } else if (NameImpl.SOAP12_NAMESPACE.equals(namespaceUri)) {
                return new Envelope1_2Impl(ownerDocument, prefix);
            }
        }
        if (localName.equalsIgnoreCase("Body")) {
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                return new Body1_1Impl(ownerDocument, prefix);
            } else if (NameImpl.SOAP12_NAMESPACE.equals(namespaceUri)) {
                return new Body1_2Impl(ownerDocument, prefix);
            }
        }
        if (localName.equalsIgnoreCase("Header")) {
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                return new Header1_1Impl(ownerDocument, prefix);
            } else if (NameImpl.SOAP12_NAMESPACE.equals(namespaceUri)) {
                return new Header1_2Impl(ownerDocument, prefix);
            }
        }
        if (localName.equalsIgnoreCase("Fault")) {
            SOAPFault fault = null;
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                fault = new Fault1_1Impl(ownerDocument, prefix);
            } else if (NameImpl.SOAP12_NAMESPACE.equals(namespaceUri)) {
                fault = new Fault1_2Impl(ownerDocument, prefix);
            }

            if (fault != null) {
//                try {
//                    fault.addNamespaceDeclaration(
//                        NameImpl.SOAP_ENVELOPE_PREFIX,
//                        SOAPConstants.URI_NS_SOAP_ENVELOPE);
//                    fault.setFaultCode(
//                        NameImpl.create(
//                            "Server",
//                            NameImpl.SOAP_ENVELOPE_PREFIX,
//                            SOAPConstants.URI_NS_SOAP_ENVELOPE));
//                    fault.setFaultString(
//                        "Fault string, and possibly fault code, not set");
//                } catch (SOAPException e) {
//                }
                return fault;
            }

        }
        if (localName.equalsIgnoreCase("Detail")) {
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                return new Detail1_1Impl(ownerDocument, prefix);
            } else if (NameImpl.SOAP12_NAMESPACE.equals(namespaceUri)) {
                return new Detail1_2Impl(ownerDocument, prefix);
            }
        }
        if (localName.equalsIgnoreCase("faultcode")
            || localName.equalsIgnoreCase("faultstring")
            || localName.equalsIgnoreCase("faultactor")) {
            // SOAP 1.2 does not have fault(code/string/actor)
            // So there is no else case required
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                return new FaultElement1_1Impl(ownerDocument,
                                               localName,
                                               prefix);
            }
        }

        return null;
    }

    protected static void invalidCreate(String msg) {
        throw new TreeException(msg);
    }
}
