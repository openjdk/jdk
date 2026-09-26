/*
 * Copyright (c) 1998, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4151834 8392732
 * @summary Test Socket.setSoLinger
 * @library /test/lib
 * @build jdk.test.lib.Platform jdk.test.lib.net.IPSupport
 * @run main SetSoLinger
 * @run main/othervm -Djava.net.preferIPv4Stack=true SetSoLinger
 * @run main/othervm -Djava.net.preferIPv6Addresses=true SetSoLinger
 */

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import jdk.test.lib.Platform;
import jdk.test.lib.net.IPSupport;

public class SetSoLinger {
    // on bsd and macos the linger value is limited to 32767 and on
    // rest of the platforms it is limited to 65535
    private static final int EXPECTED_MAX_LINGER = Platform.isOSX() || Platform.isBSD() ? 32767 : 65535;
    // some arbitrary large linger value which is greater the max limit
    private static final int LARGE_LINGER = EXPECTED_MAX_LINGER + 42;

    public static void main(String[] args) throws Exception {
        IPSupport.throwSkippedExceptionIfNonOperational();
        int actual;
        try (ServerSocket ss = new ServerSocket()) {
            InetSocketAddress addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            ss.bind(addr);
            // initiate the connection
            try (Socket s = new Socket(ss.getInetAddress(), ss.getLocalPort());
                 Socket accepted = ss.accept()) {

                // configure a high linger value
                System.out.println("setting linger to " + LARGE_LINGER + " for socket " + accepted);
                accepted.setSoLinger(true, LARGE_LINGER);
                actual = accepted.getSoLinger();
            }
        }
        if (actual != EXPECTED_MAX_LINGER) {
            throw new AssertionError("Unexpected linger value: " + actual
                    + ", expected: " + EXPECTED_MAX_LINGER);
        }
    }
}
