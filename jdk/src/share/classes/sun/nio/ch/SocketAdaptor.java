/*
 * Copyright 2000-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

    // Option adaptor object, created on demand
    private volatile OptionAdaptor opts = null;

    // Timeout "option" value for reads
    private volatile int timeout = 0;

    // Traffic-class/Type-of-service
    private volatile int trafficClass = 0;


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
            if (local == null)
                local = new InetSocketAddress(0);
            sc.bind(local);
        } catch (Exception x) {
            Net.translateException(x);
        }
    }

    public InetAddress getInetAddress() {
        if (!sc.isConnected())
            return null;
        return Net.asInetSocketAddress(sc.remoteAddress()).getAddress();
    }

    public InetAddress getLocalAddress() {
        if (!sc.isBound())
            return new InetSocketAddress(0).getAddress();
        return Net.asInetSocketAddress(sc.localAddress()).getAddress();
    }

    public int getPort() {
        if (!sc.isConnected())
            return 0;
        return Net.asInetSocketAddress(sc.remoteAddress()).getPort();
    }

    public int getLocalPort() {
        if (!sc.isBound())
            return -1;
        return Net.asInetSocketAddress(sc.localAddress()).getPort();
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
                socketInputStream = (InputStream)AccessController.doPrivileged(
                    new PrivilegedExceptionAction() {
                        public Object run() throws IOException {
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
            os = (OutputStream)
                AccessController.doPrivileged(new PrivilegedExceptionAction() {
                    public Object run() throws IOException {
                        return Channels.newOutputStream(sc);
                    }
                });
        } catch (java.security.PrivilegedActionException e) {
            throw (IOException)e.getException();
        }
        return os;
    }

    private OptionAdaptor opts() {
        if (opts == null)
            opts = new OptionAdaptor(sc);
        return opts;
    }

    public void setTcpNoDelay(boolean on) throws SocketException {
        opts().setTcpNoDelay(on);
    }

    public boolean getTcpNoDelay() throws SocketException {
        return opts().getTcpNoDelay();
    }

    public void setSoLinger(boolean on, int linger) throws SocketException {
        opts().setSoLinger(on, linger);
    }

    public int getSoLinger() throws SocketException {
        return opts().getSoLinger();
    }

    public void sendUrgentData(int data) throws IOException {
        throw new SocketException("Urgent data not supported");
    }

    public void setOOBInline(boolean on) throws SocketException {
        opts().setOOBInline(on);
    }

    public boolean getOOBInline() throws SocketException {
        return opts().getOOBInline();
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
        opts().setSendBufferSize(size);
    }

    public int getSendBufferSize() throws SocketException {
        return opts().getSendBufferSize();
    }

    public void setReceiveBufferSize(int size) throws SocketException {
        opts().setReceiveBufferSize(size);
    }

    public int getReceiveBufferSize() throws SocketException {
        return opts().getReceiveBufferSize();
    }

    public void setKeepAlive(boolean on) throws SocketException {
        opts().setKeepAlive(on);
    }

    public boolean getKeepAlive() throws SocketException {
        return opts().getKeepAlive();
    }

    public void setTrafficClass(int tc) throws SocketException {
        opts().setTrafficClass(tc);
        trafficClass = tc;
    }

    public int getTrafficClass() throws SocketException {
        int tc = opts().getTrafficClass();
        if (tc < 0) {
            tc = trafficClass;
        }
        return tc;
    }

    public void setReuseAddress(boolean on) throws SocketException {
        opts().setReuseAddress(on);
    }

    public boolean getReuseAddress() throws SocketException {
        return opts().getReuseAddress();
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
        return sc.isBound();
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
