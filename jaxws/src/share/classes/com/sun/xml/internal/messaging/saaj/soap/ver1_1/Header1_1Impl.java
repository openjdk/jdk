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
 * $Id: Header1_1Impl.java,v 1.29 2006/01/27 12:49:41 vj135062 Exp $
 */



/**
*
* @author SAAJ RI Development Team
*/
package com.sun.xml.internal.messaging.saaj.soap.ver1_1;

import java.util.List;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.soap.*;

import com.sun.xml.internal.messaging.saaj.soap.SOAPDocument;
import com.sun.xml.internal.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.internal.messaging.saaj.soap.impl.HeaderImpl;
import com.sun.xml.internal.messaging.saaj.soap.name.NameImpl;
import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;

public class Header1_1Impl extends HeaderImpl {

    protected static Logger log =
        Logger.getLogger(LogDomainConstants.SOAP_VER1_1_DOMAIN,
                         "com.sun.xml.internal.messaging.saaj.soap.ver1_1.LocalStrings");

    public Header1_1Impl(SOAPDocumentImpl ownerDocument, String prefix) {
            super(ownerDocument, NameImpl.createHeader1_1Name(prefix));
    }

    protected NameImpl getNotUnderstoodName() {
        log.log(
            Level.SEVERE,
            "SAAJ0301.ver1_1.hdr.op.unsupported.in.SOAP1.1",
            new String[] { "getNotUnderstoodName" });
        throw new UnsupportedOperationException("Not supported by SOAP 1.1");
    }

    protected NameImpl getUpgradeName() {
        return NameImpl.create(
            "Upgrade",
            getPrefix(),
            SOAPConstants.URI_NS_SOAP_1_1_ENVELOPE);
    }

    protected NameImpl getSupportedEnvelopeName() {
        return NameImpl.create(
            "SupportedEnvelope",
            getPrefix(),
            SOAPConstants.URI_NS_SOAP_1_1_ENVELOPE);
    }

    public SOAPHeaderElement addNotUnderstoodHeaderElement(QName name)
        throws SOAPException {
        log.log(
            Level.SEVERE,
            "SAAJ0301.ver1_1.hdr.op.unsupported.in.SOAP1.1",
            new String[] { "addNotUnderstoodHeaderElement" });
        throw new UnsupportedOperationException("Not supported by SOAP 1.1");
    }

    protected SOAPHeaderElement createHeaderElement(Name name) {
        return new HeaderElement1_1Impl(
            ((SOAPDocument) getOwnerDocument()).getDocument(),
            name);
    }
    protected SOAPHeaderElement createHeaderElement(QName name) {
        return new HeaderElement1_1Impl(
            ((SOAPDocument) getOwnerDocument()).getDocument(),
            name);
    }
}
