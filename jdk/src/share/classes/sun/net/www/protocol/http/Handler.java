/*
 * Copyright 1994-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

/*-
 *      HTTP stream opener
 */

package sun.net.www.protocol.http;

import java.io.IOException;
import java.net.URL;
import java.net.Proxy;

/** open an http input stream given a URL */
public class Handler extends java.net.URLStreamHandler {
    protected String proxy;
    protected int proxyPort;

    protected int getDefaultPort() {
        return 80;
    }

    public Handler () {
        proxy = null;
        proxyPort = -1;
    }

    public Handler (String proxy, int port) {
        this.proxy = proxy;
        this.proxyPort = port;
    }

    protected java.net.URLConnection openConnection(URL u)
    throws IOException {
        return openConnection(u, (Proxy)null);
    }

    protected java.net.URLConnection openConnection(URL u, Proxy p)
        throws IOException {
        return new HttpURLConnection(u, p, this);
    }
}
