/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @library /javax/net/ssl/templates
 * @bug 8242008
 * @summary Verifies resumption fails with 0 NSTs and session creation off
 * @run main/othervm MultiNSTNoSessionCreation -Djdk.tls.client.protocols=TLSv1.3 -Djdk.tls.server.newSessionTicketCount=0
 * @run main/othervm MultiNSTNoSessionCreation -Djdk.tls.client.protocols=TLSv1.2 -Djdk.tls.server.newSessionTicketCount=0
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.Arrays;

/**
 * With no NSTs sent by the server, try to resume the session with
 * setEnabledSessionCreation(false).  The test should get an exception and
 * fail to connect.
 */

public class MultiNSTNoSessionCreation {

    public static void main(String[] args) throws Exception {

        if (!args[0].equalsIgnoreCase("p")) {
            StringBuilder sb = new StringBuilder();
            Arrays.stream(args).forEach(a -> sb.append(a).append(" "));
            String params = sb.toString();
            System.setProperty("test.java.opts", System.getProperty("test.java.opts") +
                " -Dtest.src=" + System.getProperty("test.src") +
                    " -Dtest.jdk=" + System.getProperty("test.jdk") +
                    " -Dtest.root=" + System.getProperty("test.root") +
                    " -Djavax.net.debug=ssl " + params);

            System.out.println("test.java.opts: " +
                System.getProperty("test.java.opts"));

            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                    "MultiNSTNoSessionCreation", "p");

            OutputAnalyzer output = ProcessTools.executeProcess(pb);
            try {
                if (output.stderrContains(
                    "(PROTOCOL_VERSION): New session creation is disabled")) {
                    return;
                }
            } catch (RuntimeException e) {
                throw new Exception("Error collecting data", e);
            }
            throw new Exception("Disabled creation msg not found");
        }

        TLSBase.Server server = new TLSBase.Server();
        server.serverLatch.await();
        System.out.println("------  Server ready, starting initial client.");
        TLSBase.Client initial = new TLSBase.Client();
        initial.connect();
        System.out.println(
            "------  Resume client w/ setEnableSessionCreation set to false");
        TLSBase.Client resumClient = new TLSBase.Client(initial);
        resumClient.socket.setEnableSessionCreation(false);
        resumClient.connect();

        System.out.println("------  Closing connections");
        initial.close();
        resumClient.close();
        server.close();
        System.out.println("------  End");
        System.exit(0);
    }
}
