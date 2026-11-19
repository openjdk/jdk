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
 *          This test covers the client side for TLSv1.2.
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm DisableCertSignAlgsExtForClientTLS12
 */

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertFalse;

import java.security.Security;
import java.util.List;

// Test disabled signature_algorithms_cert extension on the client side
// for TLSv1.2.
public class DisableCertSignAlgsExtForClientTLS12 extends
        DisableSignatureSchemePerScopeTLS12 {

    protected DisableCertSignAlgsExtForClientTLS12()
            throws Exception {
        super();
    }

    public static void main(String[] args) throws Exception {
        Security.setProperty(
                "jdk.tls.disabledAlgorithms", DISABLED_CONSTRAINTS);
        // Disable signature_algorithms_cert extension for the client.
        System.setProperty("jdk.tls.client.disableExtensions",
                "signature_algorithms_cert");
        new DisableCertSignAlgsExtForClientTLS12().run();
    }

    @Override
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

        // signature_algorithms extension MUST NOT contain disabled
        // certificate signature scheme.
        assertFalse(sigAlgsSS.contains(CERTIFICATE_DISABLED_SIG),
                "Signature Scheme " + CERTIFICATE_DISABLED_SIG
                        + " present in ClientHello's signature_algorithms extension");

        // signature_algorithms_cert extension MUST NOT be present.
        assertEquals(getSigSchemesCliHello(extractHandshakeMsg(
                        cTOs, TLS_HS_CLI_HELLO), SIG_ALGS_CERT_EXT).size(), 0,
                "signature_algorithms_cert extension present in ClientHello");
    }
}
