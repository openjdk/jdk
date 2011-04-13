/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package java.net;

import java.io.*;
import java.security.PrivilegedAction;

/*
 * This class PlainSocketImpl simply delegates to the appropriate real
 * SocketImpl. We do this because PlainSocketImpl is already extended
 * by SocksSocketImpl.
 * <p>
 * There are two possibilities for the real SocketImpl,
 * TwoStacksPlainSocketImpl or DualStackPlainSocketImpl. We use
 * DualStackPlainSocketImpl on systems that have a dual stack
 * TCP implementation. Otherwise we create an instance of
 * TwoStacksPlainSocketImpl and delegate to it.
 *
 * @author Chris Hegarty
 */

class PlainSocketImpl extends AbstractPlainSocketImpl
{
    private AbstractPlainSocketImpl impl;

    /* the windows version. */
    private static float version;

    /* java.net.preferIPv4Stack */
    private static boolean preferIPv4Stack = false;

    /* If the version supports a dual stack TCP implementation */
    private static boolean useDualStackImpl = false;

    static {
        java.security.AccessController.doPrivileged( new PrivilegedAction<Object>() {
                public Object run() {
                    version = 0;
                    try {
                        version = Float.parseFloat(System.getProperties().getProperty("os.version"));
                        preferIPv4Stack = Boolean.parseBoolean(
                                          System.getProperties().getProperty("java.net.preferIPv4Stack"));
                    } catch (NumberFormatException e ) {
                        assert false : e;
                    }
                    return null; // nothing to return
                } });

        // (version >= 6.0) implies Vista or greater.
        if (version >= 6.0 && !preferIPv4Stack) {
            useDualStackImpl = true;
        }
    }

    /**
     * Constructs an empty instance.
     */
    PlainSocketImpl() {
        if (useDualStackImpl) {
            impl = new DualStackPlainSocketImpl();
        } else {
            impl = new TwoStacksPlainSocketImpl();
        }
    }

    /**
     * Constructs an instance with the given file descriptor.
     */
    PlainSocketImpl(FileDescriptor fd) {
        if (useDualStackImpl) {
            impl = new DualStackPlainSocketImpl(fd);
        } else {
            impl = new TwoStacksPlainSocketImpl(fd);
        }
    }

    // Override methods in SocketImpl that access impl's fields.

    protected FileDescriptor getFileDescriptor() {
        return impl.getFileDescriptor();
    }

    protected InetAddress getInetAddress() {
        return impl.getInetAddress();
    }

    protected int getPort() {
        return impl.getPort();
    }

    protected int getLocalPort() {
        return impl.getLocalPort();
    }

    void setSocket(Socket soc) {
        impl.setSocket(soc);
    }

    Socket getSocket() {
        return impl.getSocket();
    }

    void setServerSocket(ServerSocket soc) {
        impl.setServerSocket(soc);
    }

    ServerSocket getServerSocket() {
        return impl.getServerSocket();
    }

    public String toString() {
        return impl.toString();
    }

    // Override methods in AbstractPlainSocketImpl that access impl's fields.

    protected synchronized void create(boolean stream) throws IOException {
        impl.create(stream);

        // set fd to delegate's fd to be compatible with older releases
        this.fd = impl.fd;
    }

    protected void connect(String host, int port)
        throws UnknownHostException, IOException
    {
        impl.connect(host, port);
    }

    protected void connect(InetAddress address, int port) throws IOException {
        impl.connect(address, port);
    }

    protected void connect(SocketAddress address, int timeout) throws IOException {
        impl.connect(address, timeout);
    }

    public void setOption(int opt, Object val) throws SocketException {
        impl.setOption(opt, val);
    }

    public Object getOption(int opt) throws SocketException {
        return impl.getOption(opt);
    }

    synchronized void doConnect(InetAddress address, int port, int timeout) throws IOException {
        impl.doConnect(address, port, timeout);
    }

    protected synchronized void bind(InetAddress address, int lport)
        throws IOException
    {
        impl.bind(address, lport);
    }

    protected synchronized void accept(SocketImpl s) throws IOException {
        // pass in the real impl not the wrapper.
        SocketImpl delegate = ((PlainSocketImpl)s).impl;
        delegate.address = new InetAddress();
        delegate.fd = new FileDescriptor();
        impl.accept(delegate);

        // set fd to delegate's fd to be compatible with older releases
        s.fd = delegate.fd;
    }

    void setFileDescriptor(FileDescriptor fd) {
        impl.setFileDescriptor(fd);
    }

    void setAddress(InetAddress address) {
        impl.setAddress(address);
    }

    void setPort(int port) {
        impl.setPort(port);
    }

    void setLocalPort(int localPort) {
        impl.setLocalPort(localPort);
    }

    protected synchronized InputStream getInputStream() throws IOException {
        return impl.getInputStream();
    }

    void setInputStream(SocketInputStream in) {
        impl.setInputStream(in);
    }

    protected synchronized OutputStream getOutputStream() throws IOException {
        return impl.getOutputStream();
    }

    protected void close() throws IOException {
        try {
            impl.close();
        } finally {
            // set fd to delegate's fd to be compatible with older releases
            this.fd = null;
        }
    }

    void reset() throws IOException {
        try {
            impl.reset();
        } finally {
            // set fd to delegate's fd to be compatible with older releases
            this.fd = null;
        }
    }

    protected void shutdownInput() throws IOException {
        impl.shutdownInput();
    }

    protected void shutdownOutput() throws IOException {
        impl.shutdownOutput();
    }

    protected void sendUrgentData(int data) throws IOException {
        impl.sendUrgentData(data);
    }

    FileDescriptor acquireFD() {
        return impl.acquireFD();
    }

    void releaseFD() {
        impl.releaseFD();
    }

    public boolean isConnectionReset() {
        return impl.isConnectionReset();
    }

    public boolean isConnectionResetPending() {
        return impl.isConnectionResetPending();
    }

    public void setConnectionReset() {
        impl.setConnectionReset();
    }

    public void setConnectionResetPending() {
        impl.setConnectionResetPending();
    }

    public boolean isClosedOrPending() {
        return impl.isClosedOrPending();
    }

    public int getTimeout() {
        return impl.getTimeout();
    }

    // Override methods in AbstractPlainSocketImpl that need to be implemented.

    void socketCreate(boolean isServer) throws IOException {
        impl.socketCreate(isServer);
    }

    void socketConnect(InetAddress address, int port, int timeout)
        throws IOException {
        impl.socketConnect(address, port, timeout);
    }

    void socketBind(InetAddress address, int port)
        throws IOException {
        impl.socketBind(address, port);
    }

    void socketListen(int count) throws IOException {
        impl.socketListen(count);
    }

    void socketAccept(SocketImpl s) throws IOException {
        impl.socketAccept(s);
    }

    int socketAvailable() throws IOException {
        return impl.socketAvailable();
    }

    void socketClose0(boolean useDeferredClose) throws IOException {
        impl.socketClose0(useDeferredClose);
    }

    void socketShutdown(int howto) throws IOException {
        impl.socketShutdown(howto);
    }

    void socketSetOption(int cmd, boolean on, Object value)
        throws SocketException {
        socketSetOption(cmd, on, value);
    }

    int socketGetOption(int opt, Object iaContainerObj) throws SocketException {
        return impl.socketGetOption(opt, iaContainerObj);
    }

    void socketSendUrgentData(int data) throws IOException {
        impl.socketSendUrgentData(data);
    }
}
