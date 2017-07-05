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
 *
 */



/**
*
* @author SAAJ RI Development Team
*/
package com.sun.xml.internal.messaging.saaj.soap.ver1_2;

import java.util.logging.Logger;
import java.util.Locale;

import javax.xml.namespace.QName;
import javax.xml.soap.*;

import org.w3c.dom.Node;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.internal.messaging.saaj.soap.SOAPDocument;
import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.soap.impl.BodyImpl;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;

public class Body1_2Impl extends BodyImpl {

    protected static  final Logger log =
        Logger.getLogger(Body1_2Impl.class.getName(),
                         "com.sun.xml.internal.messaging.saaj.soap.ver1_2.LocalStrings");

    public Body1_2Impl(SOAPDocumentImpl ownerDocument, String prefix) {
            super(ownerDocument, NameImpl.createBody1_2Name(prefix));
    }

    protected NameImpl getFaultName(String name) {
        return NameImpl.createFault1_2Name(name, null);
    }

    protected SOAPBodyElement createBodyElement(Name name) {
        return new BodyElement1_2Impl(
            ((SOAPDocument) getOwnerDocument()).getDocument(),
            name);
    }
    protected SOAPBodyElement createBodyElement(QName name) {
        return new BodyElement1_2Impl(
            ((SOAPDocument) getOwnerDocument()).getDocument(),
            name);
    }

    protected QName getDefaultFaultCode() {
        return SOAPConstants.SOAP_RECEIVER_FAULT;
    }

    public SOAPFault addFault() throws SOAPException {
        if (hasAnyChildElement()) {
            log.severe("SAAJ0402.ver1_2.only.fault.allowed.in.body");
            throw new SOAPExceptionImpl(
                "No other element except Fault allowed in SOAPBody");
        }
        return super.addFault();
    }

    /*
     * Override setEncodingStyle of ElementImpl to restrict adding encodingStyle
     * attribute to SOAP Body (SOAP 1.2 spec, part 1, section 5.1.1)
     */
    public void setEncodingStyle(String encodingStyle) throws SOAPException {
        log.severe("SAAJ0401.ver1_2.no.encodingstyle.in.body");
        throw new SOAPExceptionImpl("encodingStyle attribute cannot appear on Body");
    }

    /*
     * Override addAttribute of ElementImpl to restrict adding encodingStyle
     * attribute to SOAP Body (SOAP 1.2 spec, part 1, section 5.1.1)
     */
    public SOAPElement addAttribute(Name name, String value)
        throws SOAPException {
        if (name.getLocalName().equals("encodingStyle")
            && name.getURI().equals(NameImpl.SOAP12_NAMESPACE)) {

            setEncodingStyle(value);
        }
        return super.addAttribute(name, value);
    }

    public SOAPElement addAttribute(QName name, String value)
        throws SOAPException {
        if (name.getLocalPart().equals("encodingStyle")
            && name.getNamespaceURI().equals(NameImpl.SOAP12_NAMESPACE)) {

            setEncodingStyle(value);
        }
        return super.addAttribute(name, value);
    }

    protected boolean isFault(SOAPElement child) {
        return (child.getElementName().getURI().equals(
                    SOAPConstants.URI_NS_SOAP_1_2_ENVELOPE) &&
                child.getElementName().getLocalName().equals(
                    "Fault"));
    }

    protected SOAPFault createFaultElement() {
        return new Fault1_2Impl(
            ((SOAPDocument) getOwnerDocument()).getDocument(), getPrefix());
    }

    /*
     * section 5.4 of SOAP1.2 candidate recommendation says that a
     * SOAP message MUST contain a single Fault element as the only
     * child element of the SOAP Body.
     */
    public SOAPBodyElement addBodyElement(Name name) throws SOAPException {
        if (hasFault()) {
            log.severe("SAAJ0402.ver1_2.only.fault.allowed.in.body");
            throw new SOAPExceptionImpl(
                "No other element except Fault allowed in SOAPBody");
        }
        return super.addBodyElement(name);
    }

    public SOAPBodyElement addBodyElement(QName name) throws SOAPException {
        if (hasFault()) {
            log.severe("SAAJ0402.ver1_2.only.fault.allowed.in.body");
            throw new SOAPExceptionImpl(
                "No other element except Fault allowed in SOAPBody");
        }
        return super.addBodyElement(name);
    }

    protected SOAPElement addElement(Name name) throws SOAPException {
        if (hasFault()) {
            log.severe("SAAJ0402.ver1_2.only.fault.allowed.in.body");
            throw new SOAPExceptionImpl(
                "No other element except Fault allowed in SOAPBody");
        }
        return super.addElement(name);
    }

    protected SOAPElement addElement(QName name) throws SOAPException {
        if (hasFault()) {
            log.severe("SAAJ0402.ver1_2.only.fault.allowed.in.body");
            throw new SOAPExceptionImpl(
                "No other element except Fault allowed in SOAPBody");
        }
        return super.addElement(name);
    }

    public SOAPElement addChildElement(Name name) throws SOAPException {
        if (hasFault()) {
            log.severe("SAAJ0402.ver1_2.only.fault.allowed.in.body");
            throw new SOAPExceptionImpl(
                "No other element except Fault allowed in SOAPBody");
        }
        return super.addChildElement(name);
    }

    public SOAPElement addChildElement(QName name) throws SOAPException {
        if (hasFault()) {
            log.severe("SAAJ0402.ver1_2.only.fault.allowed.in.body");
            throw new SOAPExceptionImpl(
                "No other element except Fault allowed in SOAPBody");
        }
        return super.addChildElement(name);
    }

    private boolean hasAnyChildElement() {
        Node currentNode = getFirstChild();
        while (currentNode != null) {
            if (currentNode.getNodeType() == Node.ELEMENT_NODE)
                return true;
            currentNode = currentNode.getNextSibling();
        }
        return false;
    }
}
