/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;

public class CheckIPv6Service {

    static boolean isIPv6Available() {
        try {
            new ServerSocket(0,0, InetAddress.getByAddress(new byte[16])).close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void doIt(SocketChannel sc, int closeAfter, int delay) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(1024);
        int total = 0;
        for (;;) {
            bb.clear();
            int n = sc.read(bb);
            if (n < 0) {
                break;
            }
            total += n;

            // echo
            bb.flip();
            sc.write(bb);

            // close after X bytes?
            if (closeAfter > 0 && total >= closeAfter) {
                break;
            }
        }

        sc.close();
        if (delay > 0) {
            try {
                Thread.currentThread().sleep(delay);
            } catch (InterruptedException x) { }
        }
    }

    public static void main(String args[]) throws IOException {
        // check if IPv6 is available; if it is, behave like EchoService.
        if (!isIPv6Available()) {
            return;
        }

        Channel c = System.inheritedChannel();
        if (c == null) {
            return;
        }

        if (c instanceof SocketChannel) {
            int closeAfter = 0;
            int delay = 0;
            if (args.length > 0) {
                closeAfter = Integer.parseInt(args[0]);
            }
            if (args.length > 1) {
                delay = Integer.parseInt(args[1]);
            }
            doIt((SocketChannel)c, closeAfter, delay);
        }
    }

}
