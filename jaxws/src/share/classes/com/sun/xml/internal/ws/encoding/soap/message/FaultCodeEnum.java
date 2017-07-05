/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.ws.encoding.soap.message;

import com.sun.xml.internal.ws.encoding.soap.streaming.SOAP12NamespaceConstants;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;

import javax.xml.namespace.QName;

public enum FaultCodeEnum {
    VersionMismatch(new QName(SOAP12NamespaceConstants.ENVELOPE, "VersionMismatch", SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE)),
    MustUnderstand(new QName(SOAP12NamespaceConstants.ENVELOPE, "MustUnderstand", SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE)),
    DataEncodingUnknown(new QName(SOAP12NamespaceConstants.ENVELOPE, "DataEncodingUnknown", SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE)),
    Sender(new QName(SOAP12NamespaceConstants.ENVELOPE, "Sender", SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE)),
    Receiver(new QName(SOAP12NamespaceConstants.ENVELOPE, "Receiver", SOAPNamespaceConstants.NSPREFIX_SOAP_ENVELOPE));

    private FaultCodeEnum(QName code){
        this.code = code;
    }

    public QName value(){
        return code;
    }

    public String getLocalPart(){
        return code.getLocalPart();
    }

    public String getNamespaceURI(){
        return code.getNamespaceURI();
    }

    public String getPrefix(){
        return code.getPrefix();
    }

    public static FaultCodeEnum get(QName soapFaultCode){
        if(VersionMismatch.code.equals(soapFaultCode))
            return VersionMismatch;
        else if(MustUnderstand.code.equals(soapFaultCode))
            return MustUnderstand;
        else if(DataEncodingUnknown.code.equals(soapFaultCode))
            return DataEncodingUnknown;
        else if(Sender.code.equals(soapFaultCode))
            return Sender;
        else if(Receiver.code.equals(soapFaultCode))
            return Receiver;
        return null;
    }

    private final QName code;
}
