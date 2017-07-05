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

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;


// Adaptor class for java.net-style options
//
// The option get/set methods in the socket, server-socket, and datagram-socket
// adaptors delegate to an instance of this class.
//

class OptionAdaptor {                                   // package-private

    private final SocketOpts.IP opts;

    OptionAdaptor(SocketChannelImpl sc) {
        opts = (SocketOpts.IP)sc.options();
    }

    OptionAdaptor(ServerSocketChannelImpl ssc) {
        opts = (SocketOpts.IP)ssc.options();
    }

    OptionAdaptor(DatagramChannelImpl dc) {
        opts = (SocketOpts.IP)dc.options();
    }

    private SocketOpts.IP opts() {
        return opts;
    }

    private SocketOpts.IP.TCP tcpOpts() {
        return (SocketOpts.IP.TCP)opts;
    }

    public void setTcpNoDelay(boolean on) throws SocketException {
        try {
            tcpOpts().noDelay(on);
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    public boolean getTcpNoDelay() throws SocketException {
        try {
            return tcpOpts().noDelay();
        } catch (Exception x) {
            Net.translateToSocketException(x);
            return false;               // Never happens
        }
    }

    public void setSoLinger(boolean on, int linger) throws SocketException {
        try {
            if (linger > 65535)
                linger = 65535;
            opts().linger(on ? linger : -1);
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    public int getSoLinger() throws SocketException {
        try {
            return opts().linger();
        } catch (Exception x) {
            Net.translateToSocketException(x);
            return 0;                   // Never happens
        }
    }

    public void setOOBInline(boolean on) throws SocketException {
        try {
            opts().outOfBandInline(on);
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    public boolean getOOBInline() throws SocketException {
        try {
            return opts().outOfBandInline();
        } catch (Exception x) {
            Net.translateToSocketException(x);
            return false;               // Never happens
        }
    }

    public void setSendBufferSize(int size)
        throws SocketException
    {
        try {
            opts().sendBufferSize(size);
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    public int getSendBufferSize() throws SocketException {
        try {
            return opts().sendBufferSize();
        } catch (Exception x) {
            Net.translateToSocketException(x);
            return 0;                   // Never happens
        }
    }

    public void setReceiveBufferSize(int size)
        throws SocketException
    {
        try {
            opts().receiveBufferSize(size);
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    public int getReceiveBufferSize() throws SocketException {
        try {
            return opts().receiveBufferSize();
        } catch (Exception x) {
            Net.translateToSocketException(x);
            return 0;                   // Never happens
        }
    }

    public void setKeepAlive(boolean on) throws SocketException {
        try {
            opts().keepAlive(on);
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    public boolean getKeepAlive() throws SocketException {
        try {
            return opts().keepAlive();
        } catch (Exception x) {
            Net.translateToSocketException(x);
            return false;               // Never happens
        }
    }

    public void setTrafficClass(int tc) throws SocketException {
        if (tc < 0 || tc > 255)
            throw new IllegalArgumentException("tc is not in range 0 -- 255");
        try {
            opts().typeOfService(tc);
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    public int getTrafficClass() throws SocketException {
        try {
            return opts().typeOfService();
        } catch (Exception x) {
            Net.translateToSocketException(x);
            return 0;                   // Never happens
        }
    }

    public void setReuseAddress(boolean on)
        throws SocketException
    {
        try {
            opts().reuseAddress(on);
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    public boolean getReuseAddress() throws SocketException {
        try {
            return opts().reuseAddress();
        } catch (Exception x) {
            Net.translateToSocketException(x);
            return false;               // Never happens
        }
    }

    public void setBroadcast(boolean on)
        throws SocketException
    {
        try {
            opts().broadcast(on);
        } catch (Exception x) {
            Net.translateToSocketException(x);
        }
    }

    public boolean getBroadcast() throws SocketException {
        try {
            return opts().broadcast();
        } catch (Exception x) {
            Net.translateToSocketException(x);
            return false;               // Never happens
        }
    }

}
