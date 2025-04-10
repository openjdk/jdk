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
 * @bug 8340321
 * @summary Disable SHA-1 in TLS/DTLS 1.2 signatures.
 *          This test only covers TLS 1.2.
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm DisableSHA1inHandshakeSignatureTLS12
 */

import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertTrue;

import java.util.List;

public class DisableSHA1inHandshakeSignatureTLS12 extends
        AbstractCheckSignatureSchemes {

    protected DisableSHA1inHandshakeSignatureTLS12() throws Exception {
        super();
    }

    public static void main(String[] args) throws Exception {
        new DisableSHA1inHandshakeSignatureTLS12().run();
    }

    @Override
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

    // Returns SHA-1 signature schemes supported for TLSv1.2 handshake
    protected List<String> getDisabledSignatureSchemes() {
        return List.of(
                "ecdsa_sha1",
                "rsa_pkcs1_sha1",
                "dsa_sha1"
        );
    }

    protected void checkClientHello() throws Exception {
        // Get signature_algorithms extension signature schemes.
        List<String> sigAlgsSS = getSigSchemesCliHello(
                extractHandshakeMsg(cTOs, TLS_HS_CLI_HELLO),
                SIG_ALGS_EXT);

        // Should not be present in signature_algorithms extension.
        getDisabledSignatureSchemes().forEach(ss ->
                assertFalse(sigAlgsSS.contains(ss),
                        "Signature Scheme " + ss
                        + " present in ClientHello's signature_algorithms extension"));

        // Get signature_algorithms_cert extension signature schemes.
        List<String> sigAlgsCertSS = getSigSchemesCliHello(
                extractHandshakeMsg(cTOs, TLS_HS_CLI_HELLO),
                SIG_ALGS_CERT_EXT);

        // Should be present in signature_algorithms_cert extension.
        getDisabledSignatureSchemes().forEach(ss ->
                assertTrue(sigAlgsCertSS.contains(ss),
                        "Signature Scheme " + ss
                        + " isn't present in ClientHello's"
                        + " signature_algorithms extension"));
    }

    protected void checkCertificateRequest() throws Exception {
        // Get CertificateRequest message signature schemes.
        List<String> sigAlgsCertSS = getSigSchemesCertReq(
                extractHandshakeMsg(sTOc, TLS_HS_CERT_REQ));

        // Should not be present in CertificateRequest message.
        getDisabledSignatureSchemes().forEach(ss ->
                assertFalse(sigAlgsCertSS.contains(ss),
                        "Signature Scheme " + ss
                        + " present in CertificateRequest"));
    }
}
