/*
 * Copyright 2000-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.ch;

import java.io.*;
import java.lang.ref.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.*;


// Make a socket channel look like a socket.
//
// The only aspects of java.net.Socket-hood that we don't attempt to emulate
// here are the interrupted-I/O exceptions (which our Solaris implementations
// attempt to support) and the sending of urgent data.  Otherwise an adapted
// socket should look enough like a real java.net.Socket to fool most of the
// developers most of the time, right down to the exception message strings.
//
// The methods in this class are defined in exactly the same order as in
// java.net.Socket so as to simplify tracking future changes to that class.
//

public class SocketAdaptor
    extends Socket
{

    // The channel being adapted
    private final SocketChannelImpl sc;

    // Timeout "option" value for reads
    private volatile int timeout = 0;

    // ## super will create a useless impl
    private SocketAdaptor(SocketChannelImpl sc) {
        this.sc = sc;
    }

    public static Socket create(SocketChannelImpl sc) {
        return new SocketAdaptor(sc);
    }

    public SocketChannel getChannel() {
        return sc;
    }

    // Override this method just to protect against changes in the superclass
    //
    public void connect(SocketAddress remote) throws IOException {
        connect(remote, 0);
    }

    public void connect(SocketAddress remote, int timeout) throws IOException {
        if (remote == null)
            throw new IllegalArgumentException("connect: The address can't be null");
        if (timeout < 0)
            throw new IllegalArgumentException("connect: timeout can't be negative");

        synchronized (sc.blockingLock()) {
            if (!sc.isBlocking())
                throw new IllegalBlockingModeException();

            try {

                if (timeout == 0) {
                    sc.connect(remote);
                    return;
                }

                // Implement timeout with a selector
                SelectionKey sk = null;
                Selector sel = null;
                sc.configureBlocking(false);
                try {
                    if (sc.connect(remote))
                        return;
                    sel = Util.getTemporarySelector(sc);
                    sk = sc.register(sel, SelectionKey.OP_CONNECT);
                    long to = timeout;
                    for (;;) {
                        if (!sc.isOpen())
                            throw new ClosedChannelException();
                        long st = System.currentTimeMillis();
                        int ns = sel.select(to);
                        if (ns > 0 &&
                            sk.isConnectable() && sc.finishConnect())
                            break;
                        sel.selectedKeys().remove(sk);
                        to -= System.currentTimeMillis() - st;
                        if (to <= 0) {
                            try {
                                sc.close();
                            } catch (IOException x) { }
                            throw new SocketTimeoutException();
                        }
                    }
                } finally {
                    if (sk != null)
                        sk.cancel();
                    if (sc.isOpen())
                        sc.configureBlocking(true);
                    if (sel != null)
                        Util.releaseTemporarySelector(sel);
                }

            } catch (Exception x) {
                Net.translateException(x, true);
            }
        }

    }

    public void bind(SocketAddress local) throws IOException {
        try {
            sc.bind(local);
        } catch (Exception x) {
            Net.translateException(x);
        }
    }

    public InetAddress getInetAddress() {
        SocketAddress remote = sc.remoteAddress();
        if (remote == null) {
            return null;
        } else {
            return ((InetSocketAddress)remote).getAddress();
        }
    }

    public InetAddress getLocalAddress() {
        if (sc.isOpen()) {
            SocketAddress local = sc.localAddress();
            if (local != null)
                return ((InetSocketAddress)local).getAddress();
        }
        return new InetSocketAddress(0).getAddress();
    }

    public int getPort() {
        SocketAddress remote = sc.remoteAddress();
        if (remote == null) {
            return 0;
        } else {
            return ((InetSocketAddress)remote).getPort();
        }
    }

    public int getLocalPort() {
        SocketAddress local = sc.localAddress();
        if (local == null) {
            return -1;
        } else {
            return ((InetSocketAddress)local).getPort();
        }
    }

    private class SocketInputStream
        extends ChannelInputStream
    {
        private SocketInputStream() {
            super(sc);
        }

        protected int read(ByteBuffer bb)
            throws IOException
        {
            synchronized (sc.blockingLock()) {
                if (!sc.isBlocking())
                    throw new IllegalBlockingModeException();
                if (timeout == 0)
                    return sc.read(bb);

                // Implement timeout with a selector
                SelectionKey sk = null;
                Selector sel = null;
                sc.configureBlocking(false);
                try {
                    int n;
                    if ((n = sc.read(bb)) != 0)
                        return n;
                    sel = Util.getTemporarySelector(sc);
                    sk = sc.register(sel, SelectionKey.OP_READ);
                    long to = timeout;
                    for (;;) {
                        if (!sc.isOpen())
                            throw new ClosedChannelException();
                        long st = System.currentTimeMillis();
                        int ns = sel.select(to);
                        if (ns > 0 && sk.isReadable()) {
                            if ((n = sc.read(bb)) != 0)
                                return n;
                        }
                        sel.selectedKeys().remove(sk);
                        to -= System.currentTimeMillis() - st;
                        if (to <= 0)
                            throw new SocketTimeoutException();
                    }
                } finally {
                    if (sk != null)
                        sk.cancel();
                    if (sc.isOpen())
                        sc.configureBlocking(true);
                    if (sel != null)
                        Util.releaseTemporarySelector(sel);
                }

            }
        }
    }

    private InputStream socketInputStream = null;

    public InputStream getInputStream() throws IOException {
        if (!sc.isOpen())
            throw new SocketException("Socket is closed");
        if (!sc.isConnected())
            throw new SocketException("Socket is not connected");
        if (!sc.isInputOpen())
            throw new SocketException("Socket input is shutdown");
        if (socketInputStream == null) {
            try {
                socketInputStream = AccessController.doPrivileged(
                    new PrivilegedExceptionAction<InputStream>() {
                        public InputStream run() throws IOException {
                            return new SocketInputStream();
                        }
                    });
            } catch (java.security.PrivilegedActionException e) {
                throw (IOException)e.getException();
            }
        }
        return socketInputStream;
    }

    public OutputStream getOutputStream() throws IOException {
        if (!sc.isOpen())
            throw new SocketException("Socket is closed");
        if (!sc.isConnected())
            throw new SocketException("Socket is not connected");
        if (!sc.isOutputOpen())
            throw new SocketException("Socket output is shutdown");
        OutputStream os = null;
        try {
            os = AccessController.doPrivileged(
                new PrivilegedExceptionAction<OutputStream>() {
                    public OutputStream run() throws IOException {
                        return Channels.newOutputStream(sc);
                    }
                });
        } catch (java.security.PrivilegedActionException e) {
            throw (IOException)e.getException();
        }
        return os;
    }

    private void setBooleanOption(SocketOption<Boolean> name, boolean value)
        throws SocketException
    {
        try {
            sc.setOption(name, value);
        } catch (IOException x) {
            Net.translateToSocketException(x);
        }
    }

    private void setIntOption(SocketOption<Integer> name, int value)
        throws SocketException
    {
        try {
            sc.setOption(name, value);
        } catch (IOException x) {
            Net.translateToSocketException(x);
        }
    }

    private boolean getBooleanOption(SocketOption<Boolean> name) throws SocketException {
        try {
            return sc.getOption(name).booleanValue();
        } catch (IOException x) {
            Net.translateToSocketException(x);
            return false;       // keep compiler happy
        }
    }

    private int getIntOption(SocketOption<Integer> name) throws SocketException {
        try {
            return sc.getOption(name).intValue();
        } catch (IOException x) {
            Net.translateToSocketException(x);
            return -1;          // keep compiler happy
        }
    }

    public void setTcpNoDelay(boolean on) throws SocketException {
        setBooleanOption(StandardSocketOption.TCP_NODELAY, on);
    }

    public boolean getTcpNoDelay() throws SocketException {
        return getBooleanOption(StandardSocketOption.TCP_NODELAY);
    }

    public void setSoLinger(boolean on, int linger) throws SocketException {
        if (!on)
            linger = -1;
        setIntOption(StandardSocketOption.SO_LINGER, linger);
    }

    public int getSoLinger() throws SocketException {
        return getIntOption(StandardSocketOption.SO_LINGER);
    }

    public void sendUrgentData(int data) throws IOException {
        throw new SocketException("Urgent data not supported");
    }

    public void setOOBInline(boolean on) throws SocketException {
        setBooleanOption(ExtendedSocketOption.SO_OOBINLINE, on);
    }

    public boolean getOOBInline() throws SocketException {
        return getBooleanOption(ExtendedSocketOption.SO_OOBINLINE);
    }

    public void setSoTimeout(int timeout) throws SocketException {
        if (timeout < 0)
            throw new IllegalArgumentException("timeout can't be negative");
        this.timeout = timeout;
    }

    public int getSoTimeout() throws SocketException {
        return timeout;
    }

    public void setSendBufferSize(int size) throws SocketException {
        // size 0 valid for SocketChannel, invalid for Socket
        if (size <= 0)
            throw new IllegalArgumentException("Invalid send size");
        setIntOption(StandardSocketOption.SO_SNDBUF, size);
    }

    public int getSendBufferSize() throws SocketException {
        return getIntOption(StandardSocketOption.SO_SNDBUF);
    }

    public void setReceiveBufferSize(int size) throws SocketException {
        // size 0 valid for SocketChannel, invalid for Socket
        if (size <= 0)
            throw new IllegalArgumentException("Invalid receive size");
        setIntOption(StandardSocketOption.SO_RCVBUF, size);
    }

    public int getReceiveBufferSize() throws SocketException {
        return getIntOption(StandardSocketOption.SO_RCVBUF);
    }

    public void setKeepAlive(boolean on) throws SocketException {
        setBooleanOption(StandardSocketOption.SO_KEEPALIVE, on);
    }

    public boolean getKeepAlive() throws SocketException {
        return getBooleanOption(StandardSocketOption.SO_KEEPALIVE);
    }

    public void setTrafficClass(int tc) throws SocketException {
        setIntOption(StandardSocketOption.IP_TOS, tc);
    }

    public int getTrafficClass() throws SocketException {
        return getIntOption(StandardSocketOption.IP_TOS);
    }

    public void setReuseAddress(boolean on) throws SocketException {
        setBooleanOption(StandardSocketOption.SO_REUSEADDR, on);
    }

    public boolean getReuseAddress() throws SocketException {
        return getBooleanOption(StandardSocketOption.SO_REUSEADDR);
    }

    public void close() throws IOException {
        try {
            sc.close();
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    public void shutdownInput() throws IOException {
        try {
            sc.shutdownInput();
        } catch (Exception x) {
            Net.translateException(x);
        }
    }

    public void shutdownOutput() throws IOException {
        try {
            sc.shutdownOutput();
        } catch (Exception x) {
            Net.translateException(x);
        }
    }

    public String toString() {
        if (sc.isConnected())
            return "Socket[addr=" + getInetAddress() +
                ",port=" + getPort() +
                ",localport=" + getLocalPort() + "]";
        return "Socket[unconnected]";
    }

    public boolean isConnected() {
        return sc.isConnected();
    }

    public boolean isBound() {
        return sc.localAddress() != null;
    }

    public boolean isClosed() {
        return !sc.isOpen();
    }

    public boolean isInputShutdown() {
        return !sc.isInputOpen();
    }

    public boolean isOutputShutdown() {
        return !sc.isOutputOpen();
    }

}
