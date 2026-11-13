/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8388484
 * @library /test/lib
 * @summary Test all combinations of named group settings
 * @run main/othervm NamedGroupSettings
 */

import jdk.test.lib.Asserts;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLParameters;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

public class NamedGroupSettings {
    private static final boolean DEBUG = System.getProperty("debug") != null;

    // TestCase including client/server settings and expected message fields.
    // When a field is missing it will be null. To show a case on one line,
    // group names are abbreviated:
    // A = x25519
    // B = x448
    // C = secp256r1
    // D = secp384r1
    // X/Y/Z = unknown group names
    record TestCase(
            String clientSetting,
            String serverSetting,
            String chGroups, // expected supported_group in CH1
            String chKeyShares, // expected key_share in CH1
            String serverResponse, // expected server response to CH1
            String serverChoice, // expected server selected group
            String ch2Groups, // expected supported_group in CH2
            String ch2KeyShares, // expected key_share in CH2
            String err) {

        static TestCase of(String clientSetting, String serverSetting,
                String chGroups, String chKeyShares,
                String serverResponse, String serverChoice,
                String ch2Groups, String ch2KeyShares) {
            return of(clientSetting, serverSetting,
                    chGroups, chKeyShares,
                    serverResponse, serverChoice,
                    ch2Groups, ch2KeyShares, null);
        }

        static TestCase of(String clientSetting, String serverSetting,
                String chGroups, String chKeyShares,
                String serverResponse, String serverChoice,
                String ch2Groups, String ch2KeyShares, String comment) {
            return new TestCase(clientSetting, serverSetting,
                    chGroups, chKeyShares,
                    serverResponse, serverChoice,
                    ch2Groups, ch2KeyShares, comment);
        }

        static String expand(String s) {
            return s.replace("A", "x25519")
                    .replace("B", "x448")
                    .replace("C", "secp256r1")
                    .replace("D", "secp384r1");
        }

        static String shrink(String s) {
            return s.replace("x25519", "A")
                    .replace("x448", "B")
                    .replace("secp256r1", "C")
                    .replace("secp384r1", "D");
        }

        public void check(Result result) {
            Asserts.assertEquals(chGroups, enc(result.chGroups));
            Asserts.assertEquals(chKeyShares, enc(result.chKeyShares));
            Asserts.assertEquals(serverResponse, result.serverResponse);
            Asserts.assertEquals(serverChoice, enc(result.serverChoice));
            Asserts.assertEquals(ch2Groups, enc(result.ch2Groups));
            Asserts.assertEquals(ch2KeyShares, enc(result.ch2KeyShares));
            if (err != null) {
                // only compare failures, result.err could be something else
                Asserts.assertEquals(err, result.err);
            }
        }
    }

    // Expected behavior:
    // - If any "*" exists, CH1 key_share contains all starred enabled groups.
    // - If no "*" exists, CH1 key_share follows old JSSE automatic selection.
    // - Server chooses first mutually supported group in CH1 supported_groups.
    // - If key_share for that group exists, SH; otherwise HRR.

