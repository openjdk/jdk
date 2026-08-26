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
 * @summary Test ML-DSA certificate selection and parameter matching
 * @library /test/lib
 *          /javax/net/ssl/templates
 *
 * @run main/othervm
 *      -Djdk.tls.server.SignatureSchemes=mldsa65,rsa_pkcs1_sha384
 *      -Djdk.tls.client.SignatureSchemes=mldsa65,rsa_pkcs1_sha384
 *      -Dtest.case=success65
 *      MLDSACertSelection
 *
 * @run main/othervm
 *      -Djdk.tls.server.SignatureSchemes=mldsa87,rsa_pkcs1_sha384
 *      -Djdk.tls.client.SignatureSchemes=mldsa87,rsa_pkcs1_sha384
 *      -Dtest.case=success87
 *      MLDSACertSelection
 *
 * @run main/othervm
 *      -Djdk.tls.server.SignatureSchemes=mldsa44,rsa_pkcs1_sha384
 *      -Djdk.tls.client.SignatureSchemes=mldsa44,rsa_pkcs1_sha384
 *      -Dtest.case=fail44
 *      MLDSACertSelection
 *
 * @run main/othervm
 *      -Djdk.tls.server.SignatureSchemes=mldsa65,rsa_pkcs1_sha384
 *      -Djdk.tls.client.SignatureSchemes=mldsa44,mldsa65,rsa_pkcs1_sha384
 *      -Dtest.case=fallback65
 *      MLDSACertSelection
 *
 * @run main/othervm
 *      -Djdk.tls.server.SignatureSchemes=mldsa44,mldsa65
 *      -Djdk.tls.client.SignatureSchemes=mldsa44,mldsa65
 *      -Dtest.case=success44
 *      MLDSACertSelection
 */

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

public class MLDSACertSelection extends SSLSocketTemplate {

    private final String testCase = System.getProperty("test.case");

    @Override
    protected SSLContext createServerSSLContext() throws Exception {
        return switch (testCase) {
            case "success65", "fail44", "fallback65" ->
                    createSSLContext(
                            new Cert[] { Cert.CA_RSA_SHA384_FOR_MLDSA },
                            new Cert[] { Cert.EE_MLDSA_65 },
                            getServerContextParameters());
            case "success44" ->
                    createSSLContext(
                            new Cert[] { Cert.CA_MLDSA_65 },
                            new Cert[] { Cert.EE_MLDSA_44_BY_CA_MLDSA_65 },
                            getServerContextParameters());
            case "success87" ->
                    createSSLContext(
                            new Cert[] { Cert.CA_RSA_SHA384_FOR_MLDSA },
                            new Cert[] { Cert.EE_MLDSA_87 },
                            getServerContextParameters());
            default -> throw new RuntimeException(
                    "Unknown test case: " + testCase);
        };
    }

    @Override
    protected SSLContext createClientSSLContext() throws Exception {
        return switch (testCase) {
            case "success65", "success87", "fail44", "fallback65" ->
                    createSSLContext(
                            new Cert[] { Cert.CA_RSA_SHA384_FOR_MLDSA },
                            null,
                            getClientContextParameters());
            case "success44" ->
                    createSSLContext(
                            new Cert[] { Cert.CA_MLDSA_65 },
                            null,
                            getClientContextParameters());
            default -> throw new RuntimeException(
                    "Unknown test case: " + testCase);
        };
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        socket.setEnabledProtocols(new String[] {"TLSv1.3"});
    }

    @Override
    protected void configureServerSocket(SSLServerSocket socket) {
        socket.setEnabledProtocols(new String[] {"TLSv1.3"});
    }

    public static void main(String[] args) throws Exception {
        String testCase = System.getProperty("test.case");
        boolean expectFail = "fail44".equals(testCase);

        try {
            new MLDSACertSelection().run();
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
