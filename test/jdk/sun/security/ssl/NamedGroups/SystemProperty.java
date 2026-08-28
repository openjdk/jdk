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
 * @summary check system property on group names
 * @run main/othervm -Djdk.tls.namedGroups=secp256r1,secp384r1 SystemProperty secp256r1
 * @run main/othervm -Djdk.tls.namedGroups=secp256r1,*secp384r1 SystemProperty secp384r1
 * @run main/othervm -Djdk.tls.namedGroups=secp256r1,*unknown SystemProperty secp256r1
 */

import jdk.test.lib.Asserts;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SystemProperty {

    private static final boolean DEBUG = System.getProperty("debug") != null;

    // Observed result from debug output
    static class Result {
        public List<String> cshares = new ArrayList<>();
        public String err;

        enum Phase {
            BEFORE_CH1,
            IN_CH1,
            WAIT_SERVER,
        }

        private Phase phase = Phase.BEFORE_CH1;

        void consume(String line) {

            if (DEBUG) {
                System.out.println(line);
            }

            if (line.contains(".java")) { // titles
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
                    readClientHelloLine(line, cshares);
                    if (has(line, "ClientHello")) {
                        phase = Phase.WAIT_SERVER;
                    }
                }
            }
        }

        private static void readClientHelloLine(String line, List<String> shares) {
            if (has(line, "named group")) {
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

    public static void main(String[] args) throws Exception {
        System.setProperty("javax.net.debug", "ssl,handshake");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(baos));
        Exception exception = null;
        try {
            SSLContext sc = SSLContext.getDefault();
            SSLEngine client = sc.createSSLEngine("someone", 8080);
            client.setUseClientMode(true);
            client.beginHandshake();

            ByteBuffer clientToServer =
                    ByteBuffer.allocate(client.getSession().getPacketBufferSize());

            client.wrap(ByteBuffer.allocate(0), clientToServer);
            clientToServer.flip();
        } catch (Exception e) {
            exception = e; // handshake errors
        } finally {
            System.clearProperty("javax.net.debug");
            System.setErr(oldErr);
        }
        Result result = new Result();
        new String(baos.toByteArray(), StandardCharsets.UTF_8).lines()
                .forEach(result::consume);
        if (result.err == null && exception != null) {
            result.err = exception.getMessage(); // parsing errors
        }
        Asserts.assertEquals(List.of(args[0]), result.cshares);
    }
}
