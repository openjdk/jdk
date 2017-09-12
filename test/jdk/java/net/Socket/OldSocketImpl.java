/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.*;
import java.net.*;

class OldSocketImpl extends SocketImpl  {
    public static void main(String[] args) throws Exception {
        Socket.setSocketImplFactory(new SocketImplFactory() {
                public SocketImpl createSocketImpl() {
                    return new OldSocketImpl();
                }
        });
        Socket socket = new Socket("localhost", 23);
    }

    public void setOption(int optID, Object value) throws SocketException { }

    public Object getOption(int optID) throws SocketException {
        return null;
    }

    protected void create(boolean stream) throws IOException { }

    protected void connect(String host, int port) throws IOException { }

    protected void connect(InetAddress address, int port) throws IOException { }

    // Not in 1.3...
    // protected void connect(SocketAddress address, int timeout) throws IOException { }

    protected void bind(InetAddress host, int port) throws IOException { }

    protected void listen(int backlog) throws IOException { }

    protected void accept(SocketImpl s) throws IOException { }

    protected InputStream getInputStream() throws IOException {
        return null;
    }

    protected OutputStream getOutputStream() throws IOException {
        return null;
    }

    protected int available() throws IOException {
        return 0;
    }

    protected void close() throws IOException { }

    protected void sendUrgentData (int data) throws SocketException { }

}
