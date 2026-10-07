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
 * @summary Ensure ML-DSA signature schemes are advertised in TLS 1.3
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm MLDSAAllowedInTLS13
 */

import java.util.List;

public class MLDSAAllowedInTLS13 extends MLDSANotAllowedInTLS12 {

    protected MLDSAAllowedInTLS13() throws Exception {
        super();
    }

    public static void main(String[] args) throws Exception {
        new MLDSAAllowedInTLS13().run();
    }

    @Override
    protected String getProtocol() {
        return "TLSv1.3";
    }

    @Override
    protected void checkClientHello() throws Exception {
        // Get signature_algorithms extension signature schemes
        List<String> sigAlgsSS = getSigSchemesCliHello(
                extractHandshakeMsg(cTOs, TLS_HS_CLI_HELLO),
                SIG_ALGS_EXT);

        assertMldsa(sigAlgsSS, "ClientHello signature_algorithms extension");

        // Get signature_algorithms_cert extension signature schemes
        List<String> sigAlgsCertSS = getSigSchemesCliHello(
                extractHandshakeMsg(cTOs, TLS_HS_CLI_HELLO),
                SIG_ALGS_CERT_EXT);

        assertMldsa(sigAlgsCertSS,
                "ClientHello signature_algorithms_cert extension");
    }

    // TLS 1.3 CertificateRequest message is encrypted
    @Override
    protected void checkCertificateRequest() {
    }
}
