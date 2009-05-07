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
package com.sun.xml.internal.messaging.saaj.soap.ver1_1;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPElement;

import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.soap.impl.HeaderElementImpl;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;

public class HeaderElement1_1Impl extends HeaderElementImpl {

    protected static final Logger log =
        Logger.getLogger(LogDomainConstants.SOAP_VER1_1_DOMAIN,
                         "com.sun.xml.internal.messaging.saaj.soap.ver1_1.LocalStrings");

    public HeaderElement1_1Impl(SOAPDocumentImpl ownerDoc, Name qname) {
        super(ownerDoc, qname);
    }
    public HeaderElement1_1Impl(SOAPDocumentImpl ownerDoc, QName qname) {
        super(ownerDoc, qname);
    }

    public SOAPElement setElementQName(QName newName) throws SOAPException {
        HeaderElementImpl copy =
            new HeaderElement1_1Impl((SOAPDocumentImpl) getOwnerDocument(), newName);
        return replaceElementWithSOAPElement(this,copy);
    }

    protected NameImpl getActorAttributeName() {
        return NameImpl.create("actor", null, NameImpl.SOAP11_NAMESPACE);
    }

    // role not supported by SOAP 1.1
    protected NameImpl getRoleAttributeName() {
        log.log(
            Level.SEVERE,
            "SAAJ0302.ver1_1.hdr.attr.unsupported.in.SOAP1.1",
            new String[] { "Role" });
        throw new UnsupportedOperationException("Role not supported by SOAP 1.1");
    }

    protected NameImpl getMustunderstandAttributeName() {
        return NameImpl.create("mustUnderstand", null, NameImpl.SOAP11_NAMESPACE);
    }

    // mustUnderstand attribute has literal value "1" or "0"
    protected String getMustunderstandLiteralValue(boolean mustUnderstand) {
        return (mustUnderstand == true ? "1" : "0");
    }

    protected boolean getMustunderstandAttributeValue(String mu) {
        if ("1".equals(mu) || "true".equalsIgnoreCase(mu))
            return true;
        return false;
    }

    // relay not supported by SOAP 1.1
    protected NameImpl getRelayAttributeName() {
        log.log(
            Level.SEVERE,
            "SAAJ0302.ver1_1.hdr.attr.unsupported.in.SOAP1.1",
            new String[] { "Relay" });
        throw new UnsupportedOperationException("Relay not supported by SOAP 1.1");
    }

    protected String getRelayLiteralValue(boolean relayAttr) {
        log.log(
            Level.SEVERE,
            "SAAJ0302.ver1_1.hdr.attr.unsupported.in.SOAP1.1",
            new String[] { "Relay" });
        throw new UnsupportedOperationException("Relay not supported by SOAP 1.1");
    }

    protected boolean getRelayAttributeValue(String mu) {
        log.log(
            Level.SEVERE,
            "SAAJ0302.ver1_1.hdr.attr.unsupported.in.SOAP1.1",
            new String[] { "Relay" });
        throw new UnsupportedOperationException("Relay not supported by SOAP 1.1");
    }

    protected String getActorOrRole() {
        return getActor();
    }

}
