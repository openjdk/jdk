/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.FileDescriptor;
import java.util.Set;
import java.util.HashSet;
import sun.net.ext.ExtendedSocketOptions;
import static sun.net.ext.ExtendedSocketOptions.SOCK_STREAM;

/*
 * On Unix systems we simply delegate to native methods.
 *
 * @author Chris Hegarty
 */

class PlainSocketImpl extends AbstractPlainSocketImpl
{
    static {
        initProto();
    }

    /**
     * Constructs an empty instance.
     */
    PlainSocketImpl() { }

    /**
     * Constructs an instance with the given file descriptor.
     */
    PlainSocketImpl(FileDescriptor fd) {
        this.fd = fd;
    }

    static final ExtendedSocketOptions extendedOptions =
            ExtendedSocketOptions.getInstance();

    protected <T> void setOption(SocketOption<T> name, T value) throws IOException {
        if (isClosedOrPending()) {
            throw new SocketException("Socket closed");
        }
        if (supportedOptions().contains(name)) {
            if (extendedOptions.isOptionSupported(name)) {
                extendedOptions.setOption(fd, name, value);
            } else {
                super.setOption(name, value);
            }
        } else {
            throw new UnsupportedOperationException("unsupported option");
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> T getOption(SocketOption<T> name) throws IOException {
        if (isClosedOrPending()) {
            throw new SocketException("Socket closed");
        }
        if (supportedOptions().contains(name)) {
            if (extendedOptions.isOptionSupported(name)) {
                return (T) extendedOptions.getOption(fd, name);
            } else {
                return super.getOption(name);
            }
        } else {
            throw new UnsupportedOperationException("unsupported option");
        }
    }

    protected Set<SocketOption<?>> supportedOptions() {
        HashSet<SocketOption<?>> options = new HashSet<>(super.supportedOptions());
        addExtSocketOptions(ExtendedSocketOptions.options(SOCK_STREAM), options);
        return options;
    }

    private void addExtSocketOptions(Set<SocketOption<?>> extOptions,
            Set<SocketOption<?>> options) {
        extOptions.forEach((option) -> {
            if (option.name().equals("SO_FLOW_SLA")) {
                // SO_FLOW_SLA is Solaris specific option which is not applicable
                // for ServerSockets.
                // getSocket() will always return null for server socket
                if (getSocket() != null) {
                    options.add(option);
                }
            } else {
                options.add(option);
            }
        });
    }
    protected void socketSetOption(int opt, boolean b, Object val) throws SocketException {
        if (opt == SocketOptions.SO_REUSEPORT &&
            !supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT)) {
            throw new UnsupportedOperationException("unsupported option");
        }
        try {
            socketSetOption0(opt, b, val);
        } catch (SocketException se) {
            if (socket == null || !socket.isConnected())
                throw se;
        }
    }

    native void socketCreate(boolean isServer) throws IOException;

    native void socketConnect(InetAddress address, int port, int timeout)
        throws IOException;

    native void socketBind(InetAddress address, int port)
        throws IOException;

    native void socketListen(int count) throws IOException;

    native void socketAccept(SocketImpl s) throws IOException;

    native int socketAvailable() throws IOException;

    native void socketClose0(boolean useDeferredClose) throws IOException;

    native void socketShutdown(int howto) throws IOException;

    static native void initProto();

    native void socketSetOption0(int cmd, boolean on, Object value)
        throws SocketException;

    native int socketGetOption(int opt, Object iaContainerObj) throws SocketException;

    native void socketSendUrgentData(int data) throws IOException;
}
