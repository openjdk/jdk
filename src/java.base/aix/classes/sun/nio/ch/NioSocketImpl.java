/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.io.FileDescriptor;
import java.net.StandardSocketOptions;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketException;
import java.lang.ref.Cleaner.Cleanable;
import jdk.internal.access.JavaIOFileDescriptorAccess;
import jdk.internal.access.SharedSecrets;
import java.net.SocketImpl;
import sun.net.PlatformSocketImpl;
import java.net.SocketAddress;
import java.net.InetAddress;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketOption;
import java.util.Objects;
import java.util.Set;
import java.net.InetSocketAddress;

public final class NioSocketImpl extends SocketImpl implements PlatformSocketImpl {

    private static final NativeDispatcher nd = new SocketDispatcher();
    private final FileDescriptor fd = new FileDescriptor();

   // private final FileDescriptor fd;
    // The stateLock for read/changing state
    private final Object stateLock = new Object();
    private static final int ST_NEW = 0;
    private static final int ST_UNCONNECTED = 1;
    private static final int ST_CONNECTING = 2;
    private static final int ST_CONNECTED = 3;
    private static final int ST_CLOSING = 4;
    private static final int ST_CLOSED = 5;
    private volatile int state;  // need stateLock to change
    // used by connect/read/write/accept, protected by stateLock
    private long readerThread;
    private long writerThread;
    private Cleanable cleaner;
    private boolean stream;
    private final boolean server;
    private final NioSocketImpl impl;

    public NioSocketImpl(boolean server) {
        impl = new sun.nio.ch.NioSocketImpl(server);
        this.server = server;
//        this.fd = fd;
    }

    //public NioSocketImpl(boolean server) {
      //  impl = new sun.nio.ch.NioSocketImpl(server);
   // }

    public void create(boolean stream) throws IOException {
        impl.create(stream);
    }

    public void connect(SocketAddress remote, int millis) throws IOException {
       impl.connect(remote, millis);
    }

    public void connect(String host, int port) throws IOException {
        impl.connect(host, port);
    }

    public void connect(InetAddress address, int port) throws IOException {
        impl.connect(address, port);
    }

    public void bind(InetAddress address, int port) throws IOException {
        impl.bind(address, port);
   }

    public void listen(int backlog) throws IOException {
        impl.listen(backlog);
    }

    public void accept(SocketImpl si) throws IOException {
        System.out.println("accept /n");
        impl.accept(si);
    }

    public InputStream getInputStream() throws IOException {
        return impl.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
        return impl.getOutputStream();
    }

    public int available() throws IOException {
        return impl.available();
    }

    public Set<SocketOption<?>> supportedOptions() {
        return impl.supportedOptions();
    }

    public <T> void setOption(SocketOption<T> opt, T value) throws IOException {
        impl.setOption(opt, value);
    }

    public <T> T getOption(SocketOption<T> opt) throws IOException {
        return impl.getOption(opt);
    }

    public void setOption(int opt, Object value) throws SocketException {
        impl.setOption(opt, value);
    }

    public Object getOption(int opt) throws SocketException {
       return impl.getOption(opt);
    }

    public void shutdownInput() throws IOException {
       impl.shutdownInput();
    }

    public void shutdownOutput() throws IOException {
        impl.shutdownOutput();
    }

    public boolean supportsUrgentData() {
        return impl.supportsUrgentData();
    }

    public void sendUrgentData(int data) throws IOException {
        impl.sendUrgentData(data);
    }

    public int timedAccept(FileDescriptor fd,
                            FileDescriptor newfd,
                            InetSocketAddress[] isaa,
                            long nanos) {
        return impl.timedAccept(fd, newfd, isaa, nanos);
    }

    public void close() throws IOException {
        synchronized (stateLock) {
            int state = this.state;
            if (state >= ST_CLOSING)
                return;
            if (state == ST_NEW) {
                // stillborn
                this.state = ST_CLOSED;
                return;
            }
            boolean connected = (state == ST_CONNECTED);
            this.state = ST_CLOSING;

            // shutdown output when linger interval not set to 0
            if (connected) {
                try {
                    var SO_LINGER = StandardSocketOptions.SO_LINGER;
                    if ((int) Net.getSocketOption(fd, SO_LINGER) != 0) {
                        Net.shutdown(fd, Net.SHUT_WR);
                    }
                } catch (IOException ignore) { }
            }

            // attempt to close the socket. If there are I/O operations in progress
            // then the socket is pre-closed and the thread(s) signalled. The
            // last thread will close the file descriptor.
            if (!tryClose()) {
                long reader = readerThread;
                long writer = writerThread;
                if (NativeThread.isVirtualThread(reader)
                        || NativeThread.isVirtualThread(writer)) {
                    Poller.stopPoll(fdVal(fd));
                }
                if (NativeThread.isNativeThread(reader)
                        || NativeThread.isNativeThread(writer)) {
                    if (NativeThread.isNativeThread(reader))
                        NativeThread.signal(reader);
                    if (NativeThread.isNativeThread(writer))
                        NativeThread.signal(writer);
                    nd.preClose(fd);
                }
            }
        }
    }

    /**
     * Closes the socket if there are no I/O operations in progress.
     */
    private boolean tryClose() throws IOException {
        assert Thread.holdsLock(stateLock) && state == ST_CLOSING;
        if (readerThread == 0 && writerThread == 0) {
            try {
                cleaner.clean();
            } catch (UncheckedIOException ioe) {
                throw ioe.getCause();
            } finally {
                state = ST_CLOSED;
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Return the file descriptor value.
     */
    private static int fdVal(FileDescriptor fd) {
        return JIOFDA.get(fd);
    }

    private static final JavaIOFileDescriptorAccess JIOFDA = SharedSecrets.getJavaIOFileDescriptorAccess();
}
