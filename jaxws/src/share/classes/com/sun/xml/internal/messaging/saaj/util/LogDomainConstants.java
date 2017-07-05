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



package com.sun.xml.internal.messaging.saaj.util;

/**
 * @author  Manveen Kaur (manveen.kaur@eng.sun.com)
 */

/**
 * This interface defines a number of constants pertaining to Logging domains.
 */

public interface LogDomainConstants {

    // TBD -- this should be configurable from a properties
    // file or something .. Leaving it as it is for now.
    public static String MODULE_TOPLEVEL_DOMAIN =
                    "javax.xml.messaging.saaj";

    // First Level Domain
    public static String CLIENT_DOMAIN =
                MODULE_TOPLEVEL_DOMAIN + ".client";

    public static String SOAP_DOMAIN =
                MODULE_TOPLEVEL_DOMAIN + ".soap";

    public static String UTIL_DOMAIN =
                MODULE_TOPLEVEL_DOMAIN + ".util";

    // Second Level Domain
    public static String HTTP_CONN_DOMAIN =
                  CLIENT_DOMAIN + ".p2p";

    public static String NAMING_DOMAIN =
                SOAP_DOMAIN + ".name";

    public static String SOAP_IMPL_DOMAIN =
                  SOAP_DOMAIN + ".impl";

    public static String SOAP_VER1_1_DOMAIN =
                  SOAP_DOMAIN + ".ver1_1";

    public static String SOAP_VER1_2_DOMAIN =
                  SOAP_DOMAIN + ".ver1_2";

}
