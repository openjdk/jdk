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
 * @summary Verifies multiple PSKs are used by TLSv1.3
 * @run main/othervm MultiNSTParallel 10 -Djdk.tls.client.protocols=TLSv1.3
 */

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import javax.net.ssl.SSLSession;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * This test verifies that parallel resumption connections successfully get
 * a PSK entry and not initiate a full handshake.
 *
 * Note:  THe first argument after 'MultiNSTParallel' is the ticket count
 * The test will set 'jdk.tls.server.NewSessionTicketCount` to that number and
 * will start the same number of resumption client attempts. The ticket count
 * must be the same or larger than resumption attempts otherwise the queue runs
 * empty and the test will fail.
 *
 * Because this test runs parallel connections, the thread order finish is not
 * guaranteed.  Each client NST id is checked with all server NSTs ids until
 * a match is found.  When a match is found, it is removed from the list to
 * verify no NST was used more than once.
 *
 * TLS 1.2 spec does not specify multiple NST behavior.
 */

public class MultiNSTParallel {

    static HexFormat hex = HexFormat.of();
    final static CountDownLatch wait = new CountDownLatch(1);

    static class ClientThread extends Thread {
        TLSBase.Client client;

        ClientThread(TLSBase.Client c) {
            client = c;
        }

        public void run() {
            String name = Thread.currentThread().getName();
            SSLSession r;
            System.err.println("waiting " + Thread.currentThread().getName());
            try {
                wait.await();
                r = new TLSBase.Client(client).connect().getSession();
            } catch (Exception e) {
                throw new RuntimeException(name + ": " +e);
            }
            StringBuffer sb = new StringBuffer(100);
            sb.append("(").append(name).append(") id = ");
            sb.append(hex.formatHex(r.getId()));
            sb.append("\n(").append(name).append(") session = ").append(r);
            if (!client.getSession().toString().equalsIgnoreCase(r.toString())) {
                throw new RuntimeException("(" + name +
                    ") Resumed session did not match");
            }
        }
    }

    static boolean pass = true;

    public static void main(String[] args) throws Exception {

        if (!args[0].equalsIgnoreCase("p")) {
            int ticketCount = Integer.parseInt(args[0]);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                sb.append(" ").append(args[i]);
            }
            String params = sb.toString();
            System.setProperty("test.java.opts", System.getProperty("test.java.opts") +
                " -Dtest.src=" + System.getProperty("test.src") +
                    " -Dtest.jdk=" + System.getProperty("test.jdk") +
                    " -Dtest.root=" + System.getProperty("test.root") +
                    " -Djavax.net.debug=ssl,handshake " +
                    " -Djdk.tls.server.newSessionTicketCount=" + ticketCount +
                    params);

            boolean TLS13 = args[1].contains("1.3");

            System.out.println("test.java.opts: " +
                System.getProperty("test.java.opts"));

            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                Utils.addTestJavaOpts("MultiNSTParallel", "p"));

            OutputAnalyzer output = ProcessTools.executeProcess(pb);
            try {
                List<String> list = output.stderrShouldContain("MultiNST PSK").
                    asLines().stream().filter(s ->
                        s.contains("MultiNST PSK")).toList();
                List<String> sp = list.stream().filter(s ->
                    s.contains("MultiNST PSK (Server)")).toList();
                List<String> serverPSK = new ArrayList<>(sp.stream().toList());
                List<String> clientPSK = list.stream().filter(s ->
                    s.contains("MultiNST PSK (Client)")).toList();
                System.out.println("found list: " + list.size());
                System.out.println("found server: " + serverPSK.size());
                serverPSK.stream().forEach(s -> System.out.println("\t" + s));
                System.out.println("found client: " + clientPSK.size());
                clientPSK.stream().forEach(s -> System.out.println("\t" + s));

                // Must search all results as order is not guaranteed.
                clientPSK.stream().forEach(cli -> {
                    for (int i = 0; i < serverPSK.size(); i++) {
                        String svr = serverPSK.get(i);
                        if (svr.regionMatches(svr.length() - 16, cli,
                            cli.length() - 16, 16)) {
                            System.out.println("entry " + (i + 1) + " match.");
                            serverPSK.remove(i);
                            return;
                        }
                    }
                    System.out.println("client entry (" + cli.substring(0, 16) +
                        ") not found in server list");
                    pass = false;
                });
            } catch (RuntimeException e) {
                System.out.println("Error looking at PSK results.");
                throw new Exception(e);
            }

            if (TLS13) {
                if (!pass) {
                    throw new Exception("Test failed: " + params);
                }
            } else {
                if (pass) {
                    throw new Exception("Test failed: " + params);
                }
            }
            System.out.println("Test Passed");
            return;
        }

        int ticketCount = Integer.parseInt(
            System.getProperty("jdk.tls.server.newSessionTicketCount"));

        TLSBase.Server server = new TLSBase.Server();

        System.out.println("------  Start connection");
        TLSBase.Client initial = new TLSBase.Client();
        SSLSession initialSession = initial.getSession();
        System.out.println("id = " + hex.formatHex(initialSession.getId()));
        System.out.println("session = " + initialSession);

        System.out.println("------  getNewSession from original client");

        ArrayList<Thread> slist = new ArrayList<>(ticketCount);

        System.out.println("tx " + ticketCount);
        for (int i = 0; ticketCount > i; i++) {
            Thread t = new ClientThread(initial);
            t.setName("Iteration " + i);
            slist.add(t);
            t.start();
        }

        wait.countDown();
        for (Thread t : slist) {
            t.join(1000);
            System.err.println("released: " + t.getName());
        }

        System.out.println("------  Closing connections");
        initial.close();
        server.close();
        System.out.println("------  End");
        System.exit(0);
    }
}
