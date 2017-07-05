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

package com.sun.xml.internal.ws.protocol.soap;

import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.ExceptionHasMessage;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.encoding.soap.SOAP12Constants;
import com.sun.xml.internal.ws.encoding.soap.SOAPConstants;
import com.sun.xml.internal.ws.fault.SOAPFaultBuilder;

import javax.xml.namespace.QName;

/**
 * This is used to represent SOAP VersionMismatchFault. Use
 * this when the received soap envelope is in a different namespace
 * than what the specified Binding says.
 *
 * @author Jitendra Kotamraju
 */
public class VersionMismatchException extends ExceptionHasMessage {

    private final SOAPVersion soapVersion;

    public VersionMismatchException(SOAPVersion soapVersion, Object... args) {
        super("soap.version.mismatch.err", args);
        this.soapVersion = soapVersion;
    }

    public String getDefaultResourceBundleName() {
        return "com.sun.xml.internal.ws.resources.soap";
    }

    public Message getFaultMessage() {
        QName faultCode = (soapVersion == SOAPVersion.SOAP_11)
            ? SOAPConstants.FAULT_CODE_VERSION_MISMATCH
            : SOAP12Constants.FAULT_CODE_VERSION_MISMATCH;
        return SOAPFaultBuilder.createSOAPFaultMessage(
                soapVersion, getLocalizedMessage(), faultCode);
    }

}
