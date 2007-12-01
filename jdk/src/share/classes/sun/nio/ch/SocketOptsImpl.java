/*
 * Copyright 2001 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketOptions;
import java.nio.channels.*;


class SocketOptsImpl
    implements SocketOpts
{

    static abstract class Dispatcher {
        abstract int getInt(int opt) throws IOException;
        abstract void setInt(int opt, int arg) throws IOException;
        // Others that pass addresses, etc., will come later
    }

    private final Dispatcher d;

    SocketOptsImpl(Dispatcher d) {
        this.d = d;
    }

    protected boolean getBoolean(int opt) throws IOException {
        return d.getInt(opt) > 0;
    }

    protected void setBoolean(int opt, boolean b) throws IOException {
        d.setInt(opt, b ? 1 : 0);
    }

    protected int getInt(int opt) throws IOException {
        return d.getInt(opt);
    }

    protected void setInt(int opt, int n) throws IOException {
        d.setInt(opt, n);
    }

    protected NetworkInterface getNetworkInterface(int opt)
        throws IOException
    {
        throw new UnsupportedOperationException("NYI");
    }

    protected void setNetworkInterface(int opt, NetworkInterface ni)
        throws IOException
    {
        throw new UnsupportedOperationException("NYI");
    }

    protected void addToString(StringBuffer sb, String s) {
        char c = sb.charAt(sb.length() - 1);
        if ((c != '[') && (c != '='))
            sb.append(' ');
        sb.append(s);
    }

    protected void addToString(StringBuffer sb, int n) {
        addToString(sb, Integer.toString(n));
    }


    // SO_BROADCAST

    public boolean broadcast() throws IOException {
        return getBoolean(SocketOptions.SO_BROADCAST);
    }

    public SocketOpts broadcast(boolean b) throws IOException {
        setBoolean(SocketOptions.SO_BROADCAST, b);
        return this;
    }


    // SO_KEEPALIVE

    public boolean keepAlive() throws IOException {
        return getBoolean(SocketOptions.SO_KEEPALIVE);
    }

    public SocketOpts keepAlive(boolean b) throws IOException {
        setBoolean(SocketOptions.SO_KEEPALIVE, b);
        return this;
    }


    // SO_LINGER

    public int linger() throws IOException {
        return getInt(SocketOptions.SO_LINGER);
    }

    public SocketOpts linger(int n) throws IOException {
        setInt(SocketOptions.SO_LINGER, n);
        return this;
    }


    // SO_OOBINLINE

    public boolean outOfBandInline() throws IOException {
        return getBoolean(SocketOptions.SO_OOBINLINE);
    }

    public SocketOpts outOfBandInline(boolean b) throws IOException {
        setBoolean(SocketOptions.SO_OOBINLINE, b);
        return this;
    }


    // SO_RCVBUF

    public int receiveBufferSize() throws IOException {
        return getInt(SocketOptions.SO_RCVBUF);
    }

    public SocketOpts receiveBufferSize(int n) throws IOException {
        if (n <= 0)
            throw new IllegalArgumentException("Invalid receive size");
        setInt(SocketOptions.SO_RCVBUF, n);
        return this;
    }


    // SO_SNDBUF

    public int sendBufferSize() throws IOException {
        return getInt(SocketOptions.SO_SNDBUF);
    }

    public SocketOpts sendBufferSize(int n) throws IOException {
        if (n <= 0)
            throw new IllegalArgumentException("Invalid send size");
        setInt(SocketOptions.SO_SNDBUF, n);
        return this;
    }


    // SO_REUSEADDR

    public boolean reuseAddress() throws IOException {
        return getBoolean(SocketOptions.SO_REUSEADDR);
    }

    public SocketOpts reuseAddress(boolean b) throws IOException {
        setBoolean(SocketOptions.SO_REUSEADDR, b);
        return this;
    }


    // toString

    protected void toString(StringBuffer sb) throws IOException {
        int n;
        if (broadcast())
            addToString(sb, "broadcast");
        if (keepAlive())
            addToString(sb, "keepalive");
        if ((n = linger()) > 0) {
            addToString(sb, "linger=");
            addToString(sb, n);
        }
        if (outOfBandInline())
            addToString(sb, "oobinline");
        if ((n = receiveBufferSize()) > 0) {
            addToString(sb, "rcvbuf=");
            addToString(sb, n);
        }
        if ((n = sendBufferSize()) > 0) {
            addToString(sb, "sndbuf=");
            addToString(sb, n);
        }
        if (reuseAddress())
            addToString(sb, "reuseaddr");
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(this.getClass().getInterfaces()[0].getName());
        sb.append('[');
        int i = sb.length();
        try {
            toString(sb);
        } catch (IOException x) {
            sb.setLength(i);
            sb.append("closed");
        }
        sb.append(']');
        return sb.toString();
    }


    // IP-specific socket options

    static class IP
        extends SocketOptsImpl
        implements SocketOpts.IP
    {

        IP(Dispatcher d) {
            super(d);
        }


        // IP_MULTICAST_IF2
        // ## Do we need IP_MULTICAST_IF also?

        public NetworkInterface multicastInterface() throws IOException {
            return getNetworkInterface(SocketOptions.IP_MULTICAST_IF2);
        }

        public SocketOpts.IP multicastInterface(NetworkInterface ni)
            throws IOException
        {
            setNetworkInterface(SocketOptions.IP_MULTICAST_IF2, ni);
            return this;
        }


        // IP_MULTICAST_LOOP

        public boolean multicastLoop() throws IOException {
            return getBoolean(SocketOptions.IP_MULTICAST_LOOP);
        }

        public SocketOpts.IP multicastLoop(boolean b) throws IOException {
            setBoolean(SocketOptions.IP_MULTICAST_LOOP, b);
            return this;
        }


        // IP_TOS

        public int typeOfService() throws IOException {
            return getInt(SocketOptions.IP_TOS);
        }

        public SocketOpts.IP typeOfService(int tos) throws IOException {
            setInt(SocketOptions.IP_TOS, tos);
            return this;
        }


        // toString

        protected void toString(StringBuffer sb) throws IOException {
            super.toString(sb);
            int n;
            if ((n = typeOfService()) > 0) {
                addToString(sb, "tos=");
                addToString(sb, n);
            }
        }


        // TCP-specific IP options

        public static class TCP
            extends SocketOptsImpl.IP
            implements SocketOpts.IP.TCP
        {

            TCP(Dispatcher d) {
                super(d);
            }

            // TCP_NODELAY

            public boolean noDelay() throws IOException {
                return getBoolean(SocketOptions.TCP_NODELAY);
            }

            public SocketOpts.IP.TCP noDelay(boolean b) throws IOException {
                setBoolean(SocketOptions.TCP_NODELAY, b);
                return this;
            }


            // toString

            protected void toString(StringBuffer sb) throws IOException {
                super.toString(sb);
                if (noDelay())
                    addToString(sb, "nodelay");
            }

        }
    }

}
