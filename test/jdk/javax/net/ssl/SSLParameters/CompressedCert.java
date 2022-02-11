/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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

// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.

/*
 * @test
 * @bug 8273042
 * @summary TLS Certificate Compression
 * @library /javax/net/ssl/templates
 * @run main/othervm CompressedCert
 */

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.security.Security;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class CompressedCert extends SSLSocketTemplate {
    private final Map<String, Function<byte[], byte[]>> certDeflaters;
    private final Map<String, Function<byte[], byte[]>> certInflaters;
    private final boolean requireClientCert;
    private final boolean exceptionExpected;

    private static final Function<byte[], byte[]> certDeflater = (input) -> {
        Deflater deflater = new Deflater();
        deflater.setInput(input);
        deflater.finish();
        byte[] output = new byte[input.length];
        int l = deflater.deflate(output);
        deflater.end();

        byte[] data = new byte[l];
        System.arraycopy(output, 0, data, 0, l);

        return data;
    };

    private static final Function<byte[], byte[]> certInflater = (input) -> {
        try {
            Inflater inflater = new Inflater();
            inflater.setInput(input);
            byte[] output = new byte[1024 * 8];
            int l = inflater.inflate(output);
            inflater.end();

            byte[] data = new byte[l];
            System.arraycopy(output, 0, data, 0, l);

            return data;
        } catch (Exception ex) {
            // just ignore
            return null;
        }
    };

    private static final Function<byte[], byte[]> invalidDeflater = (input) -> {
        return input;
    };

    private static final Function<byte[], byte[]> invalidInflater = (input) -> {
        return input;
    };

    public CompressedCert(
            Map<String, Function<byte[], byte[]>> certDeflaters,
            Map<String, Function<byte[], byte[]>> certInflaters,
            boolean requireClientCert,
            boolean exceptionExpected) {
        this.certDeflaters = certDeflaters;
        this.certInflaters = certInflaters;
        this.requireClientCert =  requireClientCert;
        this.exceptionExpected = exceptionExpected;
    }

    @Override
    protected void configureServerSocket(SSLServerSocket sslServerSocket) {
        SSLParameters sslParameters = sslServerSocket.getSSLParameters();
        sslParameters.setCertificateDeflaters(certDeflaters);
        sslParameters.setCertificateInflaters(certInflaters);
        sslParameters.setNeedClientAuth(requireClientCert);
        sslServerSocket.setSSLParameters(sslParameters);
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        SSLParameters sslParameters = socket.getSSLParameters();
        sslParameters.setCertificateDeflaters(certDeflaters);
        sslParameters.setCertificateInflaters(certInflaters);
        socket.setSSLParameters(sslParameters);
    }

    @Override
    protected void runServerApplication(SSLSocket socket) {
        try {
            super.runServerApplication(socket);
        } catch (Exception ex) {
            // Just ignore, let the client handle the failure information.
        }
    }

    @Override
    protected void runClientApplication(SSLSocket sslSocket) throws Exception {
        try {
            super.runClientApplication(sslSocket);
            if (exceptionExpected) {
                throw new RuntimeException("Unexpected success!");
            }
        } catch (Exception ex) {
            if (!exceptionExpected) {
                throw ex;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Security.setProperty("jdk.tls.disabledAlgorithms", "");

        runTest(Map.of("zlib", certDeflater),
                Map.of("zlib", certInflater),
                false,
                false);
        runTest(Map.of("zlib", certDeflater),
                Map.of("zlib", certInflater),
                true,
                false);

        runTest(Map.of("zlib", certDeflater),
                Map.of(),
                false,
                false);
        runTest(Map.of("zlib", certDeflater),
                Map.of(),
                true,
                false);

        runTest(Map.of("zlib", certDeflater),
                null,
                false,
                false);
        runTest(Map.of("zlib", certDeflater),
                null,
                true,
                false);

        runTest(Map.of(),
                Map.of("zlib", certInflater),
                false,
                false);
        runTest(Map.of(),
                Map.of("zlib", certInflater),
                true,
                false);

        runTest(Map.of(),
                Map.of(),
                false,
                false);
        runTest(Map.of(),
                Map.of(),
                true,
                false);

        runTest(Map.of(),
                null,
                false,
                false);
        runTest(Map.of(),
                null,
                true,
                false);

        runTest(null,
                Map.of("zlib", certInflater),
                false,
                false);
        runTest(null,
                Map.of("zlib", certInflater),
                true,
                false);

        runTest(null,
                Map.of(),
                false,
                false);
        runTest(null,
                Map.of(),
                true,
                false);

        runTest(null,
                null,
                false,
                false);
        runTest(null,
                null,
                true,
                false);

        runTest(Map.of("zlib", certDeflater),
                Map.of("brotli", certInflater),
                false,
                false);
        runTest(Map.of("zlib", certDeflater),
                Map.of("brotli", certInflater),
                true,
                false);

        runTest(Map.of("brotli", certDeflater),
                Map.of("zlib", certInflater),
                false,
                false);
        runTest(Map.of("brotli", certDeflater),
                Map.of("zlib", certInflater),
                true,
                false);

        runTest(Map.of("zlib", certDeflater),
                Map.of("zlib", invalidInflater),
                false,
                true);
        runTest(Map.of("zlib", certDeflater),
                Map.of("zlib", invalidInflater),
                true,
                true);

        runTest(Map.of("zlib", invalidDeflater),
                Map.of("zlib", certInflater),
                false,
                true);
        runTest(Map.of("zlib", invalidDeflater),
                Map.of("zlib", certInflater),
                true,
                true);
    }

    private static void runTest(
            Map<String, Function<byte[], byte[]>> certDeflaters,
            Map<String, Function<byte[], byte[]>> certInflaters,
            boolean requireClientCert,
            boolean exceptionExpected) throws Exception {
        new CompressedCert(certDeflaters, certInflaters,
                requireClientCert, exceptionExpected).run();
    }
}
