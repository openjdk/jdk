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
 * @summary Test ML-DSA client-authentication certificate selection and
 *          parameter matching
 * @library /javax/net/ssl/templates
 *
 * @run main/othervm
 *      -Djdk.tls.server.SignatureSchemes=mldsa65,rsa_pkcs1_sha384
 *      -Djdk.tls.client.SignatureSchemes=mldsa65,rsa_pkcs1_sha384
 *      -Dtest.case=success65
 *      MLDSAClientAuthMismatch
 *
 * @run main/othervm
 *      -Djdk.tls.server.SignatureSchemes=mldsa87,rsa_pkcs1_sha384
 *      -Djdk.tls.client.SignatureSchemes=mldsa87,rsa_pkcs1_sha384
 *      -Dtest.case=success87
 *      MLDSAClientAuthMismatch
 *
 * @run main/othervm
 *      -Djdk.tls.server.SignatureSchemes=mldsa65,rsa_pkcs1_sha384
 *      -Djdk.tls.client.SignatureSchemes=mldsa44,mldsa65,rsa_pkcs1_sha384
 *      -Dtest.case=failClientAuthMismatch
 *      MLDSAClientAuthMismatch
 *
 * @run main/othervm
 *      -Djdk.tls.server.SignatureSchemes=mldsa44,mldsa65
 *      -Djdk.tls.client.SignatureSchemes=mldsa44,mldsa65
 *      -Dtest.case=success44
 *      MLDSAClientAuthMismatch
 */

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

public class MLDSAClientAuthMismatch extends SSLSocketTemplate {

    private static final String TEST_CASE = System.getProperty("test.case");

    @Override
    protected SSLContext createServerSSLContext() throws Exception {
        return switch (TEST_CASE) {
            case "success65", "failClientAuthMismatch" ->
                    createSSLContext(
                            new Cert[] { Cert.CA_RSA_SHA384_FOR_MLDSA },
                            new Cert[] { Cert.EE_MLDSA_65 },
                            getServerContextParameters());
            case "success87" ->
                    createSSLContext(
                            new Cert[] { Cert.CA_RSA_SHA384_FOR_MLDSA },
                            new Cert[] { Cert.EE_MLDSA_87 },
                            getServerContextParameters());
            case "success44" ->
                    createSSLContext(
                            new Cert[] { Cert.CA_MLDSA_65 },
                            new Cert[] { Cert.EE_MLDSA_44_BY_CA_MLDSA_65 },
                            getServerContextParameters());
            default -> throw new RuntimeException(
                    "Unknown test case: " + TEST_CASE);
        };
    }

    @Override
    protected SSLContext createClientSSLContext() throws Exception {
        return switch (TEST_CASE) {
            case "success65" -> createSSLContext(
                    new Cert[] { Cert.CA_RSA_SHA384_FOR_MLDSA },
                    new Cert[] { Cert.EE_MLDSA_65 },
                    getClientContextParameters());
            case "success87" -> createSSLContext(
                    new Cert[] { Cert.CA_RSA_SHA384_FOR_MLDSA },
                    new Cert[] { Cert.EE_MLDSA_87 },
                    getClientContextParameters());
            case "failClientAuthMismatch" -> createSSLContext(
                    new Cert[] { Cert.CA_RSA_SHA384_FOR_MLDSA },
                    new Cert[] { Cert.EE_MLDSA_44 },
                    getClientContextParameters());
            case "success44" -> createSSLContext(
                    new Cert[] { Cert.CA_MLDSA_65 },
                    new Cert[] { Cert.EE_MLDSA_44_BY_CA_MLDSA_65 },
                    getClientContextParameters());
            default -> throw new RuntimeException(
                    "Unknown test case: " + TEST_CASE);
        };
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        socket.setEnabledProtocols(new String[] {"TLSv1.3"});
    }

    @Override
    protected void configureServerSocket(SSLServerSocket socket) {
        socket.setEnabledProtocols(new String[] {"TLSv1.3"});
        // require client authentication
        socket.setNeedClientAuth(true);
    }

    public static void main(String[] args) throws Exception {
        boolean expectFail = "failClientAuthMismatch".equals(TEST_CASE);

        try {
            new MLDSAClientAuthMismatch().run();
            if (expectFail) {
                throw new RuntimeException(
                        "Expected SSLHandshakeException was not thrown");
            }
        } catch (SSLHandshakeException e) {
            if (!expectFail) {
                throw e;
            }

            System.out.println("Expected SSLHandshakeException: " +
                    e.getMessage());
        }
    }
}
