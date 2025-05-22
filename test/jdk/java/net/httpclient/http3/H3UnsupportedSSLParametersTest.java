/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.UnsupportedProtocolVersionException;

import javax.net.ssl.SSLParameters;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @summary Tests that a HttpClient configured with SSLParameters that doesn't include TLSv1.3
 *          cannot be used for HTTP3
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @run junit H3UnsupportedSSLParametersTest
 */
public class H3UnsupportedSSLParametersTest {

    /**
     * Configures a HttpClient builder to use a SSLParameter which doesn't list TLSv1.3
     * as one of the supported protocols. The method then uses this builder
     * to create a HttpClient for HTTP3 and expects that build() to fail with
     * UnsupportedProtocolVersionException
     */
    @Test
    public void testNoTLSv13() throws Exception {
        final SSLParameters params = new SSLParameters();
        params.setProtocols(new String[]{"TLSv1.2"});
        final UncheckedIOException uioe = assertThrows(UncheckedIOException.class,
                () -> HttpServerAdapters.createClientBuilderForH3()
                        .proxy(HttpClient.Builder.NO_PROXY)
                        .version(HttpClient.Version.HTTP_3)
                        .sslParameters(params)
                        .build());
        assertTrue(uioe.getCause() instanceof UnsupportedProtocolVersionException,
                "Unexpected cause " + uioe.getCause() + " in HttpClient build failure");
    }

    /**
     * Builds a HttpClient with SSLParameters which explicitly lists TLSv1.3 as one of the supported
     * protocol versions and expects the build() to succeed and return a HttpClient instance
     */
    @Test
    public void testExplicitTLSv13() throws Exception {
        final SSLParameters params = new SSLParameters();
        params.setProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
        final HttpClient client = HttpServerAdapters.createClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .sslParameters(params)
                .version(HttpClient.Version.HTTP_3).build();
        assertNotNull(client, "HttpClient is null");
    }
}
