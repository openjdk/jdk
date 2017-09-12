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
package com.sun.xml.internal.messaging.saaj.soap.ver1_1;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.soap.*;
import javax.xml.stream.XMLStreamReader;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.ContentType;
import com.sun.xml.internal.messaging.saaj.soap.MessageImpl;
import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;

public class Message1_1Impl extends MessageImpl implements SOAPConstants {

    protected static final Logger log =
        Logger.getLogger(LogDomainConstants.SOAP_VER1_1_DOMAIN,
                         "com.sun.xml.internal.messaging.saaj.soap.ver1_1.LocalStrings");

    public Message1_1Impl() {
        super();
    }

    public Message1_1Impl(boolean isFastInfoset, boolean acceptFastInfoset) {
        super(isFastInfoset, acceptFastInfoset);
    }

    public Message1_1Impl(SOAPMessage msg) {
        super(msg);
    }

    // unused. can we delete this? - Kohsuke
    public Message1_1Impl(MimeHeaders headers, InputStream in)
        throws IOException, SOAPExceptionImpl {
        super(headers, in);
    }

    public Message1_1Impl(MimeHeaders headers, ContentType ct, int stat, InputStream in)
        throws SOAPExceptionImpl {
        super(headers,ct,stat,in);
    }

    public Message1_1Impl(MimeHeaders headers, ContentType ct, int stat, XMLStreamReader reader)
            throws SOAPExceptionImpl {
            super(headers,ct,stat,reader);
    }

    @Override
    public SOAPPart getSOAPPart() {
        if (soapPartImpl == null) {
            soapPartImpl = new SOAPPart1_1Impl(this);
        }
        return soapPartImpl;
    }

    @Override
    protected boolean isCorrectSoapVersion(int contentTypeId) {
        return (contentTypeId & SOAP1_1_FLAG) != 0;
    }

    @Override
    public String getAction() {
        log.log(
            Level.SEVERE,
            "SAAJ0303.ver1_1.msg.op.unsupported.in.SOAP1.1",
            new String[] { "Action" });
        throw new UnsupportedOperationException("Operation not supported by SOAP 1.1");
    }

    @Override
    public void setAction(String type) {
        log.log(
            Level.SEVERE,
            "SAAJ0303.ver1_1.msg.op.unsupported.in.SOAP1.1",
            new String[] { "Action" });
        throw new UnsupportedOperationException("Operation not supported by SOAP 1.1");
    }

    @Override
    public String getCharset() {
        log.log(
            Level.SEVERE,
            "SAAJ0303.ver1_1.msg.op.unsupported.in.SOAP1.1",
            new String[] { "Charset" });
        throw new UnsupportedOperationException("Operation not supported by SOAP 1.1");
    }

    @Override
    public void setCharset(String charset) {
        log.log(
            Level.SEVERE,
            "SAAJ0303.ver1_1.msg.op.unsupported.in.SOAP1.1",
            new String[] { "Charset" });
        throw new UnsupportedOperationException("Operation not supported by SOAP 1.1");
    }

    @Override
    protected String getExpectedContentType() {
        return isFastInfoset ? "application/fastinfoset" : "text/xml";
    }

    @Override
   protected String getExpectedAcceptHeader() {
       String accept = "text/xml, text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2";
       return acceptFastInfoset ? ("application/fastinfoset, " + accept) : accept;
   }

}
