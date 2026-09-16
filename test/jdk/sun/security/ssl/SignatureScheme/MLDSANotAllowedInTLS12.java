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
 * @bug 8381641
 * @summary Test ML-DSA signature scheme advertisement in TLS 1.2
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm MLDSANotAllowedInTLS12
 */

import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertTrue;

import java.util.List;

public class MLDSANotAllowedInTLS12 extends AbstractCheckSignatureSchemes {

    private static final List<String> MLDSA_SCHEMES = List.of(
            "mldsa65", "mldsa87", "mldsa44");

    protected MLDSANotAllowedInTLS12() throws Exception {
        super();
    }

    public static void main(String[] args) throws Exception {
        new MLDSANotAllowedInTLS12().run();
    }

    @Override
    protected String getProtocol() {
        return "TLSv1.2";
    }

    // Run things in TLS handshake order
    protected void run() throws Exception {
        // Produce ClientHello
        clientEngine.wrap(clientOut, cTOs);
        cTOs.flip();

        checkClientHello();

        // Consume ClientHello
        serverEngine.unwrap(cTOs, serverIn);
        runDelegatedTasks(serverEngine);

        // Produce ServerHello and CertificateRequest
        serverEngine.wrap(serverOut, sTOc);
        sTOc.flip();

        checkCertificateRequest();
    }

    protected void checkClientHello() throws Exception {
        // Get signature_algorithms extension signature schemes
        List<String> sigAlgsSS = getSigSchemesCliHello(
                extractHandshakeMsg(cTOs, TLS_HS_CLI_HELLO),
                SIG_ALGS_EXT);

        assertNoMldsa(sigAlgsSS,
                "ClientHello signature_algorithms extension");

        // Get signature_algorithms_cert extension signature schemes
        List<String> sigAlgsCertSS = getSigSchemesCliHello(
                extractHandshakeMsg(cTOs, TLS_HS_CLI_HELLO),
                SIG_ALGS_CERT_EXT);

        assertNoMldsa(sigAlgsCertSS,
                "ClientHello signature_algorithms_cert extension");
    }

    protected void checkCertificateRequest() throws Exception {
        // Get CertificateRequest message signature schemes
        List<String> certReqSS = getSigSchemesCertReq(
                extractHandshakeMsg(sTOc, TLS_HS_CERT_REQ));

        assertNoMldsa(certReqSS, "TLS 1.2 CertificateRequest");
    }

    protected static void assertNoMldsa(List<String> signatureSchemes,
            String messageSource) {
        MLDSA_SCHEMES.forEach(signatureScheme ->
                assertFalse(signatureSchemes.contains(signatureScheme),
                        "Signature scheme " + signatureScheme
                        + " present in " + messageSource));
    }

    protected static void assertMldsa(List<String> signatureSchemes,
            String messageSource) {
        assertTrue(signatureSchemes.size() >= MLDSA_SCHEMES.size(),
                "Not enough signature schemes in " + messageSource);
        assertEquals(signatureSchemes.subList(0, MLDSA_SCHEMES.size()),
                MLDSA_SCHEMES, "Unexpected ML-DSA signature scheme order in "
                + messageSource);
    }
}
