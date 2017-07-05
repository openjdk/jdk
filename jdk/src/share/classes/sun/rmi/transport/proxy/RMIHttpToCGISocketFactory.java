/*
 * Copyright (c) 1998, Oracle and/or its affiliates. All rights reserved.
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
package sun.rmi.transport.proxy;

import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.URL;
import java.rmi.server.RMISocketFactory;

/**
 * RMIHttpToCGISocketFactory creates a socket connection to the
 * specified host that is comminicated within an HTTP request,
 * forwarded through the default firewall proxy, to the target host's
 * normal HTTP server, to a CGI program which forwards the request to
 * the actual specified port on the socket.
 */
public class RMIHttpToCGISocketFactory extends RMISocketFactory {

    public Socket createSocket(String host, int port)
        throws IOException
    {
        return new HttpSendSocket(host, port,
                                  new URL("http", host,
                                          "/cgi-bin/java-rmi.cgi" +
                                          "?forward=" + port));
    }

    public ServerSocket createServerSocket(int port) throws IOException
    {
        return new HttpAwareServerSocket(port);
    }
}
