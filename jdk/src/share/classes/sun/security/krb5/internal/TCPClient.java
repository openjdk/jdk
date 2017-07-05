/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

/*
 *
 *  (C) Copyright IBM Corp. 1999 All Rights Reserved.
 *  Copyright 1997 The Open Group Research Institute.  All rights reserved.
 */

package sun.security.krb5.internal;

import java.io.*;
import java.net.*;

public class TCPClient {

    private Socket tcpSocket;
    private BufferedOutputStream out;
    private BufferedInputStream in;

    public TCPClient(String hostname, int port) throws IOException {
        tcpSocket = new Socket(hostname, port);
        out = new BufferedOutputStream(tcpSocket.getOutputStream());
        in = new BufferedInputStream(tcpSocket.getInputStream());
    }

    public void send(byte[] data) throws IOException {
        byte[] lenField = new byte[4];
        intToNetworkByteOrder(data.length, lenField, 0, 4);
        out.write(lenField);

        out.write(data);
        out.flush();
    }

    public byte[] receive() throws IOException {
        byte[] lenField = new byte[4];
        int count = readFully(lenField, 4);

        if (count != 4) {
            if (Krb5.DEBUG) {
                System.out.println(
                    ">>>DEBUG: TCPClient could not read length field");
            }
            return null;
        }

        int len = networkByteOrderToInt(lenField, 0, 4);
        if (Krb5.DEBUG) {
            System.out.println(
                ">>>DEBUG: TCPClient reading " + len + " bytes");
        }
        if (len <= 0) {
            if (Krb5.DEBUG) {
                System.out.println(
                    ">>>DEBUG: TCPClient zero or negative length field: "+len);
            }
            return null;
        }

        byte data[] = new byte[len];
        count = readFully(data, len);
        if (count != len) {
            if (Krb5.DEBUG) {
                System.out.println(
                    ">>>DEBUG: TCPClient could not read complete packet (" +
                    len + "/" + count + ")");
            }
            return null;
        } else {
            return data;
        }
    }

    public void close() throws IOException {
        tcpSocket.close();
    }

    /**
     * Read requested number of bytes before returning.
     * @return The number of bytes actually read; -1 if none read
     */
    private int readFully(byte[] inBuf, int total) throws IOException {
        int count, pos = 0;

        while (total > 0) {
            count = in.read(inBuf, pos, total);

            if (count == -1) {
                return (pos == 0? -1 : pos);
            }
            pos += count;
            total -= count;
        }
        return pos;
    }

    /**
     * Returns the integer represented by 4 bytes in network byte order.
     */
    private static final int networkByteOrderToInt(byte[] buf, int start,
        int count) {
        if (count > 4) {
            throw new IllegalArgumentException(
                "Cannot handle more than 4 bytes");
        }

        int answer = 0;

        for (int i = 0; i < count; i++) {
            answer <<= 8;
            answer |= ((int)buf[start+i] & 0xff);
        }
        return answer;
    }

    /**
     * Encodes an integer into 4 bytes in network byte order in the buffer
     * supplied.
     */
    private static final void intToNetworkByteOrder(int num, byte[] buf,
        int start, int count) {
        if (count > 4) {
            throw new IllegalArgumentException(
                "Cannot handle more than 4 bytes");
        }

        for (int i = count-1; i >= 0; i--) {
            buf[start+i] = (byte)(num & 0xff);
            num >>>= 8;
        }
    }
}
