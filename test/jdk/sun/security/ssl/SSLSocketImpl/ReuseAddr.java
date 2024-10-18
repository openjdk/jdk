/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4482446
 * @summary java.net.SocketTimeoutException on 98, NT, 2000 for JSSE
 * @library /javax/net/ssl/templates
 * @run main ReuseAddr
 *
 *     SunJSSE does not support dynamic system properties, no way to re-use
 *     system properties in samevm/agentvm mode.
 * @author Brad Wetmore
 */

import java.net.ServerSocket;
import java.net.BindException;

public class ReuseAddr extends SSLSocketTemplate {

    private static final int MAX_ATTEMPTS = 3;

    @Override
    protected void doServerSide() throws Exception {
        super.doServerSide();

        // Note that if this port is already used by another test,
        // this test will fail.
        System.out.println("Try rebinding to same port: " + serverPort);
        try (ServerSocket server = new ServerSocket(serverPort)) {
            System.out.println("Server port: " + server.getLocalPort());
        }
    }

    public static void main(String[] args) throws Exception {
        for (int i=1 ; i <= MAX_ATTEMPTS; i++) {
            try {
                new ReuseAddr().run();
                System.out.println("Test succeeded at attempt " + i);
                break;
            } catch (BindException x) {
                System.out.println("attempt " + i + " failed: " + x);
                if (i == MAX_ATTEMPTS) {
                    String msg = "Could not succeed after " + i + " attempts";
                    System.err.println(msg);
                    throw new AssertionError("Failed to reuse address: " + msg, x);
                } else {
                    System.out.println("Retrying...");
                }
            }
        }
    }
}
