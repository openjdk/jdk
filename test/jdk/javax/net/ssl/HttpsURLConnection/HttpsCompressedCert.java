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

import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static jdk.test.lib.Asserts.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.net.ssl.SSLParameters;

/*
 * @test
 * @bug 8372526
 * @summary TLS certificate compression
 * @library /test/lib
 *          /javax/net/ssl/templates
 * @run main/othervm HttpsCompressedCert true
 * @run main/othervm HttpsCompressedCert false
 */

public class HttpsCompressedCert {
    public static void main(String[] args) throws Exception {
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setEnableCertificateCompression(
                Boolean.parseBoolean(args[0]));
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(SSLClientContext.createClientSSLContext())
                .version(HttpClient.Version.HTTP_2)
                .sslParameters(sslParameters)
                .build();

        HttpRequest httpRequest = HttpRequest.newBuilder(
                        new URI("https://www.google.com/"))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(
                httpRequest, ofString());
        assertEquals(response.statusCode(), 200);
    }
}

