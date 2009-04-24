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
package com.sun.xml.internal.ws.wsdl.parser;

import javax.xml.namespace.QName;


/**
 * Interface defining WSDL-related constants.
 *
 * @author WS Development Team
 */
public interface WSDLConstants {
    // namespace URIs
    public static final String PREFIX_NS_WSDL = "wsdl";
    public static final String NS_XMLNS = "http://www.w3.org/2001/XMLSchema";
    public static final String NS_WSDL = "http://schemas.xmlsoap.org/wsdl/";
    public static final String NS_SOAP11_HTTP_BINDING = "http://schemas.xmlsoap.org/soap/http";

    public static final QName QNAME_SCHEMA = new QName(NS_XMLNS, "schema");

    // QNames
    public static final QName QNAME_BINDING = new QName(NS_WSDL, "binding");
    public static final QName QNAME_DEFINITIONS = new QName(NS_WSDL, "definitions");
    public static final QName QNAME_DOCUMENTATION = new QName(NS_WSDL, "documentation");
    public static final QName NS_SOAP_BINDING_ADDRESS = new QName("http://schemas.xmlsoap.org/wsdl/soap/",
            "address");
    public static final QName NS_SOAP_BINDING = new QName("http://schemas.xmlsoap.org/wsdl/soap/",
            "binding");
    public static final QName NS_SOAP12_BINDING = new QName("http://schemas.xmlsoap.org/wsdl/soap12/",
            "binding");
    public static final QName NS_SOAP12_BINDING_ADDRESS = new QName("http://schemas.xmlsoap.org/wsdl/soap12/",
            "address");

    //public static final QName QNAME_FAULT = new QName(NS_WSDL, "fault");
    public static final QName QNAME_IMPORT = new QName(NS_WSDL, "import");

    //public static final QName QNAME_INPUT = new QName(NS_WSDL, "input");
    public static final QName QNAME_MESSAGE = new QName(NS_WSDL, "message");
    public static final QName QNAME_PART = new QName(NS_WSDL, "part");
    public static final QName QNAME_OPERATION = new QName(NS_WSDL, "operation");
    public static final QName QNAME_INPUT = new QName(NS_WSDL, "input");
    public static final QName QNAME_OUTPUT = new QName(NS_WSDL, "output");

    //public static final QName QNAME_OUTPUT = new QName(NS_WSDL, "output");
    //public static final QName QNAME_PART = new QName(NS_WSDL, "part");
    public static final QName QNAME_PORT = new QName(NS_WSDL, "port");
    public static final QName QNAME_ADDRESS = new QName(NS_WSDL, "address");
    public static final QName QNAME_PORT_TYPE = new QName(NS_WSDL, "portType");
    public static final QName QNAME_FAULT = new QName(NS_WSDL, "fault");
    public static final QName QNAME_SERVICE = new QName(NS_WSDL, "service");
    public static final QName QNAME_TYPES = new QName(NS_WSDL, "types");

    public static final String ATTR_TRANSPORT = "transport";
    public static final String ATTR_LOCATION = "location";
    public static final String ATTR_NAME = "name";
    public static final String ATTR_TNS = "targetNamespace";

    //public static final QName QNAME_ATTR_ARRAY_TYPE = new QName(NS_WSDL, "arrayType");
}
