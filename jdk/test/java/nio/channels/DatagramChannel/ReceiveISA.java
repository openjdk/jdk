/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4503641
 * @summary Check that DatagramChannel.receive returns a new SocketAddress
 *          when it receives a packet from the same source address but
 *          different endpoint.
 */
import java.nio.*;
import java.nio.channels.*;
import java.net.*;

public class ReceiveISA {

    public static void main(String args[]) throws Exception {

        // clients
        DatagramChannel dc1 = DatagramChannel.open();
        DatagramChannel dc2 = DatagramChannel.open();

        // bind server to any port
        DatagramChannel dc3 = DatagramChannel.open();
        dc3.socket().bind((SocketAddress)null);

        // get server address
        InetAddress lh = InetAddress.getLocalHost();
        InetSocketAddress isa
            = new InetSocketAddress( lh, dc3.socket().getLocalPort() );

        ByteBuffer bb = ByteBuffer.allocateDirect(100);
        bb.put("Dia duit!".getBytes());
        bb.flip();

        dc1.send(bb, isa);      // packet 1 from dc1
        dc1.send(bb, isa);      // packet 2 from dc1
        dc2.send(bb, isa);      // packet 3 from dc1

        // receive 3 packets
        dc3.socket().setSoTimeout(1000);
        ByteBuffer rb = ByteBuffer.allocateDirect(100);
        SocketAddress sa[] = new SocketAddress[3];
        for (int i=0; i<3; i++) {
            sa[i] = dc3.receive(rb);
            rb.clear();
        }

        /*
         * Check that sa[0] equals sa[1] (both from dc1)
         * Check that sa[1] not equal to sa[2] (one from dc1, one from dc2)
         */

        if (!sa[0].equals(sa[1])) {
            throw new Exception("Source address for packets 1 & 2 should be equal");
        }

        if (sa[1].equals(sa[2])) {
            throw new Exception("Source address for packets 2 & 3 should be different");
        }
    }

}
