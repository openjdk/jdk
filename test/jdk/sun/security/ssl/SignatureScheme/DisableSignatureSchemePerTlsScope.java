/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8349583
 * @summary Add mechanism to disable signature schemes based on their TLS scope
 * @library /javax/net/ssl/templates
 * @run main/othervm DisableSignatureSchemePerTlsScope TLSv1.2
 * @run main/othervm DisableSignatureSchemePerTlsScope TLSv1.3
 */

import java.security.Security;
import java.util.List;

public class DisableSignatureSchemePerTlsScope extends
        AbstractCheckSignatureSchemes {

    // Disabled for Handshake scope.
    private static final String HANDSHAKE_DISABLED_SIG = "rsa_pkcs1_sha1";

    // Disabled for Certificate scope.
    private static final String CERTIFICATE_DISABLED_SIG = "rsa_pkcs1_sha384";

    // Protocol version to run test for.
    private static String protocol;

    protected DisableSignatureSchemePerTlsScope() throws Exception {
        super();
    }

    public static void main(String[] args) throws Exception {
        Security.setProperty("jdk.tls.disabledAlgorithms",
                HANDSHAKE_DISABLED_SIG + " usage handshake, "
                        + CERTIFICATE_DISABLED_SIG + " usage certificate");
        protocol = args[0];
        new DisableSignatureSchemePerTlsScope().run();
    }

    @Override
    protected String getProtocol() {
        return protocol;
    }

    private void run() throws Exception {

        // Check the ClientHello message.

        clientEngine.wrap(clientOut, cTOs);
        cTOs.flip();

        // Get signature_algorithms extension signature schemes.
        List<String> sigAlgsSS = getSigSchemesCliHello(
                extractHandshakeMsg(cTOs, TLS_HS_CLI_HELLO),
                SIG_ALGS_EXT);

        // signature_algorithms extension MUST NOT contain disabled
        // handshake signature scheme.
        if (sigAlgsSS.contains(HANDSHAKE_DISABLED_SIG)) {
            throw new RuntimeException("Signature Scheme "
                    + HANDSHAKE_DISABLED_SIG
                    + " present in ClientHello's signature_algorithms"
                    + " extension");
        }

        // signature_algorithms extension MUST contain disabled
        // certificate signature scheme.
        if (!sigAlgsSS.contains(CERTIFICATE_DISABLED_SIG)) {
            throw new RuntimeException("Signature Scheme "
                    + CERTIFICATE_DISABLED_SIG
                    + " isn't present in ClientHello's"
                    + " signature_algorithms extension");
        }

        // Get signature_algorithms_cert extension signature schemes.
        List<String> sigAlgsCertSS = getSigSchemesCliHello(
                extractHandshakeMsg(cTOs, TLS_HS_CLI_HELLO),
                SIG_ALGS_CERT_EXT);

        // signature_algorithms_cert extension MUST contain disabled
        // handshake signature scheme.
        if (!sigAlgsCertSS.contains(HANDSHAKE_DISABLED_SIG)) {
            throw new RuntimeException("Signature Scheme "
                    + HANDSHAKE_DISABLED_SIG
                    + " isn't present in ClientHello's"
                    + " signature_algorithms extension");
        }

        // signature_algorithms_cert extension MUST NOT contain disabled
        // certificate signature scheme.
        if (sigAlgsCertSS.contains(CERTIFICATE_DISABLED_SIG)) {
            throw new RuntimeException("Signature Scheme "
                    + CERTIFICATE_DISABLED_SIG
                    + " present in ClientHello's"
                    + " signature_algorithms extension");
        }

        // Check the CertificateRequest message.

        // TLSv1.3 sends signature schemes in signature_algorithms and
        // signature_algorithms_cert extensions. Same as ClientHello, but they
        // are encrypted. We skip CertificateRequest verification for TLSv1.3.
        if (!List.of(serverEngine.getEnabledProtocols()).contains("TLSv1.3")) {

            // Consume client_hello.
            serverEngine.unwrap(cTOs, serverIn);
            runDelegatedTasks(serverEngine);

            // Produce server_hello.
            serverEngine.wrap(serverOut, sTOc);
            sTOc.flip();

            // Get CertificateRequest message signature schemes.
            sigAlgsCertSS = getSigSchemesCertReq(sTOc);

            // CertificateRequest message MUST contain disabled handshake
            // signature scheme (same as signature_algorithms_cert extension).
            if (!sigAlgsCertSS.contains(HANDSHAKE_DISABLED_SIG)) {
                throw new RuntimeException("Signature Scheme "
                        + HANDSHAKE_DISABLED_SIG
                        + " isn't present in CertificateRequest");
            }

            // CertificateRequest message MUST NOT contain disabled certificate
            // signature scheme (same as signature_algorithms_cert extension).
            if (sigAlgsCertSS.contains(CERTIFICATE_DISABLED_SIG)) {
                throw new RuntimeException("Signature Scheme "
                        + CERTIFICATE_DISABLED_SIG
                        + " present in CertificateRequest");
            }
        }
    }
}
