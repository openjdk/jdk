/*
 * Copyright 1995-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.net.www.protocol.gopher;

import java.io.*;
import java.util.*;
import sun.net.NetworkClient;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.Proxy;
import java.net.InetSocketAddress;
import java.net.SocketPermission;
import java.security.Permission;
import sun.net.www.protocol.http.HttpURLConnection;

/**
 * A class to handle the gopher protocol.
 */

public class Handler extends java.net.URLStreamHandler {

    protected int getDefaultPort() {
        return 70;
    }

    public java.net.URLConnection openConnection(URL u)
    throws IOException {
        return openConnection(u, null);
    }

    public java.net.URLConnection openConnection(URL u, Proxy p)
    throws IOException {


        /* if set for proxy usage then go through the http code to get */
        /* the url connection. */
        if (p == null && GopherClient.getUseGopherProxy()) {
            String host = GopherClient.getGopherProxyHost();
            if (host != null) {
                InetSocketAddress saddr = InetSocketAddress.createUnresolved(host, GopherClient.getGopherProxyPort());

                p = new Proxy(Proxy.Type.HTTP, saddr);
            }
        }
        if (p != null) {
            return new HttpURLConnection(u, p);
        }

        return new GopherURLConnection(u);
    }
}

class GopherURLConnection extends sun.net.www.URLConnection {

    Permission permission;

    GopherURLConnection(URL u) {
        super(u);
    }

    public void connect() throws IOException {
    }

    public InputStream getInputStream() throws IOException {
        return new GopherClient(this).openStream(url);
    }

    public Permission getPermission() {
        if (permission == null) {
            int port = url.getPort();
            port = port < 0 ? 70 : port;
            String host = url.getHost() + ":" + url.getPort();
            permission = new SocketPermission(host, "connect");
        }
        return permission;
    }
}
