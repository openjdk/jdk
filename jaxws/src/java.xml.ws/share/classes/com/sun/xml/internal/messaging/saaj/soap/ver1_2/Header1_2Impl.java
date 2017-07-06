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

/**
*
* @author SAAJ RI Development Team
*/
package com.sun.xml.internal.messaging.saaj.soap.ver1_2;

import java.util.logging.Logger;
import java.util.logging.Level;

import javax.xml.namespace.QName;
import javax.xml.soap.*;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.internal.messaging.saaj.soap.SOAPDocument;
import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.soap.impl.HeaderImpl;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;
import org.w3c.dom.Element;


public class Header1_2Impl extends HeaderImpl {

    protected static final Logger log =
        Logger.getLogger(
            LogDomainConstants.SOAP_VER1_2_DOMAIN,
            "com.sun.xml.internal.messaging.saaj.soap.ver1_2.LocalStrings");

    public Header1_2Impl(SOAPDocumentImpl ownerDocument, String prefix) {
        super(ownerDocument, NameImpl.createHeader1_2Name(prefix));
    }

    public Header1_2Impl(SOAPDocumentImpl ownerDoc, Element domElement) {
        super(ownerDoc, domElement);
    }

    @Override
    protected NameImpl getNotUnderstoodName() {
        return NameImpl.createNotUnderstood1_2Name(null);
    }

    @Override
    protected NameImpl getUpgradeName() {
        return NameImpl.createUpgrade1_2Name(null);
    }

    @Override
    protected NameImpl getSupportedEnvelopeName() {
        return NameImpl.createSupportedEnvelope1_2Name(null);
    }

    @Override
    public SOAPHeaderElement addNotUnderstoodHeaderElement(final QName sourceName)
        throws SOAPException {

        if (sourceName == null) {
            log.severe("SAAJ0410.ver1_2.no.null.to.addNotUnderstoodHeader");
            throw new SOAPException("Cannot pass NULL to addNotUnderstoodHeaderElement");
        }
        if ("".equals(sourceName.getNamespaceURI())) {
            log.severe("SAAJ0417.ver1_2.qname.not.ns.qualified");
            throw new SOAPException("The qname passed to addNotUnderstoodHeaderElement must be namespace-qualified");
        }
        String prefix = sourceName.getPrefix();
        if ("".equals(prefix)) {
            prefix = "ns1";
        }
        Name notunderstoodName = getNotUnderstoodName();
        SOAPHeaderElement notunderstoodHeaderElement =
            (SOAPHeaderElement) addChildElement(notunderstoodName);
        notunderstoodHeaderElement.addAttribute(
            NameImpl.createFromUnqualifiedName("qname"),
            getQualifiedName(
                new QName(
                    sourceName.getNamespaceURI(),
                    sourceName.getLocalPart(),
                    prefix)));
        notunderstoodHeaderElement.addNamespaceDeclaration(
            prefix,
            sourceName.getNamespaceURI());
        return notunderstoodHeaderElement;
    }

    @Override
    public SOAPElement addTextNode(String text) throws SOAPException {
        log.log(
            Level.SEVERE,
            "SAAJ0416.ver1_2.adding.text.not.legal",
            getElementQName());
        throw new SOAPExceptionImpl("Adding text to SOAP 1.2 Header is not legal");
    }

    @Override
    protected SOAPHeaderElement createHeaderElement(Name name)
        throws SOAPException {
        String uri = name.getURI();
        if (uri == null || uri.equals("")) {
            log.severe("SAAJ0413.ver1_2.header.elems.must.be.ns.qualified");
            throw new SOAPExceptionImpl("SOAP 1.2 header elements must be namespace qualified");
        }
        return new HeaderElement1_2Impl(
            ((SOAPDocument) getOwnerDocument()).getDocument(),
            name);
    }

    @Override
    protected SOAPHeaderElement createHeaderElement(QName name)
        throws SOAPException {
        String uri = name.getNamespaceURI();
        if (uri == null || uri.equals("")) {
            log.severe("SAAJ0413.ver1_2.header.elems.must.be.ns.qualified");
            throw new SOAPExceptionImpl("SOAP 1.2 header elements must be namespace qualified");
        }
        return new HeaderElement1_2Impl(
            ((SOAPDocument) getOwnerDocument()).getDocument(),
            name);
    }

    @Override
    public void setEncodingStyle(String encodingStyle) throws SOAPException {
        log.severe("SAAJ0409.ver1_2.no.encodingstyle.in.header");
        throw new SOAPExceptionImpl("encodingStyle attribute cannot appear on Header");
    }

    @Override
    public SOAPElement addAttribute(Name name, String value)
        throws SOAPException {
        if (name.getLocalName().equals("encodingStyle")
            && name.getURI().equals(NameImpl.SOAP12_NAMESPACE)) {

            setEncodingStyle(value);
        }
        return super.addAttribute(name, value);
    }

    @Override
    public SOAPElement addAttribute(QName name, String value)
        throws SOAPException {
        if (name.getLocalPart().equals("encodingStyle")
            && name.getNamespaceURI().equals(NameImpl.SOAP12_NAMESPACE)) {

            setEncodingStyle(value);
        }
        return super.addAttribute(name, value);
    }
}
