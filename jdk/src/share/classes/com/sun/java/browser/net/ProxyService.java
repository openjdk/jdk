/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.browser.net;

import java.net.URL;
import java.io.IOException;

/**
 *
 * @author  Zhengyu Gu
 */
public class ProxyService extends Object {
    private static ProxyServiceProvider provider = null;


    public static void setProvider(ProxyServiceProvider p)
    throws IOException {
        if(null == provider)
            provider = p;
        else
            throw new IOException("Proxy service provider has already been set.");
    }


    /**
     *  <p>The function returns proxy information of the specified URL.</p>
     *  @param url URL
     *  @return returns proxy information. If there is not proxy, returns null
     *  @since 1.4
     */
    public static ProxyInfo[] getProxyInfo(URL url)
    throws IOException {
        if(null == provider)
            throw new IOException("Proxy service provider is not yet set");

        return provider.getProxyInfo(url);
    }
}
