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
 * @bug 8365820
 * @summary Apply certificate scope constraints to algorithms in
 *          "signature_algorithms" extension when
 *          "signature_algorithms_cert" extension is not being sent.
 *          This test covers the client side for TLSv1.3.
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm DisableCertSignAlgsExtForClientTLS13
 */

import static jdk.test.lib.Asserts.assertFalse;

import java.security.Security;
import java.util.List;

// Test disabled signature_algorithms_cert extension on the client side
// for TLSv1.3.
public class DisableCertSignAlgsExtForClientTLS13 extends
        DisableCertSignAlgsExtForClientTLS12 {

    protected DisableCertSignAlgsExtForClientTLS13()
            throws Exception {
        super();
    }

    public static void main(String[] args) throws Exception {
        Security.setProperty(
                "jdk.tls.disabledAlgorithms", DISABLED_CONSTRAINTS);
        // Disable signature_algorithms_cert extension for the client.
        System.setProperty("jdk.tls.client.disableExtensions",
                "signature_algorithms_cert");
        new DisableCertSignAlgsExtForClientTLS13().run();
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

        // These signature schemes MOST NOT be present in signature_algorithms
        // extension.
        TLS13_CERT_ONLY.forEach(ss ->
                assertFalse(sigAlgsSS.contains(ss), "Signature Scheme " + ss
                        + " present in ClientHello's"
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
