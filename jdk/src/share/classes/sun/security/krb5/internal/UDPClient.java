/*
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

/*
 *
 *  (C) Copyright IBM Corp. 1999 All Rights Reserved.
 *  Copyright 1997 The Open Group Research Institute.  All rights reserved.
 */

package sun.security.krb5.internal;

import java.io.*;
import java.net.*;

public class UDPClient {
    InetAddress iaddr;
    int iport;
    int bufSize = 65507;
    DatagramSocket dgSocket;
    DatagramPacket dgPacketIn;

    public UDPClient(InetAddress newIAddr, int port)
        throws SocketException {
        iaddr = newIAddr;
        iport = port;
        dgSocket = new DatagramSocket();
    }

    public UDPClient(String hostname, int port)
        throws UnknownHostException, SocketException {
        iaddr = InetAddress.getByName(hostname);
        iport = port;
        dgSocket = new DatagramSocket();
    }

    public UDPClient(String hostname, int port, int timeout)
        throws UnknownHostException, SocketException {
        iaddr = InetAddress.getByName(hostname);
        iport = port;
        dgSocket = new DatagramSocket();
        dgSocket.setSoTimeout(timeout);
    }

    public void setBufSize(int newBufSize) {
        bufSize = newBufSize;
    }

    public InetAddress getInetAddress() {
        if (dgPacketIn != null)
            return dgPacketIn.getAddress();
        return null;
    }

    public void send(byte[] data) throws IOException {
        DatagramPacket dgPacketOut = new DatagramPacket(data, data.length,
                                                        iaddr, iport);
        dgSocket.send(dgPacketOut);
    }

    public byte[] receive() throws IOException {
        byte ibuf[] = new byte[bufSize];
        dgPacketIn = new DatagramPacket(ibuf, ibuf.length);
        try {
            dgSocket.receive(dgPacketIn);
        }
        catch (SocketException e) {
            dgSocket.receive(dgPacketIn);
        }
        byte[] data = new byte[dgPacketIn.getLength()];
        System.arraycopy(dgPacketIn.getData(), 0, data, 0,
                         dgPacketIn.getLength());
        return data;
    }

    public void close() {
        dgSocket.close();
    }
}
