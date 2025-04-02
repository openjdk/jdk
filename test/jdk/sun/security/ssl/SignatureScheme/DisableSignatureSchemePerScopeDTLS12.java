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
 *          This test only covers DTLS 1.2.
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm DisableSignatureSchemePerScopeDTLS12
 */

import java.security.Security;

public class DisableSignatureSchemePerScopeDTLS12
        extends DisableSignatureSchemePerScopeTLS12 {

    protected DisableSignatureSchemePerScopeDTLS12() throws Exception {
        super();
    }

    public static void main(String[] args) throws Exception {
        Security.setProperty(
                "jdk.tls.disabledAlgorithms", DISABLED_CONSTRAINTS);
        new DisableSignatureSchemePerScopeDTLS12().run();
    }

    @Override
    protected String getProtocol() {
        return "DTLSv1.2";
    }

    // No CertificateRequest in DTLS server flight.
    @Override
    protected void checkCertificateRequest() {
    }
}
