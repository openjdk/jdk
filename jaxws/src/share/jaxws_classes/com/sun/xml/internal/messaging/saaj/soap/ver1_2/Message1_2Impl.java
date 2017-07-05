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
package com.sun.xml.internal.messaging.saaj.soap.ver1_2;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.soap.*;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.ContentType;
import com.sun.xml.internal.messaging.saaj.soap.MessageImpl;

public class Message1_2Impl extends MessageImpl implements SOAPConstants{

    public Message1_2Impl() {
        super();
    }

    public Message1_2Impl(SOAPMessage msg) {
        super(msg);
    }

    public Message1_2Impl(boolean isFastInfoset, boolean acceptFastInfoset) {
        super(isFastInfoset, acceptFastInfoset);
    }

    // unused. can we delete this? - Kohsuke
    public Message1_2Impl(MimeHeaders headers, InputStream in)
        throws IOException, SOAPExceptionImpl {
        super(headers, in);
    }

    public Message1_2Impl(MimeHeaders headers, ContentType ct, int stat, InputStream in)
        throws SOAPExceptionImpl {
        super(headers,ct,stat,in);
    }

    public SOAPPart getSOAPPart()  {
        if (soapPartImpl == null)
            soapPartImpl = new SOAPPart1_2Impl(this);

        return soapPartImpl;
    }

    protected boolean isCorrectSoapVersion(int contentTypeId) {
        return (contentTypeId & SOAP1_2_FLAG) != 0;
    }

    protected String getExpectedContentType() {
        return isFastInfoset ? "application/soap+fastinfoset" : "application/soap+xml";
    }

   protected String getExpectedAcceptHeader() {
       String accept = "application/soap+xml, text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2";
       return acceptFastInfoset ? ("application/soap+fastinfoset, " + accept) : accept;
   }

}
