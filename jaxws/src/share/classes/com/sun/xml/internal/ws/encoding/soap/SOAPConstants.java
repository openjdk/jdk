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


package com.sun.xml.internal.ws.encoding.soap;

import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;

import javax.xml.namespace.QName;

/**
 *
 * @author WS Development Team
 */
public class SOAPConstants {

    public static final String URI_ENVELOPE = SOAPNamespaceConstants.ENVELOPE;
    public static final String URI_HTTP = SOAPNamespaceConstants.TRANSPORT_HTTP;
    public static final String URI_ENCODING  = "http://schemas.xmlsoap.org/soap/encoding/";
    public static final String NS_WSDL_SOAP = "http://schemas.xmlsoap.org/wsdl/soap/";
    public static final QName QNAME_ENVELOPE_ENCODINGSTYLE = new QName(URI_ENVELOPE, "encodingStyle");

    public final static QName QNAME_SOAP_ENVELOPE             = new QName(URI_ENVELOPE, "Envelope");
    public final static QName QNAME_SOAP_HEADER             = new QName(URI_ENVELOPE, "Header");
    public static final QName QNAME_MUSTUNDERSTAND         = new QName(URI_ENVELOPE, "mustUnderstand");
    public static final QName QNAME_ROLE                   = new QName(URI_ENVELOPE, "actor");
    public final static QName QNAME_SOAP_BODY             = new QName(URI_ENVELOPE, "Body");
    public final static QName QNAME_SOAP_FAULT             = new QName(URI_ENVELOPE, "Fault");
    public final static QName QNAME_SOAP_FAULT_CODE             = new QName("", "faultcode");
    public final static QName QNAME_SOAP_FAULT_STRING             = new QName("", "faultstring");
    public final static QName QNAME_SOAP_FAULT_ACTOR             = new QName("", "faultactor");
    public final static QName QNAME_SOAP_FAULT_DETAIL             = new QName("", "detail");
    public final static QName FAULT_CODE_MUST_UNDERSTAND   = new QName(URI_ENVELOPE, "MustUnderstand");

    public final static QName FAULT_CODE_VERSION_MISMATCH  = new QName(URI_ENVELOPE, "VersionMismatch");
    public final static QName FAULT_CODE_DATA_ENCODING_UNKNOWN = new QName(URI_ENVELOPE, "DataEncodingUnknown");
    public final static QName FAULT_CODE_PROCEDURE_NOT_PRESENT = new QName(URI_ENVELOPE, "ProcedureNotPresent");
    public final static QName FAULT_CODE_BAD_ARGUMENTS      = new QName(URI_ENVELOPE, "BadArguments");
}
