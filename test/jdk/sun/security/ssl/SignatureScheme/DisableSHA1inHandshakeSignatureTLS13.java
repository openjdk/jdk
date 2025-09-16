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
 *          This test only covers TLS 1.3.
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm DisableSHA1inHandshakeSignatureTLS13
 */

import java.security.Security;
import java.util.List;

public class DisableSHA1inHandshakeSignatureTLS13 extends
        DisableSHA1inHandshakeSignatureTLS12 {

    protected DisableSHA1inHandshakeSignatureTLS13() throws Exception {
        super();
    }

    public static void main(String[] args) throws Exception {
        // SHA-1 algorithm MUST NOT be used in any TLSv1.3 handshake signatures.
        // This is regardless of jdk.tls.disabledAlgorithms configuration.
        Security.setProperty("jdk.tls.disabledAlgorithms", "");
        new DisableSHA1inHandshakeSignatureTLS13().run();
    }

    @Override
    protected String getProtocol() {
        return "TLSv1.3";
    }

    // Returns SHA-1 signature schemes NOT supported for TLSv1.3 handshake
    // signatures, but supported for TLSv1.3 certificate signatures.
    @Override
    protected List<String> getDisabledSignatureSchemes() {
        return List.of("ecdsa_sha1", "rsa_pkcs1_sha1");
    }

    // TLSv1.3 sends CertificateRequest signature schemes in
    // signature_algorithms and signature_algorithms_cert extensions. Same as
    // ClientHello, but they are encrypted. So we skip CertificateRequest
    // signature schemes verification for TLSv1.3.
    @Override
    protected void checkCertificateRequest() {
    }
}
