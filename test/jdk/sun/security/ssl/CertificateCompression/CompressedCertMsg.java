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

import static jdk.test.lib.Asserts.assertEquals;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;

/*
 * @test
 * @bug 8372526
 * @summary Add support for ZLIB TLS Certificate Compression
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm CompressedCertMsg
 */

public class CompressedCertMsg extends CompressedCertMsgBase {

    // Server sends CertificateRequest and gets a certificate
    // from the client as well as the client from the server.
    @Override
    protected void configureServerSocket(SSLServerSocket sslServerSocket) {
        SSLParameters sslParameters = sslServerSocket.getSSLParameters();
        sslParameters.setNeedClientAuth(true);
        sslServerSocket.setSSLParameters(sslParameters);
    }

    public static void main(String[] args) throws Exception {

        // Complete 1 handshake.
        String log = runAndGetLog(() -> {
            try {
                new CompressedCertMsg().run();
            } catch (Exception _) {
            }
        });

        // Make sure CompressedCertificate message is produced twice - by the
        // server and by the client.
        assertEquals(2, countSubstringOccurrences(log,
                """
                Produced CompressedCertificate handshake message (
                "CompressedCertificate": {
                  "algorithm": "zlib",
                """));

        // Make sure CompressedCertificate message is consumed twice - by the
        // server and by the client.
        assertEquals(2, countSubstringOccurrences(log,
                """
                Consuming CompressedCertificate handshake message (
                "CompressedCertificate": {
                  "algorithm": "zlib",
                """));
    }
}
