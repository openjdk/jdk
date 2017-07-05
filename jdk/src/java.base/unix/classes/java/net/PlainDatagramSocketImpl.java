/*
 * Copyright (c) 2007, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import jdk.net.*;
import static sun.net.ExtendedOptionsImpl.*;

/*
 * On Unix systems we simply delegate to native methods.
 *
 * @author Chris Hegarty
 */

class PlainDatagramSocketImpl extends AbstractPlainDatagramSocketImpl
{
    static {
        init();
    }

    protected <T> void setOption(SocketOption<T> name, T value) throws IOException {
        if (!name.equals(ExtendedSocketOptions.SO_FLOW_SLA)) {
            super.setOption(name, value);
        } else {
            if (isClosed()) {
                throw new SocketException("Socket closed");
            }
            checkSetOptionPermission(name);
            checkValueType(value, SocketFlow.class);
            setFlowOption(getFileDescriptor(), (SocketFlow)value);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> T getOption(SocketOption<T> name) throws IOException {
        if (!name.equals(ExtendedSocketOptions.SO_FLOW_SLA)) {
            return super.getOption(name);
        }
        if (isClosed()) {
            throw new SocketException("Socket closed");
        }
        checkGetOptionPermission(name);
        SocketFlow flow = SocketFlow.create();
        getFlowOption(getFileDescriptor(), flow);
        return (T)flow;
    }

    protected Set<SocketOption<?>> supportedOptions() {
        HashSet<SocketOption<?>> options = new HashSet<>(
            super.supportedOptions());

        if (flowSupported()) {
            options.add(ExtendedSocketOptions.SO_FLOW_SLA);
        }
        return options;
    }

    protected void socketSetOption(int opt, Object val) throws SocketException {
        try {
            socketSetOption0(opt, val);
        } catch (SocketException se) {
            if (!connected)
                throw se;
        }
    }

    protected synchronized native void bind0(int lport, InetAddress laddr)
        throws SocketException;

    protected native void send(DatagramPacket p) throws IOException;

    protected synchronized native int peek(InetAddress i) throws IOException;

    protected synchronized native int peekData(DatagramPacket p) throws IOException;

    protected synchronized native void receive0(DatagramPacket p)
        throws IOException;

    protected native void setTimeToLive(int ttl) throws IOException;

    protected native int getTimeToLive() throws IOException;

    @Deprecated
    protected native void setTTL(byte ttl) throws IOException;

    @Deprecated
    protected native byte getTTL() throws IOException;

    protected native void join(InetAddress inetaddr, NetworkInterface netIf)
        throws IOException;

    protected native void leave(InetAddress inetaddr, NetworkInterface netIf)
        throws IOException;

    protected native void datagramSocketCreate() throws SocketException;

    protected native void datagramSocketClose();

    protected native void socketSetOption0(int opt, Object val)
        throws SocketException;

    protected native Object socketGetOption(int opt) throws SocketException;

    protected native void connect0(InetAddress address, int port) throws SocketException;

    protected native void disconnect0(int family);

    native int dataAvailable();

    /**
     * Perform class load-time initializations.
     */
    private static native void init();
}
