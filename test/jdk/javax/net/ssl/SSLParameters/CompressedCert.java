/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/*
 * @test
 * @bug 8372526
 * @summary TLS Certificate Compression
 * @library /javax/net/ssl/templates
 * @run main/othervm CompressedCert
 */

public class CompressedCert extends SSLSocketTemplate {

    private final boolean enableClientCertComp;
    private final boolean enableServerCertComp;
    private final boolean requireClientCert;

    public CompressedCert(
            boolean enableClientCertComp,
            boolean enableServerCertComp,
            boolean requireClientCert) {
        this.enableClientCertComp = enableClientCertComp;
        this.enableServerCertComp = enableServerCertComp;
        this.requireClientCert = requireClientCert;
    }

    @Override
    protected void configureServerSocket(SSLServerSocket sslServerSocket) {
        SSLParameters sslParameters = sslServerSocket.getSSLParameters();
        sslParameters.setEnableCertificateCompression(enableClientCertComp);
        sslParameters.setNeedClientAuth(requireClientCert);
        sslServerSocket.setSSLParameters(sslParameters);
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        SSLParameters sslParameters = socket.getSSLParameters();
        sslParameters.setEnableCertificateCompression(enableServerCertComp);
        socket.setSSLParameters(sslParameters);
    }

    public static void main(String[] args) throws Exception {
        runTest(false, false, false);
        runTest(false, false, true);
        runTest(false, true, false);
        runTest(true, false, true);
        runTest(true, true, false);
        runTest(true, true, true);
    }

    private static void runTest(
            boolean enableClientCertComp,
            boolean enableServerCertComp,
            boolean requireClientCert) throws Exception {
        new CompressedCert(enableClientCertComp, enableServerCertComp,
                requireClientCert).run();
    }
}
