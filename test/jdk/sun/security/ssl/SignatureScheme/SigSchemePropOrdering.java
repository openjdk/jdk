/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 8255867
 * @summary SignatureScheme JSSE property does not preserve ordering in handshake messages
 * @library /javax/net/ssl/templates
 * @run main/othervm SigSchemePropOrdering
 */

import java.util.Arrays;
import java.util.List;

public class SigSchemePropOrdering extends AbstractCheckSignatureSchemes {

    private static final String SIG_SCHEME_STR =
            "rsa_pkcs1_sha256,rsa_pss_rsae_sha256,rsa_pss_pss_sha256," +
                    "ed448,ed25519,ecdsa_secp256r1_sha256";

    SigSchemePropOrdering() throws Exception {
        super();
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("javax.net.debug", "ssl:handshake");
        System.setProperty("jdk.tls.client.SignatureSchemes", SIG_SCHEME_STR);
        System.setProperty("jdk.tls.server.SignatureSchemes", SIG_SCHEME_STR);
        new SigSchemePropOrdering().run();
    }

    protected String getProtocol() {
        return "TLSv1.2";
    }

    private void run() throws Exception {
        // Start the handshake.  Check the ClientHello's signature_algorithms
        // extension and make sure the ordering matches what we specified
        // in the property above.
        List<String> expectedSS = Arrays.asList(SIG_SCHEME_STR.split(","));

        clientEngine.wrap(clientOut, cTOs);
        cTOs.flip();

        List<String> actualSS = getSigSchemesCliHello(
                extractHandshakeMsg(cTOs, TLS_HS_CLI_HELLO),
                SIG_ALGS_EXT);

        // Make sure the ordering is correct
        if (!expectedSS.equals(actualSS)) {
            System.out.println("FAIL: Mismatch between property ordering " +
                    "and ClientHello message");
            System.out.print("Expected SigSchemes: ");
            expectedSS.forEach(ss -> System.out.print(ss + " "));
            System.out.println();
            System.out.print("Actual SigSchemes: ");
            actualSS.forEach(ss -> System.out.print(ss + " "));
            System.out.println();
            throw new RuntimeException(
                    "FAIL: Expected and Actual values differ.");
        }

        // Consume the ClientHello and get the server flight of handshake
        // messages.  We expect that it will be one TLS record containing
        // multiple handshake messages, one of which is a CertificateRequest.
        serverEngine.unwrap(cTOs, serverIn);
        runDelegatedTasks(serverEngine);

        // Wrap the server flight
        serverEngine.wrap(serverOut, sTOc);
        sTOc.flip();

        actualSS = getSigSchemesCertReq(
                extractHandshakeMsg(sTOc, TLS_HS_CERT_REQ));

        // Make sure the ordering is correct
        if (!expectedSS.equals(actualSS)) {
            System.out.println("FAIL: Mismatch between property ordering " +
                    "and CertificateRequest message");
            System.out.print("Expected SigSchemes: ");
            expectedSS.forEach(ss -> System.out.print(ss + " "));
            System.out.println();
            System.out.print("Actual SigSchemes: ");
            actualSS.forEach(ss -> System.out.print(ss + " "));
            System.out.println();
            throw new RuntimeException(
                    "FAIL: Expected and Actual values differ.");
        }
    }
}
