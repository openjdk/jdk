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
import static jdk.test.lib.security.SecurityUtils.countSubstringOccurrences;
import static jdk.test.lib.security.SecurityUtils.runAndGetLog;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;

/*
 * @test
 * @bug 8372526
 * @summary Add support for ZLIB TLS Certificate Compression
 * @library /javax/net/ssl/templates
 *          /test/lib
 *
 * @run main/othervm CompressedCertMsg
 * @run main/othervm -Djdk.tls.client.disableExtensions=compress_certificate CompressedCertMsg
 * @run main/othervm -Djdk.tls.server.disableExtensions=compress_certificate CompressedCertMsg
 * @run main/othervm -Djdk.tls.client.disableExtensions=compress_certificate
 *                   -Djdk.tls.server.disableExtensions=compress_certificate
 *                   CompressedCertMsg
 */

public class CompressedCertMsg extends SSLSocketTemplate {

    private static final String PRODUCED_COMP_CERT_MSG =
            """
            Produced CompressedCertificate handshake message (
            "CompressedCertificate": {
              "algorithm": "ZLIB",
            """;

    private static final String CONSUMING_COMP_CERT_MSG =
            """
            Consuming CompressedCertificate handshake message (
            "CompressedCertificate": {
              "algorithm": "ZLIB",
            """;

    private static final String IGNORE_EXT_MSG =
            """
            Ignore unknown or unsupported extension (
            "compress_certificate (27)": {
            """;

    private static final String CH_EXT =
            """
                },
                "compress_certificate (27)": {
                  "compression algorithms": [ZLIB]
                },
            """;

    private static final String CR_EXT =
            """
            "CertificateRequest": {
              "certificate_request_context": "",
              "extensions": [
                "compress_certificate (27)": {
                  "compression algorithms": [ZLIB]
            """;

    // Server sends CertificateRequest and gets a certificate
    // from the client as well as the client from the server.
    @Override
    protected void configureServerSocket(SSLServerSocket sslServerSocket) {
        SSLParameters sslParameters = sslServerSocket.getSSLParameters();
        sslParameters.setNeedClientAuth(true);
        sslServerSocket.setSSLParameters(sslParameters);
    }

    public static void main(String[] args) throws Exception {
        boolean clientSideEnabled = !System.getProperty(
                        "jdk.tls.client.disableExtensions", "")
                .contains("compress_certificate");
        boolean serverSideEnabled = !System.getProperty(
                        "jdk.tls.server.disableExtensions", "")
                .contains("compress_certificate");

        // Complete 1 handshake.
        String log = runAndGetLog(() -> {
            try {
                new CompressedCertMsg().run();
            } catch (Exception _) {
            }
        });

        // To make the test pass on Windows.
        log = log.replace("\r\n", "\n");

        if (clientSideEnabled && serverSideEnabled) {
            // Make sure CompressedCertificate message is produced and consumed
            // twice - by the server and by the client.
            assertEquals(2, countSubstringOccurrences(log,
                    PRODUCED_COMP_CERT_MSG));
            assertEquals(2, countSubstringOccurrences(log,
                    CONSUMING_COMP_CERT_MSG));
            // Extensions are produced and consumed, so they appear in the
            // log twice.
            assertEquals(2, countSubstringOccurrences(log, CH_EXT));
            assertEquals(2, countSubstringOccurrences(log, CR_EXT));
            assertEquals(0, countSubstringOccurrences(log, IGNORE_EXT_MSG));
        } else if (clientSideEnabled) {
            assertEquals(0, countSubstringOccurrences(log,
                    PRODUCED_COMP_CERT_MSG));
            assertEquals(0, countSubstringOccurrences(log,
                    CONSUMING_COMP_CERT_MSG));
            assertEquals(2, countSubstringOccurrences(log, CH_EXT));
            assertEquals(0, countSubstringOccurrences(log, CR_EXT));
            assertEquals(1, countSubstringOccurrences(log, IGNORE_EXT_MSG));
        } else if (serverSideEnabled) {
            assertEquals(0, countSubstringOccurrences(log,
                    PRODUCED_COMP_CERT_MSG));
            assertEquals(0, countSubstringOccurrences(log,
                    CONSUMING_COMP_CERT_MSG));
            assertEquals(0, countSubstringOccurrences(log, CH_EXT));
            assertEquals(2, countSubstringOccurrences(log, CR_EXT));
            assertEquals(1, countSubstringOccurrences(log, IGNORE_EXT_MSG));
        } else {
            assertEquals(0, countSubstringOccurrences(log,
                    PRODUCED_COMP_CERT_MSG));
            assertEquals(0, countSubstringOccurrences(log,
                    CONSUMING_COMP_CERT_MSG));
            assertEquals(0, countSubstringOccurrences(log, CH_EXT));
            assertEquals(0, countSubstringOccurrences(log, CR_EXT));
            assertEquals(0, countSubstringOccurrences(log, IGNORE_EXT_MSG));
        }
    }
}
