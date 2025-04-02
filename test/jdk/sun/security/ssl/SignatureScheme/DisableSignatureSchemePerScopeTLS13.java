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
 *          This test only covers TLS 1.3.
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm DisableSignatureSchemePerScopeTLS13
 */


import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertTrue;

import java.security.Security;
import java.util.List;

public class DisableSignatureSchemePerScopeTLS13
        extends DisableSignatureSchemePerScopeTLS12 {

    // Signature schemes not supported in TLSv1.3 only for the handshake.
    // This is regardless of jdk.tls.disabledAlgorithms configuration.
    List<String> NOT_SUPPORTED_FOR_HANDSHAKE = List.of(
            "rsa_pkcs1_sha1",
            "rsa_pkcs1_sha256",
            "rsa_pkcs1_sha384",
            "rsa_pkcs1_sha512"
    );

    protected DisableSignatureSchemePerScopeTLS13() throws Exception {
        super();
    }

    public static void main(String[] args) throws Exception {
        Security.setProperty(
                "jdk.tls.disabledAlgorithms", DISABLED_CONSTRAINTS);
        new DisableSignatureSchemePerScopeTLS13().run();
    }

    @Override
    protected String getProtocol() {
        return "TLSv1.3";
    }

    @Override
    protected void checkClientHello() throws Exception {
        super.checkClientHello();

        // Get signature_algorithms extension signature schemes.
        List<String> sigAlgsSS = getSigSchemesCliHello(
                extractHandshakeMsg(cTOs, TLS_HS_CLI_HELLO),
                SIG_ALGS_EXT);

        // Should not be present in signature_algorithms extension.
        NOT_SUPPORTED_FOR_HANDSHAKE.forEach(ss ->
                assertFalse(sigAlgsSS.contains(ss),
                        "Signature Scheme " + ss
                        + " present in ClientHello's signature_algorithms extension"));

        // Get signature_algorithms_cert extension signature schemes.
        List<String> sigAlgsCertSS = getSigSchemesCliHello(
                extractHandshakeMsg(cTOs, TLS_HS_CLI_HELLO),
                SIG_ALGS_CERT_EXT);

        // Should be present in signature_algorithms_cert extension.
        NOT_SUPPORTED_FOR_HANDSHAKE.forEach(ss ->
                assertTrue(sigAlgsCertSS.contains(ss),
                        "Signature Scheme " + ss
                        + " isn't present in ClientHello's"
                        + " signature_algorithms extension"));
    }

    // TLSv1.3 sends CertificateRequest signature schemes in
    // signature_algorithms and signature_algorithms_cert extensions. Same as
    // ClientHello, but they are encrypted. So we skip CertificateRequest
    // signature schemes verification for TLSv1.3.
    @Override
    protected void checkCertificateRequest() {
    }
}
