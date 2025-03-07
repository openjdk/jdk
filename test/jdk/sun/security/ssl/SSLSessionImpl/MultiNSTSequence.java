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
 * @summary Verifies sequence of used NST entries from the cache queue.
 * @run main/othervm MultiNSTSequence -Djdk.tls.server.newSessionTicketCount=2
 */

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import javax.net.ssl.SSLSession;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * This test verifies that multiple NSTs take the oldest PSK from the
 * QueueCacheEntry stored in the TLS Session Cache.
 *
 * Note: Beyond 9 iterations the PSK id verification code becomes complicated
 * with a QueueCacheEntry limit set to retain only the 10 newest entries.
 *
 * TLS 1.2 spec does not specify multiple NST behavior.
 */

public class MultiNSTSequence {

    static HexFormat hex = HexFormat.of();
    static final int ITERATIONS = 9;

    public static void main(String[] args) throws Exception {

        if (!args[0].equalsIgnoreCase("p")) {
            StringBuilder sb = new StringBuilder();
            Arrays.stream(args).forEach(a -> sb.append(a).append(" "));
            String params = sb.toString();
            System.setProperty("test.java.opts", System.getProperty("test.java.opts") +
                " -Dtest.src=" + System.getProperty("test.src") +
                    " -Dtest.jdk=" + System.getProperty("test.jdk") +
                    " -Dtest.root=" + System.getProperty("test.root") +
                    " -Djavax.net.debug=ssl,handshake " + params
                              );

            System.out.println("test.java.opts: " +
                System.getProperty("test.java.opts"));

            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                Utils.addTestJavaOpts("MultiNSTSequence", "p"));

            OutputAnalyzer output = ProcessTools.executeProcess(pb);
            boolean pass = true;
            try {
                List<String> list = output.stderrShouldContain("MultiNST PSK").
                    asLines().stream().filter(s ->
                        s.contains("MultiNST PSK")).toList();
                List<String> serverPSK = list.stream().filter(s ->
                    s.contains("MultiNST PSK (Server)")).toList();
                List<String> clientPSK = list.stream().filter(s ->
                    s.contains("MultiNST PSK (Client)")).toList();
                System.out.println("found list: " + list.size());
                System.out.println("found server: " + serverPSK.size());
                serverPSK.stream().forEach(s -> System.out.println("\t" + s));
                System.out.println("found client: " + clientPSK.size());
                clientPSK.stream().forEach(s -> System.out.println("\t" + s));
                int i;
                for (i = 0; i < ITERATIONS; i++) {
                    String svr = serverPSK.get(i);
                    String cli = clientPSK.get(i);
                    if (svr.regionMatches(svr.length() - 16, cli, cli.length() - 16, 16)) {
                        System.out.println("entry " + (i + 1) + " match.");
                    } else {
                        System.out.println("entry " + (i + 1) + " server and client PSK didn't match:");
                        System.out.println("  server: " + svr);
                        System.out.println("  client: " + cli);
                        pass = false;
                    }
                }
            } catch (RuntimeException e) {
                System.out.println("Server and Client PSK usage order is not" +
                    " the same.");
                pass = false;
            }

            if (!pass) {
                throw new Exception("Test failed: " + params);
            }
            System.out.println("Test Passed");
            return;
        }

        TLSBase.Server server = new TLSBase.Server();

        System.out.println("------  Initial connection");
        TLSBase.Client initial = new TLSBase.Client();

        SSLSession initialSession = initial.connect().getSession();
        System.out.println("id = " + hex.formatHex(initialSession.getId()));
        System.out.println("session = " + initialSession);

        System.out.println("------  Resume client");
        for (int i = 0; i < ITERATIONS; i++) {
            SSLSession r = new TLSBase.Client(initial).connect().getSession();
            StringBuilder sb = new StringBuilder(100);
            sb.append("Iteration: ").append(i);
            sb.append("\tid = ").append(hex.formatHex(r.getId()));
            sb.append("\tsession = ").append(r);
            System.out.println(sb);
            if (!initialSession.toString().equalsIgnoreCase(r.toString())) {
                throw new Exception("Resumed session did not match");
            }
        }

        System.out.println("------  Closing connections");
        initial.close();
        server.close();
        System.out.println("------  End");
        System.exit(0);
    }
}
