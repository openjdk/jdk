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


import java.security.Security;

public class DisableSignatureSchemePerScopeTLS13
        extends DisableSignatureSchemePerScopeTLS12 {

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

    // TLSv1.3 sends CertificateRequest signature schemes in
    // signature_algorithms and signature_algorithms_cert extensions. Same as
    // ClientHello, but they are encrypted. So we skip CertificateRequest
    // signature schemes verification for TLSv1.3.
    @Override
    protected void checkCertificateRequest() {
    }
}
