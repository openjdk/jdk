/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.

/*
 * @test
 * @bug 8367133
 * @summary Verify that handshake succeeds when Finished message is fragmented
 * @modules java.base/sun.security.util
 * @library /test/lib
 * @build DTLSOverDatagram
 * @run main/othervm FragmentedFinished
 */

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

public class FragmentedFinished extends DTLSOverDatagram {
    private SSLEngine serverSSLEngine;
    public static void main(String[] args) throws Exception {
        FragmentedFinished testCase = new FragmentedFinished();
        testCase.runTest(testCase);
    }

    @Override
    SSLEngine createSSLEngine(boolean isClient) throws Exception {
        SSLEngine sslEngine = super.createSSLEngine(isClient);
        if (!isClient) {
            serverSSLEngine = sslEngine;
        }
        return sslEngine;
    }

    @Override
    DatagramPacket createHandshakePacket(byte[] ba, SocketAddress socketAddr) {
        if (ba.length < 30) { // detect ChangeCipherSpec
            // Reduce the maximumPacketSize to force fragmentation
            // of the Finished message
            SSLParameters params = serverSSLEngine.getSSLParameters();
            params.setMaximumPacketSize(53);
            serverSSLEngine.setSSLParameters(params);
        }

        return super.createHandshakePacket(ba, socketAddr);
    }
}
