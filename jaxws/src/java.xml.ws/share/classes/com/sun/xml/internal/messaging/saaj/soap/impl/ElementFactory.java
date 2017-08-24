/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.messaging.saaj.soap.impl;

import javax.xml.namespace.QName;
import javax.xml.soap.*;

import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import com.sun.xml.internal.messaging.saaj.soap.ver1_1.Body1_1Impl;
import com.sun.xml.internal.messaging.saaj.soap.ver1_1.Detail1_1Impl;
import com.sun.xml.internal.messaging.saaj.soap.ver1_1.Envelope1_1Impl;
import com.sun.xml.internal.messaging.saaj.soap.ver1_1.Fault1_1Impl;
import com.sun.xml.internal.messaging.saaj.soap.ver1_1.FaultElement1_1Impl;
import com.sun.xml.internal.messaging.saaj.soap.ver1_1.Header1_1Impl;
import com.sun.xml.internal.messaging.saaj.soap.ver1_1.SOAPPart1_1Impl;
import com.sun.xml.internal.messaging.saaj.soap.ver1_2.Body1_2Impl;
import com.sun.xml.internal.messaging.saaj.soap.ver1_2.Detail1_2Impl;
import com.sun.xml.internal.messaging.saaj.soap.ver1_2.Envelope1_2Impl;
import com.sun.xml.internal.messaging.saaj.soap.ver1_2.Fault1_2Impl;
import com.sun.xml.internal.messaging.saaj.soap.ver1_2.Header1_2Impl;
import com.sun.xml.internal.messaging.saaj.soap.ver1_2.SOAPPart1_2Impl;
import org.w3c.dom.Element;

import java.util.Objects;


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

    /**
     * Create element wrapper for existing DOM element.
     *
     * @param ownerDocument SOAP document wrapper not null
     * @param element DOM element not null
     * @return SOAP wrapper for DOM element
     */
    public static SOAPElement createElement(SOAPDocumentImpl ownerDocument, Element element) {
        Objects.requireNonNull(ownerDocument);
        Objects.requireNonNull(element);

        String localName = element.getLocalName();
        String namespaceUri = element.getNamespaceURI();
        String prefix = element.getPrefix();

        if ("Envelope".equalsIgnoreCase(localName)) {
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                return new Envelope1_1Impl(ownerDocument, element);
            } else if (NameImpl.SOAP12_NAMESPACE.equals(namespaceUri)) {
                return new Envelope1_2Impl(ownerDocument, element);
            }
        }
        if ("Body".equalsIgnoreCase(localName)) {
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                return new Body1_1Impl(ownerDocument, element);
            } else if (NameImpl.SOAP12_NAMESPACE.equals(namespaceUri)) {
                return new Body1_2Impl(ownerDocument, element);
            }
        }
        if ("Header".equalsIgnoreCase(localName)) {
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                return new Header1_1Impl(ownerDocument, element);
            } else if (NameImpl.SOAP12_NAMESPACE.equals(namespaceUri)) {
                return new Header1_2Impl(ownerDocument, element);
            }
        }
        if ("Fault".equalsIgnoreCase(localName)) {
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                return new Fault1_1Impl(ownerDocument, element);
            } else if (NameImpl.SOAP12_NAMESPACE.equals(namespaceUri)) {
                return new Fault1_2Impl(ownerDocument, element);
            }

        }
        if ("Detail".equalsIgnoreCase(localName)) {
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                return new Detail1_1Impl(ownerDocument, element);
            } else if (NameImpl.SOAP12_NAMESPACE.equals(namespaceUri)) {
                return new Detail1_2Impl(ownerDocument, element);
            }
        }
        if ("faultcode".equalsIgnoreCase(localName)
                || "faultstring".equalsIgnoreCase(localName)
                || "faultactor".equalsIgnoreCase(localName)) {
            // SOAP 1.2 does not have fault(code/string/actor)
            // So there is no else case required
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                return new FaultElement1_1Impl(ownerDocument,
                        localName,
                        prefix);
            }
        }

        return new ElementImpl(ownerDocument, element);
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

        if ("Envelope".equalsIgnoreCase(localName)) {
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                return new Envelope1_1Impl(ownerDocument, prefix);
            } else if (NameImpl.SOAP12_NAMESPACE.equals(namespaceUri)) {
                return new Envelope1_2Impl(ownerDocument, prefix);
            }
        }
        if ("Body".equalsIgnoreCase(localName)) {
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                return new Body1_1Impl(ownerDocument, prefix);
            } else if (NameImpl.SOAP12_NAMESPACE.equals(namespaceUri)) {
                return new Body1_2Impl(ownerDocument, prefix);
            }
        }
        if ("Header".equalsIgnoreCase(localName)) {
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                return new Header1_1Impl(ownerDocument, prefix);
            } else if (NameImpl.SOAP12_NAMESPACE.equals(namespaceUri)) {
                return new Header1_2Impl(ownerDocument, prefix);
            }
        }
        if ("Fault".equalsIgnoreCase(localName)) {
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
        if ("Detail".equalsIgnoreCase(localName)) {
            if (NameImpl.SOAP11_NAMESPACE.equals(namespaceUri)) {
                return new Detail1_1Impl(ownerDocument, prefix);
            } else if (NameImpl.SOAP12_NAMESPACE.equals(namespaceUri)) {
                return new Detail1_2Impl(ownerDocument, prefix);
            }
        }
        if ("faultcode".equalsIgnoreCase(localName)
                || "faultstring".equalsIgnoreCase(localName)
                || "faultactor".equalsIgnoreCase(localName)) {
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
