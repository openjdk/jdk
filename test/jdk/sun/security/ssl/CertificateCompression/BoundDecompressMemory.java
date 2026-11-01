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
import static jdk.test.lib.Asserts.assertNotNull;
import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Utils.runAndCheckException;
import static jdk.test.lib.security.SecurityUtils.runAndGetLog;

import java.util.Scanner;
import javax.net.ssl.SSLHandshakeException;

/*
 * @test
 * @bug 8372526
 * @summary Bound the memory usage when decompressing CompressedCertificate.
 * @modules java.base/sun.security.x509
 *          java.base/sun.security.util
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm BoundDecompressMemory
 */

public class BoundDecompressMemory extends CompressedCertMsgCache {

    private static final int MAX_HANDSHAKE_MESSAGE_SIZE = 4096;

    public static void main(String[] args) throws Exception {
        System.setProperty("jdk.tls.maxHandshakeMessageSize",
                Integer.toString(MAX_HANDSHAKE_MESSAGE_SIZE));

        String log = runAndGetLog(() -> {
            try {
                // Use highly compressible subject name for server's certificate.
                serverCertSubjectName = "O=Some-Org"
                        + "A".repeat(MAX_HANDSHAKE_MESSAGE_SIZE)
                        + ", L=Some-City, ST=Some-State, C=US";

                setupCertificates();
                serverSslContext = getSSLContext(trustedCert, serverCert,
                        serverKeys.getPrivate(), "TLSv1.3");
                clientSslContext = getSSLContext(trustedCert, clientCert,
                        clientKeys.getPrivate(), "TLSv1.3");

                runAndCheckException(() -> new BoundDecompressMemory().run(),
                        serverEx -> {
                            Throwable clientEx = serverEx.getSuppressed()[0];
                            assertTrue(
                                    clientEx instanceof SSLHandshakeException);
                            assertEquals("(bad_certificate) Improper "
                                            + "certificate compression",
                                    clientEx.getMessage());
                        }
                );
            } catch (Exception _) {
            }
        });

        // Check for the specific decompression error message.
        assertNotNull(new Scanner(log).findWithinHorizon("The size of the "
                + "uncompressed certificate message "
                + "exceeds maximum allowed size of "
                + MAX_HANDSHAKE_MESSAGE_SIZE
                + " bytes; compressed size: \\d+", 0));
    }
}
