/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;

/**
 * A {@link SocketImpl} implementation where all methods throw {@link UnsupportedOperationException}.
 * This class is intended to be subclassed by tests, which override its methods to mock certain behaviour.
 */
class ThrowingSocketImpl extends SocketImpl {

    ThrowingSocketImpl() {}

    @Override
    protected void create(boolean stream) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void connect(String host, int port) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void connect(InetAddress address, int port) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void connect(SocketAddress address, int timeout) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void bind(InetAddress host, int port) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void listen(int backlog) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void accept(SocketImpl s) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected InputStream getInputStream() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected OutputStream getOutputStream() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected int available() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void close() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void sendUrgentData(int data) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setOption(int optID, Object value) throws SocketException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getOption(int optID) throws SocketException {
        throw new UnsupportedOperationException();
    }

}
