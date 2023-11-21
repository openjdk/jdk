/*
 * Copyright (c) 1996, 2023, Oracle and/or its affiliates. All rights reserved.
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
package sun.rmi.transport.tcp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.ServerSocket;
import java.rmi.server.RMISocketFactory;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * RMIDirectSocketFactory creates a direct socket connection to the
 * specified port on the specified host.
 */
public class TCPDirectSocketFactory extends RMISocketFactory {

    @SuppressWarnings("removal")
    private static final int connectTimeout =    // default 1 minute
        AccessController.doPrivileged((PrivilegedAction<Integer>) () ->
            Integer.getInteger("sun.rmi.transport.tcp.initialConnectTimeout", 60 * 1000).intValue());

    public Socket createSocket(String host, int port) throws IOException
    {
        if (connectTimeout == 0) {
            return new Socket(host, port);
        } else {
            SocketAddress address = host != null ? new InetSocketAddress(host, port) :
                                                   new InetSocketAddress(InetAddress.getByName(null), port);
            Socket s = new Socket();
            s.connect(address, connectTimeout);
            return s;
        }
    }

    public ServerSocket createServerSocket(int port) throws IOException
    {
        return new ServerSocket(port);
    }
}