    static final List<TestCase> TEST_CASES = List.of(
            // Negotiable
            TestCase.of("*A,*B,*C", "A,B,C", "A,B,C", "A,B,C", "SH", "A", null, null), // first mutual A has share
            TestCase.of("*A,*B,*C", "B,A,C", "A,B,C", "A,B,C", "SH", "A", null, null), // server order ignored; client first mutual A has share
            TestCase.of("*A,*B,*C", "B,C", "A,B,C", "A,B,C", "SH", "B", null, null), // A unsupported by server; first mutual B has share
            TestCase.of("*A,*B,*C", "C", "A,B,C", "A,B,C", "SH", "C", null, null), // only C mutual and has share
            TestCase.of("A,*B,*C", "A,B,C", "A,B,C", "B,C", "HRR", "A", "A,B,C", "A"), // first mutual A has no share
            TestCase.of("A,*B,*C", "B,A,C", "A,B,C", "B,C", "HRR", "A", "A,B,C", "A"), // server order ignored; first mutual A has no share
            TestCase.of("A,*B,*C", "B,C", "A,B,C", "B,C", "SH", "B", null, null), // first mutual B has share
            TestCase.of("A,*B,*C", "C,B", "A,B,C", "B,C", "SH", "B", null, null), // server order ignored; first mutual B has share
            TestCase.of("A,*B,*C", "C", "A,B,C", "B,C", "SH", "C", null, null), // first mutual C has share
            TestCase.of("A,*B,*C", "A", "A,B,C", "B,C", "HRR", "A", "A,B,C", "A"), // only mutual A has no share
            TestCase.of("A,B,*C", "A,B,C", "A,B,C", "C", "HRR", "A", "A,B,C", "A"), // first mutual A has no share
            TestCase.of("A,B,*C", "B,A,C", "A,B,C", "C", "HRR", "A", "A,B,C", "A"), // server order ignored; first mutual A has no share
            TestCase.of("A,B,*C", "B,C", "A,B,C", "C", "HRR", "B", "A,B,C", "B"), // first mutual B has no share
            TestCase.of("A,B,*C", "C,B", "A,B,C", "C", "HRR", "B", "A,B,C", "B"), // server order ignored; first mutual B has no share
            TestCase.of("A,B,*C", "C", "A,B,C", "C", "SH", "C", null, null), // first mutual C has share
            TestCase.of("B,A,*C", "A,B,C", "B,A,C", "C", "HRR", "B", "B,A,C", "B"), // client order makes B first mutual
            TestCase.of("*B,A,*C", "A,B,C", "B,A,C", "B,C", "SH", "B", null, null), // client order makes B first mutual and has share
            TestCase.of("*C,*B,A", "A,B,C", "C,B,A", "C,B", "SH", "C", null, null), // client order makes C first mutual and has share
            TestCase.of("A,B,C", "A,B,C", "A,B,C", "A,C", "SH", "A", null, null), // no star: old default shares include A
            TestCase.of("A,B,C", "B,C", "A,B,C", "A,C", "HRR", "B", "A,B,C", "B"), // no star: first mutual B not in default shares
            TestCase.of("A,B,C", "C", "A,B,C", "A,C", "SH", "C", null, null), // no star: first mutual C in default shares
            TestCase.of("B,A,C", "A,B,C", "B,A,C", "B,C", "SH", "B", null, null), // no star: client first mutual B has default share
            TestCase.of("C,A,B", "A,B,C", "C,A,B", "C,A", "SH", "C", null, null), // no star: client first mutual C has default share
            TestCase.of("X,A,*B,*C", "A,B,C", "A,B,C", "B,C", "HRR", "A", "A,B,C", "A"), // unknown client group X ignored
            TestCase.of("A,*X,*B,*C", "A,B,C", "A,B,C", "B,C", "HRR", "A", "A,B,C", "A"), // unknown starred client group X ignored
            TestCase.of("A,*B,*C", "X,B,C", "A,B,C", "B,C", "SH", "B", null, null), // unknown server group X ignored; first mutual B
            TestCase.of("A,*B,*C", "X,Y,C", "A,B,C", "B,C", "SH", "C", null, null), // unknown server groups ignored; first mutual C
            TestCase.of("X,A,*Y,*B,*C", "Y,X,B,C", "A,B,C", "B,C", "SH", "B", null, null), // unknown groups ignored on both sides
            TestCase.of("X,A,Y,B,Z,C", "A,B,C", "A,B,C", "A,C", "SH", "A", null, null), // unknown unstarred groups ignored; no-star default shares
            TestCase.of("X,A,Y,B,Z,C", "B,C", "A,B,C", "A,C", "HRR", "B", "A,B,C", "B"), // unknown ignored; first mutual B lacks default share
            TestCase.of("X,*A,Y,*B,Z,*C", "A,B,C", "A,B,C", "A,B,C", "SH", "A", null, null), // unknown ignored; all known starred shares sent
            TestCase.of("A,B,*X", "A,B,C", "A,B", "A", "SH", "A", null, null), // unknown starred ignored; default keyshare!

            // No group negotiated
            TestCase.of("A,*B,*C", "D", "A,B,C", "B,C", null, null, null, null, "(handshake_failure) No common named group"),
            TestCase.of("A,*B,*C", "X,Y,Z", "A,B,C", "B,C", null, null, null, null, "(handshake_failure) No common named group"),
            TestCase.of("A,B,C", "D", "A,B,C", "A,C", null, null, null, null, "(handshake_failure) No common named group"),
            TestCase.of("A,B,*C", "D", "A,B,C", "C", null, null, null, null, "(handshake_failure) No common named group"),

            // Syntax error
            TestCase.of("**A", "A", null, null, null, null, null, null, "Multiple asterisks"),
            TestCase.of("*A,*A", "A", null, null, null, null, null, null, "Duplicate element of namedGroups: x25519"),
            TestCase.of("A,*A", "A", null, null, null, null, null, null, "Duplicate element of namedGroups: x25519"),
            TestCase.of("A,,B", "A", null, null, null, null, null, null, "An element of namedGroups is blank"),
            TestCase.of("*", "A", null, null, null, null, null, null, "An element of namedGroups is blank"),
            TestCase.of("A,*", "A", null, null, null, null, null, null, "An element of namedGroups is blank"),
            TestCase.of("X,Y,Z", "A,B,C", null, null, null, null, null, null,
                    "(missing_extension) No supported_groups or signature_algorithms extension when pre_shared_key extension is not present"),
            TestCase.of("*X,*Y", "A,B,C", null, null, null, null, null, null,
                    "(missing_extension) No supported_groups or signature_algorithms extension when pre_shared_key extension is not present")
    );

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            // Manual play
            System.out.println(test(
                    args[0].equals("null") ? null : TestCase.expand(args[0]),
                    args[1].equals("null") ? null : TestCase.expand(args[1])));
        } else {
            for (TestCase c : TEST_CASES) {
                System.out.println(c.clientSetting + " - " + c.serverSetting);
                Result result = test(TestCase.expand(c.clientSetting), TestCase.expand(c.serverSetting));
                c.check(result);
            }
        }
    }

    // encode one group name using TextCase.shrink
    static String enc(String s) {
        if (s == null) return null;
        return TestCase.shrink(s);
    }

    // encode a list of group names using TextCase.shrink
    static String enc(Collection<String> ss) {
        if (ss == null || ss.isEmpty()) return null;
        var sj = new StringJoiner(",");
        for (String s : ss) {
            sj.add(s);
        }
        return TestCase.shrink(sj.toString());
    }

    // Observed result from debug output
    static class Result {
        public List<String> chGroups = new ArrayList<>();
        public List<String> chKeyShares = new ArrayList<>();
        public String serverResponse;
        public String serverChoice;
        public List<String> ch2Groups = new ArrayList<>();
        public List<String> ch2KeyShares = new ArrayList<>();
        public String err;

        enum Phase {
            BEFORE_CH1,
            IN_CH1,
            WAIT_SERVER,
            IN_SH,
            IN_HRR,
            IN_CH2
        }

        private Phase phase = Phase.BEFORE_CH1;

        void consume(String line) {

            if (DEBUG) {
                System.out.println(line);
            }

            if (line.contains(".java")) {
                return;
            }

            if (line.contains("SSLHandshakeException")) {
                err = line.substring(line.indexOf(':') + 1).trim();
            }

            switch (phase) {
                case BEFORE_CH1 -> {
                    if (has(line, "ClientHello")) {
                        phase = Phase.IN_CH1;
                    }
                }
                case IN_CH1 -> {
                    readClientHelloLine(line, chGroups, chKeyShares);
                    if (has(line, "ClientHello")) {
                        phase = Phase.WAIT_SERVER;
                    }
                }
                case WAIT_SERVER -> {
                    if (has(line, "HelloRetryRequest")) {
                        serverResponse = "HRR";
                        phase = Phase.IN_HRR;
                    } else if (has(line, "ServerHello")) {
                        serverResponse = "SH";
                        phase = Phase.IN_SH;
                    }
                }
                case IN_SH -> {
                    if (has(line, "named group")) {
                        serverChoice = parseOne(line);
                    }
                }
                case IN_HRR -> {
                    if (has(line, "selected group")) {
                        serverChoice = parseOne(line);
                    } else if (has(line, "ClientHello")) {
                        phase = Phase.IN_CH2;
                    }
                }
                case IN_CH2 -> readClientHelloLine(line, ch2Groups, ch2KeyShares);
            }
        }

        private static void readClientHelloLine(String line,
                List<String> groups, List<String> shares) {
            if (has(line, "named groups")) {
                groups.addAll(parseList(line));
            } else if (has(line, "named group")) {
                shares.add(parseOne(line));
            }
        }
    }

    static boolean has(String in, String key) {
        return in.contains('"' + key + '"');
    }

    static String parseOne(String line) {
        String value = line.substring(line.indexOf(':') + 1).trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    static List<String> parseList(String in) {
        return Arrays.stream(in.split("[\\[\\]]")[1].split(",\\s*")).toList();
    }

    static Result test(String clientSetting, String serverSetting) throws Exception {
        System.setProperty("javax.net.debug", "ssl,handshake");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(baos));
        Exception exception = null;
        try {
            SSLContext sc = SSLContext.getDefault();
            SSLEngine client = sc.createSSLEngine("someone", 8080);
            client.setUseClientMode(true);
            if (clientSetting != null) {
                SSLParameters cp = client.getSSLParameters();
                cp.setNamedGroups(clientSetting.split(","));
                client.setSSLParameters(cp);
            }
            client.beginHandshake();

            SSLEngine server = sc.createSSLEngine();
            server.setUseClientMode(false);
            if (serverSetting != null) {
                SSLParameters sp = server.getSSLParameters();
                sp.setNamedGroups(serverSetting.split(","));
                server.setSSLParameters(sp);
            }
            server.beginHandshake();


            ByteBuffer clientToServer =
                    ByteBuffer.allocate(client.getSession().getPacketBufferSize());
            ByteBuffer serverToClient =
                    ByteBuffer.allocate(server.getSession().getPacketBufferSize());
            ByteBuffer app =
                    ByteBuffer.allocate(server.getSession().getApplicationBufferSize());

            client.wrap(ByteBuffer.allocate(0), clientToServer);
            clientToServer.flip();

            server.unwrap(clientToServer, app);

            while (server.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                Runnable task;
                while ((task = server.getDelegatedTask()) != null) {
                    task.run();
                }
            }

            // If SH has been sent, the next call might fail because
            // authentication has not been configured at all. Otherwise,
            // we will record HRR and CH2 messages.
            // The error is recorded but will not be used in comparison.
            server.wrap(ByteBuffer.allocate(0), serverToClient);
            serverToClient.flip();

            client.unwrap(serverToClient, app);
            while (client.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                Runnable task;
                while ((task = client.getDelegatedTask()) != null) {
                    task.run();
                }
            }
        } catch (Exception e) {
            exception = e; // handshake errors
        } finally {
            System.clearProperty("javax.net.debug");
            System.setErr(oldErr);
        }
        Result info = new Result();
        new String(baos.toByteArray(), StandardCharsets.UTF_8).lines()
                .forEach(info::consume);
        if (info.err == null && exception != null) {
            info.err = exception.getMessage(); // parsing errors
        }
        return info;
    }
}
