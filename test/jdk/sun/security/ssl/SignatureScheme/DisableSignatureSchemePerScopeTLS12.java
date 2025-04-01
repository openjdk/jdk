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
 * @summary Add mechanism to disable signature schemes based on their TLS scope.
 *          This test only covers TLS 1.2.
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm DisableSignatureSchemePerScopeTLS12
 */

import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertTrue;

import java.security.Security;
import java.util.List;

public class DisableSignatureSchemePerScopeTLS12 extends
        AbstractCheckSignatureSchemes {

    // Disabled for Handshake scope.
    protected static final String HANDSHAKE_DISABLED_SIG = "rsa_pss_rsae_sha384";

    // Disabled for Certificate scope.
    protected static final String CERTIFICATE_DISABLED_SIG = "ecdsa_secp384r1_sha384";

    // jdk.tls.disabledAlgorithms value
    // We differ from "HandshakeSignature" and "CertificateSignature" specified
    // in java.security to check case-insensitive matching.
    protected static final String DISABLED_CONSTRAINTS =
            HANDSHAKE_DISABLED_SIG + " usage HandShakesignature, "
            + CERTIFICATE_DISABLED_SIG + " usage certificateSignature";

    protected DisableSignatureSchemePerScopeTLS12() throws Exception {
        super();
    }

    public static void main(String[] args) throws Exception {
        Security.setProperty(
                "jdk.tls.disabledAlgorithms", DISABLED_CONSTRAINTS);
        new DisableSignatureSchemePerScopeTLS12().run();
    }

    protected String getProtocol() {
        return "TLSv1.2";
    }

    // Run things in TLS handshake order.
    protected void run() throws Exception {

        // Produce client_hello
        clientEngine.wrap(clientOut, cTOs);
        cTOs.flip();

        checkClientHello();

        // Consume client_hello.
        serverEngine.unwrap(cTOs, serverIn);
        runDelegatedTasks(serverEngine);

        // Produce server_hello.
        serverEngine.wrap(serverOut, sTOc);
        sTOc.flip();

        checkCertificateRequest();
    }

    protected void checkClientHello() throws Exception {
        // --- Check signature_algorithms extension ---

        // Get signature_algorithms extension signature schemes.
        List<String> sigAlgsSS = getSigSchemesCliHello(
                extractHandshakeMsg(cTOs, TLS_HS_CLI_HELLO),
                SIG_ALGS_EXT);

        // signature_algorithms extension MUST NOT contain disabled
        // handshake signature scheme.
        assertFalse(sigAlgsSS.contains(HANDSHAKE_DISABLED_SIG),
                "Signature Scheme " + HANDSHAKE_DISABLED_SIG
                + " present in ClientHello's signature_algorithms extension");

        // signature_algorithms extension MUST contain disabled
        // certificate signature scheme.
        assertTrue(sigAlgsSS.contains(CERTIFICATE_DISABLED_SIG),
                "Signature Scheme " + CERTIFICATE_DISABLED_SIG
                + " isn't present in ClientHello's"
                + " signature_algorithms extension");

        // --- Check signature_algorithms_cert extension ---

        // Get signature_algorithms_cert extension signature schemes.
        List<String> sigAlgsCertSS = getSigSchemesCliHello(
                extractHandshakeMsg(cTOs, TLS_HS_CLI_HELLO),
                SIG_ALGS_CERT_EXT);

        // signature_algorithms_cert extension MUST contain disabled
        // handshake signature scheme.
        assertTrue(sigAlgsCertSS.contains(HANDSHAKE_DISABLED_SIG),
                "Signature Scheme " + HANDSHAKE_DISABLED_SIG
                + " isn't present in ClientHello's"
                + " signature_algorithms extension");

        // signature_algorithms_cert extension MUST NOT contain disabled
        // certificate signature scheme.
        assertFalse(sigAlgsCertSS.contains(CERTIFICATE_DISABLED_SIG),
                "Signature Scheme " + CERTIFICATE_DISABLED_SIG
                + " present in ClientHello's signature_algorithms extension");
    }

    protected void checkCertificateRequest() throws Exception {
        // Get CertificateRequest message signature schemes.
        List<String> sigAlgsCertSS = getSigSchemesCertReq(
                extractHandshakeMsg(sTOc, TLS_HS_CERT_REQ));

        // TLSv1.2 CertificateRequest message MUST NOT contain both:
        // disabled handshake signature scheme and disabled
        // certificate signature scheme

        assertFalse(sigAlgsCertSS.contains(HANDSHAKE_DISABLED_SIG),
                "Signature Scheme " + HANDSHAKE_DISABLED_SIG
                + " present in CertificateRequest");

        assertFalse(sigAlgsCertSS.contains(CERTIFICATE_DISABLED_SIG),
                "Signature Scheme " + CERTIFICATE_DISABLED_SIG
                + " present in CertificateRequest");
    }
}
