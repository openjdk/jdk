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

package com.sun.tools.internal.ws.wsdl.document;

import javax.xml.namespace.QName;

/**
 * Interface defining WSDL-related constants.
 *
 * @author WS Development Team
 */
public interface WSDLConstants {

    // namespace URIs
    public static String NS_XMLNS = "http://www.w3.org/2000/xmlns/";
    public static String NS_WSDL = "http://schemas.xmlsoap.org/wsdl/";

    // QNames
    public static QName QNAME_BINDING = new QName(NS_WSDL, "binding");
    public static QName QNAME_DEFINITIONS = new QName(NS_WSDL, "definitions");
    public static QName QNAME_DOCUMENTATION =
        new QName(NS_WSDL, "documentation");
    public static QName QNAME_FAULT = new QName(NS_WSDL, "fault");
    public static QName QNAME_IMPORT = new QName(NS_WSDL, "import");
    public static QName QNAME_INPUT = new QName(NS_WSDL, "input");
    public static QName QNAME_MESSAGE = new QName(NS_WSDL, "message");
    public static QName QNAME_OPERATION = new QName(NS_WSDL, "operation");
    public static QName QNAME_OUTPUT = new QName(NS_WSDL, "output");
    public static QName QNAME_PART = new QName(NS_WSDL, "part");
    public static QName QNAME_PORT = new QName(NS_WSDL, "port");
    public static QName QNAME_PORT_TYPE = new QName(NS_WSDL, "portType");
    public static QName QNAME_SERVICE = new QName(NS_WSDL, "service");
    public static QName QNAME_TYPES = new QName(NS_WSDL, "types");

    public static QName QNAME_ATTR_ARRAY_TYPE = new QName(NS_WSDL, "arrayType");
}
