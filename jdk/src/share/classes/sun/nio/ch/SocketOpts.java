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

import java.io.IOException;
import java.nio.*;
import java.net.NetworkInterface;


// Typical use:
//
//     sc.options()
//         .noDelay(true)
//         .typeOfService(SocketOpts.IP.TOS_RELIABILITY)
//         .sendBufferSize(1024)
//         .receiveBufferSize(1024)
//         .keepAlive(true);
//


public interface SocketOpts {   // SocketOptions already used in java.net

    // Options that apply to all kinds of sockets

    // SO_BROADCAST
    public abstract boolean broadcast() throws IOException;
    public abstract SocketOpts broadcast(boolean b) throws IOException;

    // SO_KEEPALIVE
    public abstract boolean keepAlive() throws IOException;
    public abstract SocketOpts keepAlive(boolean b) throws IOException;

    // SO_LINGER
    public abstract int linger() throws IOException;
    public abstract SocketOpts linger(int n) throws IOException;

    // SO_OOBINLINE
    public abstract boolean outOfBandInline() throws IOException;
    public abstract SocketOpts outOfBandInline(boolean b) throws IOException;

    // SO_RCVBUF
    public abstract int receiveBufferSize() throws IOException;
    public abstract SocketOpts receiveBufferSize(int n) throws IOException;

    // SO_SNDBUF
    public abstract int sendBufferSize() throws IOException;
    public abstract SocketOpts sendBufferSize(int n) throws IOException;

    // SO_REUSEADDR
    public abstract boolean reuseAddress() throws IOException;
    public abstract SocketOpts reuseAddress(boolean b) throws IOException;


    // IP-specific options

    public static interface IP
        extends SocketOpts
    {

        // IP_MULTICAST_IF2
        public abstract NetworkInterface multicastInterface()
            throws IOException;
        public abstract IP multicastInterface(NetworkInterface ni)
            throws IOException;

        // IP_MULTICAST_LOOP
        public abstract boolean multicastLoop() throws IOException;
        public abstract IP multicastLoop(boolean b) throws IOException;

        // IP_TOS
        public static final int TOS_LOWDELAY = 0x10;
        public static final int TOS_THROUGHPUT = 0x08;
        public static final int TOS_RELIABILITY = 0x04;
        public static final int TOS_MINCOST = 0x02;
        public abstract int typeOfService() throws IOException;
        public abstract IP typeOfService(int tos) throws IOException;


        // TCP-specific options

        public static interface TCP
            extends IP
        {
            // TCP_NODELAY
            public abstract boolean noDelay() throws IOException;
            public abstract TCP noDelay(boolean b) throws IOException;

        }

    }

}
