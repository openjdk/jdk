/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @run main/othervm MultiNSTClient -Djdk.tls.client.protocols=TLSv1.3 -Djdk.tls.server.enableSessionTicketExtension=true -Djdk.tls.client.enableSessionTicketExtension=true
 * @summary jkljkf fjkslfjfdsl
 */


import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import javax.net.ssl.SSLSession;
import java.util.HexFormat;
import java.util.List;

public class MultiNSTClient {

    static HexFormat hex = HexFormat.of();

    public static void main(String[] args) throws Exception {

        if (!args[0].equalsIgnoreCase("p")) {
            System.setProperty("test.java.opts",
                "-Dtest.src=" + System.getProperty("test.src") +
                    " -Dtest.jdk=" + System.getProperty("test.jdk") +
                    " -Dtest.root=" + System.getProperty("test.root") +
                    " -Djavax.net.debug=ssl,handshake "

                );

            System.out.println("test.java.opts: " +
                System.getProperty("test.java.opts"));

            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                Utils.addTestJavaOpts("MultiNSTClient", "p"));

            OutputAnalyzer output = ProcessTools.executeProcess(pb);
            System.out.println("I'm here");
            List<String> list = output.stderrShouldContain("MultiNST PSK").asLines().stream().filter(s -> s.contains("MultiNST PSK")).toList();
            List<String> serverPSK = list.stream().filter(s -> s.contains("MultiNST PSK (Server)")).toList();
            List<String> clientPSK = list.stream().filter(s -> s.contains("MultiNST PSK (Client)")).toList();
            //System.err.println("found server: " + serverPSK.size() + " " + serverPSK);
            System.out.println("found list: " + list.size());
            System.out.println("found server: " + serverPSK.size());
            serverPSK.stream().forEach(s -> System.out.println("\t" + s));
            //System.err.println("found client: " + clientPSK.size() + " " + clientPSK);
            System.out.println("found client: " + clientPSK.size());
            clientPSK.stream().forEach(s -> System.out.println("\t" + s));
            for (int i = 0; i < 2; i++) {
                String svr = serverPSK.getFirst();
                String cli = clientPSK.getFirst();
                if (svr.regionMatches(svr.length() - 16, cli, cli.length() - 16, 16)) {
                    System.out.println("entry " + (i + 1) + " match.");
                } else {
                    System.out.println("entry " + (i + 1) + " server and client PSK didn't match:");
                    System.out.println("  server: " + svr);
                    System.out.println("  client: " + cli);
                    throw new Exception("match failed");
                }
            }
            return;
        }

        TLSBase.Server server = new TLSBase.Server();

        System.out.println("------  Start connection");
        TLSBase.Client client1 = new TLSBase.Client();

        SSLSession initialSession = client1.getSession();
        System.out.println("id = " + hex.formatHex(initialSession.getId()));
        System.out.println("session = " + initialSession);

        System.out.println("------  getNewSession from original client");
        SSLSession resumption;
        resumption = client1.getNewSession();
        System.out.println("id = " + hex.formatHex(resumption.getId()));
        System.out.println("session = " + resumption);
        if (!initialSession.toString().equalsIgnoreCase(resumption.toString())) {
            throw new Exception("Resumed session did not match");
        }

        System.out.println("------  Second getNewSession from original client");
        resumption = client1.getNewSession();
        System.out.println("id = " + hex.formatHex(resumption.getId()));
        System.out.println("session = " + resumption);
        if (!initialSession.toString().equalsIgnoreCase(resumption.toString())) {
            throw new Exception("Resumed session did not match");
        }

        System.out.println("------  new client");
        TLSBase.Client client2 = new TLSBase.Client();
        SSLSession newSession = client2.getSession();

        System.out.println("id = " + hex.formatHex(newSession.getId()));
        System.out.println("session = " + newSession);
        if (initialSession.toString().equalsIgnoreCase(newSession.toString())) {
            throw new Exception("new session is the same as the initial.");
        }
        System.out.println("------  Closing connections");
        client1.close();
        client2.close();
        server.close(client1);
        System.out.println("------  End");
        System.exit(0);
    }
}

//run main/othervm -Djdk.tls.client.protocols=TLSv1.2 -Djdk.tls.server.enableSessionTicketExtension=false -Djdk.tls.client.enableSessionTicketExtension=false MultiNSTClient
//run main/othervm -Djdk.tls.client.protocols=TLSv1.2 -Djdk.tls.server.enableSessionTicketExtension=true -Djdk.tls.client.enableSessionTicketExtension=true MultiNSTClient
