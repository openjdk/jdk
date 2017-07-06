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

package com.sun.xml.internal.messaging.saaj.soap;

import javax.xml.soap.SAAJMetaFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.SOAPException;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;

public class SAAJMetaFactoryImpl extends SAAJMetaFactory {

    protected static final Logger log =
        Logger.getLogger(LogDomainConstants.SOAP_DOMAIN,
                         "com.sun.xml.internal.messaging.saaj.soap.LocalStrings");

    @Override
    protected  MessageFactory newMessageFactory(String protocol)
        throws SOAPException {
        if (SOAPConstants.SOAP_1_1_PROTOCOL.equals(protocol)) {
              return new com.sun.xml.internal.messaging.saaj.soap.ver1_1.SOAPMessageFactory1_1Impl();
        } else if (SOAPConstants.SOAP_1_2_PROTOCOL.equals(protocol)) {
              return new com.sun.xml.internal.messaging.saaj.soap.ver1_2.SOAPMessageFactory1_2Impl();
        } else if (SOAPConstants.DYNAMIC_SOAP_PROTOCOL.equals(protocol)) {
              return new com.sun.xml.internal.messaging.saaj.soap.dynamic.SOAPMessageFactoryDynamicImpl();
        } else {
            log.log(
                Level.SEVERE,
                "SAAJ0569.soap.unknown.protocol",
                new Object[] {protocol, "MessageFactory"});
            throw new SOAPException("Unknown Protocol: " + protocol +
                                        "  specified for creating MessageFactory");
        }
    }

    @Override
    protected  SOAPFactory newSOAPFactory(String protocol)
        throws SOAPException {
        if (SOAPConstants.SOAP_1_1_PROTOCOL.equals(protocol)) {
            return new com.sun.xml.internal.messaging.saaj.soap.ver1_1.SOAPFactory1_1Impl();
        } else if (SOAPConstants.SOAP_1_2_PROTOCOL.equals(protocol)) {
            return new com.sun.xml.internal.messaging.saaj.soap.ver1_2.SOAPFactory1_2Impl();
        } else if (SOAPConstants.DYNAMIC_SOAP_PROTOCOL.equals(protocol)) {
            return new com.sun.xml.internal.messaging.saaj.soap.dynamic.SOAPFactoryDynamicImpl();
        } else {
            log.log(
                Level.SEVERE,
                "SAAJ0569.soap.unknown.protocol",
                new Object[] {protocol, "SOAPFactory"});
            throw new SOAPException("Unknown Protocol: " + protocol +
                                        "  specified for creating SOAPFactory");
        }
    }

}
