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


    //JAXWS 2.0
    public static final String JAXWS_HANDLER_CONFIG =
        "com.sun.xml.internal.ws.handler.config";
    public static final String JAXWS_CLIENT_HANDLE_PROPERTY =
        "com.sun.xml.internal.ws.client.handle";

}
