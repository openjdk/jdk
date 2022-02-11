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
 * @run main/othervm UnsetCompressedCert
 */

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.security.Security;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class UnsetCompressedCert extends SSLSocketTemplate {
    private final Map<String, Function<byte[], byte[]>> certDeflaters;
    private final Map<String, Function<byte[], byte[]>> certInflaters;
    private final boolean requireClientCert;

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

    public UnsetCompressedCert(
            Map<String, Function<byte[], byte[]>> certDeflaters,
            Map<String, Function<byte[], byte[]>> certInflaters,
            boolean requireClientCert) {
        this.certDeflaters = certDeflaters;
        this.certInflaters = certInflaters;
        this.requireClientCert =  requireClientCert;
    }

    @Override
    protected void configureServerSocket(SSLServerSocket sslServerSocket) {
        SSLParameters sslParameters = sslServerSocket.getSSLParameters();
        if (certDeflaters != null) {
            sslParameters.setCertificateDeflaters(certDeflaters);
        }
        if (certInflaters != null) {
            sslParameters.setCertificateInflaters(certInflaters);
        }
        sslParameters.setNeedClientAuth(requireClientCert);
        sslServerSocket.setSSLParameters(sslParameters);
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        SSLParameters sslParameters = socket.getSSLParameters();
        if (certDeflaters != null) {
            sslParameters.setCertificateDeflaters(certDeflaters);
        }

        if (certInflaters != null) {
            sslParameters.setCertificateInflaters(certInflaters);
        }
        socket.setSSLParameters(sslParameters);
    }

    public static void main(String[] args) throws Exception {
        Security.setProperty("jdk.tls.disabledAlgorithms", "");

        runTest(Map.of("zlib", certDeflater),
                null,
                false);
        runTest(Map.of("zlib", certDeflater),
                null,
                true);

        runTest(Map.of(),
                null,
                false);
        runTest(Map.of(),
                null,
                true);

        runTest(null,
                Map.of("zlib", certInflater),
                false);
        runTest(null,
                Map.of("zlib", certInflater),
                true);

        runTest(null,
                Map.of(),
                false);
        runTest(null,
                Map.of(),
                true);

        runTest(null,
                null,
                false);
        runTest(null,
                null,
                true);
    }

    private static void runTest(
            Map<String, Function<byte[], byte[]>> certDeflaters,
            Map<String, Function<byte[], byte[]>> certInflaters,
            boolean requireClientCert) throws Exception {
        new UnsetCompressedCert(
                certDeflaters, certInflaters, requireClientCert).run();
    }
}
