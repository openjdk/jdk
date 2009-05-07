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

package com.sun.xml.internal.ws.client;

import com.sun.xml.internal.ws.developer.JAXWSProperties;

public interface BindingProviderProperties extends JAXWSProperties{

    //legacy properties
    @Deprecated
    public static final String HOSTNAME_VERIFICATION_PROPERTY =
        "com.sun.xml.internal.ws.client.http.HostnameVerificationProperty";
    public static final String HTTP_COOKIE_JAR =
        "com.sun.xml.internal.ws.client.http.CookieJar";

    public static final String REDIRECT_REQUEST_PROPERTY =
        "com.sun.xml.internal.ws.client.http.RedirectRequestProperty";
    public static final String ONE_WAY_OPERATION =
        "com.sun.xml.internal.ws.server.OneWayOperation";


    // Proprietary
    public static final String REQUEST_TIMEOUT =
        "com.sun.xml.internal.ws.request.timeout";

    //JAXWS 2.0
    public static final String JAXWS_HANDLER_CONFIG =
        "com.sun.xml.internal.ws.handler.config";
    public static final String JAXWS_CLIENT_HANDLE_PROPERTY =
        "com.sun.xml.internal.ws.client.handle";

}
