/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.xml.internal.messaging.saaj.soap.ver1_1;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPException;
import javax.xml.transform.Source;

import com.sun.xml.internal.messaging.saaj.soap.*;
import com.sun.xml.internal.messaging.saaj.soap.impl.EnvelopeImpl;
import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;
import com.sun.xml.internal.messaging.saaj.util.XMLDeclarationParser;

public class SOAPPart1_1Impl extends SOAPPartImpl implements SOAPConstants {

    protected static final Logger log =
        Logger.getLogger(LogDomainConstants.SOAP_VER1_1_DOMAIN,
                         "com.sun.xml.internal.messaging.saaj.soap.ver1_1.LocalStrings");

    public SOAPPart1_1Impl() {
        super();
     }

    public SOAPPart1_1Impl(MessageImpl message) {
        super(message);
    }

    protected String getContentType() {
        return isFastInfoset() ? "application/fastinfoset" : "text/xml";
    }

    protected Envelope createEnvelopeFromSource() throws SOAPException {
        // Record the presence of xml declaration before the envelope gets
        // created.
        XMLDeclarationParser parser = lookForXmlDecl();
        Source tmp = source;
        source = null;
        EnvelopeImpl envelope =
            (EnvelopeImpl) EnvelopeFactory.createEnvelope(tmp, this);

        if (!envelope.getNamespaceURI().equals(SOAPConstants.URI_NS_SOAP_1_1_ENVELOPE)) {
            log.severe("SAAJ0304.ver1_1.msg.invalid.SOAP1.1");
            throw new SOAPException("InputStream does not represent a valid SOAP 1.1 Message");
        }

        if (parser != null && !omitXmlDecl) {
            envelope.setOmitXmlDecl("no");
            envelope.setXmlDecl(parser.getXmlDeclaration());
            envelope.setCharsetEncoding(parser.getEncoding());
        }
        return envelope;
    }

    protected Envelope createEmptyEnvelope(String prefix)
        throws SOAPException {
        return new Envelope1_1Impl(getDocument(), prefix, true, true);
    }

    protected SOAPPartImpl duplicateType() {
        return new SOAPPart1_1Impl();
    }

}
