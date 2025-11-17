/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.quic.QuicServerConnection;
import jdk.httpclient.test.lib.quic.QuicStandaloneServer;
import jdk.internal.net.http.quic.TerminationCause;
import jdk.internal.net.quic.QuicVersion;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static org.testng.Assert.*;

/*
 * @test
 * @summary Verifies that the HTTP client correctly handles malformed responses
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @library ../access
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @build java.net.http/jdk.internal.net.http.Http3ConnectionAccess
 * @run testng/othervm
 *              -Djdk.internal.httpclient.debug=true
 *              -Djdk.httpclient.HttpClient.log=requests,responses,errors H3MalformedResponseTest
 */

/// Verifies that the HTTP/3 malformed responses are correctly handled.
///
/// ### HTTP/3 `HEADERS` frame & QPACK Field Section encoding crash course
///
/// Consider an HTTP/3 `HEADERS` frame that carries:
///
/// ```
/// :status: 200
/// content-length: 2
/// ```
///
/// This will be encoded as the following byte sequence:
///
/// ```
/// 01 06 00 00 D9 54 01 32
/// ```
///
/// Let's start with decoding the HTTP/3 frame:
///
/// - `01`: Frame Type (`01` denotes `HEADERS``)
///
/// - `06`: Payload length (6 bytes)
///
/// Figured this is a `HEADERS` frame containing 6 bytes: `00 00 D9 54 01 32`.
/// Let's decode the QPACK Field Section
///
/// - `00`: Required Insert Count (0)
///
/// - `00`: Base (0)
///
/// - `D9`:
///   QPACK has a static table (indexed from 0) and `:status: 200` is at the
///   static-table index 25.
///
///   ```
///   D9 = <1101 1001>
///      = <1> (Indexed Field Line)
///      + <1> (Static Table)
///      + <01 1001> (entry index = 25)
///   ```
///
/// - `54 01 32`:
///   `content-length: 2` can be encoded as a *literal field line with name
///   reference* using the static name `content-length` (static index 4) and
///   the literal value `2`.
///
///   ```
///   54 01 32 = <0101 0100 0000 0001 0011 0010>
///            = <0> (Literal Field Line)
///            + <1> (Name Reference)
///            + <0100> (entry index = 4)
///            + <0000 0001> (value length = 1 byte)
///            + <0011 0010> (value = ASCII "2" = 0x32 = 50)
///   ```
///
/// Note that the `value length` field (i.e., `0000 0001`) follows a variable
/// coding scheme:
///
/// | Prefix                | Total size | Payload size | Max value     |
/// | --------------------- | ---------- | ------------ | ------------- |
/// | `00xx xxxx`           | 1 byte     | 6 bits       | 63            |
/// | `01xx xxxx xxxx xxxx` | 2 bytes    | 14 bits      | 16,383        |
/// | `10xx xxx…`           | 4 bytes    | 30 bits      | 1,073,741,823 |
/// | `11xx xxx…`           | 8 bytes    | 62 bits      | 4.61e18       |
public class H3MalformedResponseTest implements HttpServerAdapters {

    private SSLContext sslContext;
    private QuicStandaloneServer server;
    private String requestURIBase;

    // These responses are malformed and should not be accepted by the client,
    // but they should not cause connection closure
    @DataProvider
    public static Object[][] malformedResponse() {
        return new Object[][]{
                new Object[] {"empty", HexFormat.of().parseHex(
                        ""
                )},
                new Object[] {"non-final response", HexFormat.of().parseHex(
                        "01040000"+ // headers, length 4, section prefix
                                "ff00" // :status:100
                )},
                new Object[] {"uppercase header name", HexFormat.of().parseHex(
                        "01090000"+ // headers, length 9, section prefix
                                "d9"+ // :status:200
                                "234147450130"+ // AGE:0
                                "000100" // data, 1 byte
                )},
                new Object[] {"content too long", HexFormat.of().parseHex(
                        "01040000"+ // headers, length 4, section prefix
                                "d9"+ // :status:200
                                "c4"+ // content-length:0
                                "000100" // data, 1 byte
                )},
                new Object[] {"content too short", HexFormat.of().parseHex(
                        "01060000"+ // headers, length 6, section prefix
                                "d9"+ // :status:200
                                "540132"+ // content-length:2
                                "000100" // data, 1 byte
                )},
                new Object[] {"text in content-length", HexFormat.of().parseHex(
                        "01060000"+ // headers, length 6, section prefix
                                "d9"+ // :status:200
                                "540161"+ // content-length:a
                                "000100" // data, 1 byte
                )},
                new Object[] {"connection: close", HexFormat.of().parseHex(
                        "01150000"+ // headers, length 21, section prefix
                                "d9"+ // :status:200
                                "2703636F6E6E656374696F6E05636C6F7365"+ // connection:close
                                "000100" // data, 1 byte
                )},
                // request pseudo-headers in response
                new Object[] {":method in response", HexFormat.of().parseHex(
                        "01040000"+ // headers, length 4, section prefix
                                "d9"+ // :status:200
                                "d1"+ // :method:get
                                "000100" // data, 1 byte
                )},
                new Object[] {":authority in response", HexFormat.of().parseHex(
                        "01100000"+ // headers, length 16, section prefix
                                "d9"+ // :status:200
                                "508b089d5c0b8170dc702fbce7"+ // :authority
                                "000100" // data, 1 byte
                )},
                new Object[] {":path in response", HexFormat.of().parseHex(
                        "010a0000"+ // headers, length 10, section prefix
                                "d9"+ // :status:200
                                "51856272d141ff"+ // :path
                                "000100" // data, 1 byte
                )},
                new Object[] {":scheme in response", HexFormat.of().parseHex(
                        "01040000"+ // headers, length 4, section prefix
                                "d9"+ // :status:200
                                "d7"+ // :scheme:https
                                "000100" // data, 1 byte
                )},
                new Object[] {"undefined pseudo-header", HexFormat.of().parseHex(
                        "01080000"+ // headers, length 8, section prefix
                                "d9"+ // :status:200
                                "223A6D0130"+ // :m:0
                                "000100" // data, 1 byte
                )},
                new Object[] {"pseudo-header after regular", HexFormat.of().parseHex(
                        "011a0000"+ // headers, length 26, section prefix
                                "5f5094ca3ee35a74a6b589418b5258132b1aa496ca8747"+ //user-agent
                                "d9"+ // :status:200
                                "000100" // data, 1 byte
                )},
                new Object[] {"trailer", HexFormat.of().parseHex(
                        "01020000" // headers, length 2, section prefix
                )},
                new Object[] {"trailer+data", HexFormat.of().parseHex(
                        "01020000"+ // headers, length 2, section prefix
                                "000100" // data, 1 byte
                )},
                //  valid characters include \t, 0x20-0x7e, 0x80-0xff (RFC 9110, section 5.5)
                new Object[] {"invalid character in field value 00", HexFormat.of().parseHex(
                        "01060000"+ // headers, length 6, section prefix
                                "d9"+ // :status:200
                                "570100"+ // etag:\0
                                "000100" // data, 1 byte
                )},
                new Object[] {"invalid character in field value 0a", HexFormat.of().parseHex(
                        "01060000"+ // headers, length 6, section prefix
                                "d9"+ // :status:200
                                "57010a"+ // etag:\n
                                "000100" // data, 1 byte
                )},
                new Object[] {"invalid character in field value 0d", HexFormat.of().parseHex(
                        "01060000"+ // headers, length 6, section prefix
                                "d9"+ // :status:200
                                "57010d"+ // etag:\r
                                "000100" // data, 1 byte
                )},
                new Object[] {"invalid character in field value 7f", HexFormat.of().parseHex(
                        "01060000"+ // headers, length 6, section prefix
                                "d9"+ // :status:200
                                "57017f"+ // etag: 0x7f
                                "000100" // data, 1 byte
                )},
        };
    }

    // These responses are malformed and should not be accepted by the client.
    // They might or might not cause connection closure (H3_FRAME_UNEXPECTED)
    @DataProvider
    public static Object[][] malformedResponse2() {
        // data before headers is covered by H3ErrorHandlingTest
        return new Object[][]{
                new Object[] {"100+data", HexFormat.of().parseHex(
                        "01040000"+ // headers, length 4, section prefix
                                "ff00"+ // :status:100
                                "000100" // data, 1 byte
                )},
                new Object[] {"100+data+200", HexFormat.of().parseHex(
                        "01040000"+ // headers, length 4, section prefix
                                "ff00"+ // :status:100
                                "000100"+ // data, 1 byte
                                "01030000"+ // headers, length 3, section prefix
                                "d9" // :status:200
                )},
                new Object[] {"200+data+200", HexFormat.of().parseHex(
                        "01030000"+ // headers, length 3, section prefix
                                "d9"+ // :status:200
                                "000100"+ // data, 1 byte
                                "01030000"+ // headers, length 3, section prefix
                                "d9" // :status:200
                )},
                new Object[] {"200+data+100", HexFormat.of().parseHex(
                        "01030000"+ // headers, length 3, section prefix
                                "d9"+ // :status:200
                                "000100"+ // data, 1 byte
                                "01040000"+ // headers, length 4, section prefix
                                "ff00" // :status:100
                )},
                new Object[] {"200+data+trailers+data", HexFormat.of().parseHex(
                        "01030000"+ // headers, length 3, section prefix
                                "d9"+ // :status:200
                                "000100"+ // data, 1 byte
                                "01020000"+ // trailers, length 2, section prefix
                                "000100" // data, 1 byte
                )},
                new Object[] {"200+trailers+data", HexFormat.of().parseHex(
                        "01030000"+ // headers, length 3, section prefix
                                "d9"+ // :status:200
                                "01020000"+ // trailers, length 2, section prefix
                                "000100" // data, 1 byte
                )},
                new Object[] {"200+200", HexFormat.of().parseHex(
                        "01030000"+ // headers, length 3, section prefix
                                "d9"+ // :status:200
                                "01030000"+ // headers, length 3, section prefix
                                "d9" // :status:200
                )},
                new Object[] {"200+100", HexFormat.of().parseHex(
                        "01030000"+ // headers, length 3, section prefix
                                "d9"+ // :status:200
                                "01040000"+ // headers, length 4, section prefix
                                "ff00" // :status:100
                )},
        };
    }

    @DataProvider
    public static Object[][] wellformedResponse() {
        return new Object[][]{
                new Object[] {"100+200+data+reserved", HexFormat.of().parseHex(
                        "01040000"+ // headers, length 4, section prefix
                                "ff00"+ // :status:100
                        "01030000"+ // headers, length 3, section prefix
                                "d9"+ // :status:200
                                "000100"+ // data, 1 byte
                                "210100" // reserved, 1 byte
                )},
                new Object[] {"200+data+reserved", HexFormat.of().parseHex(
                        "01030000"+ // headers, length 3, section prefix
                                "d9"+ // :status:200
                                "000100"+ // data, 1 byte
                                "210100" // reserved, 1 byte
                )},
                new Object[] {"200+data", HexFormat.of().parseHex(
                        "01030000"+ // headers, length 3, section prefix
                                "d9"+ // :status:200
                                "000100" // data, 1 byte
                )},
                new Object[] {"200+user-agent+data", HexFormat.of().parseHex(
                        "011a0000"+ // headers, length 26, section prefix
                                "d9"+ // :status:200
                                "5f5094ca3ee35a74a6b589418b5258132b1aa496ca8747"+ //user-agent
                                "000100" // data, 1 byte
                )},
                new Object[] {"200", HexFormat.of().parseHex(
                        "01030000"+ // headers, length 3, section prefix
                                "d9" // :status:200
                )},
                new Object[] {"200+data+data", HexFormat.of().parseHex(
                        "01030000"+ // headers, length 3, section prefix
                                "d9"+ // :status:200
                                "000100"+ // data, 1 byte
                                "000100" // data, 1 byte
                )},
                new Object[] {"200+data+trailers", HexFormat.of().parseHex(
                        "01030000"+ // headers, length 3, section prefix
                                "d9"+ // :status:200
                                "000100"+ // data, 1 byte
                                "01020000" // trailers, length 2, section prefix
                )},
                new Object[] {"200+trailers", HexFormat.of().parseHex(
                        "01030000"+ // headers, length 3, section prefix
                                "d9"+ // :status:200
                                "01020000" // trailers, length 2, section prefix
                )},
        };
    }

    @BeforeClass
    public void beforeClass() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null) {
            throw new AssertionError("Unexpected null sslContext");
        }
        server = QuicStandaloneServer.newBuilder()
                .availableVersions(new QuicVersion[]{QuicVersion.QUIC_V1})
                .sslContext(sslContext)
                .alpn("h3")
                .build();
        server.start();
        System.out.println("Server started at " + server.getAddress());
        requestURIBase = URIBuilder.newBuilder().scheme("https").loopback()
                .port(server.getAddress().getPort()).build().toString();
    }

    @AfterClass
    public void afterClass() throws Exception {
        if (server != null) {
            System.out.println("Stopping server " + server.getAddress());
            server.close();
        }
    }

    /**
     * Server sends a well-formed response
     */
    @Test(dataProvider = "wellformedResponse")
    public void testWellFormedResponse(String desc, byte[] response) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            try (OutputStream outputStream = s.outputStream()) {
                outputStream.write(response);
            }
            // verify that the connection stays open
            completeUponTermination(c, errorCF);
        });
        HttpClient client = getHttpClient();
        try {
            HttpRequest request = getRequest();
            final HttpResponse<Void> response1 = client.send(
                    request,
                    BodyHandlers.discarding());
            assertEquals(response1.statusCode(), 200);
            assertFalse(errorCF.isDone(), "Expected the connection to be open");
        } finally {
            client.shutdownNow();
        }
    }


    /**
     * Server sends a malformed response that should not close connection
     */
    @Test(dataProvider = "malformedResponse")
    public void testMalformedResponse(String desc, byte[] response) throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            try (OutputStream outputStream = s.outputStream()) {
                outputStream.write(response);
            }
            // verify that the connection stays open
            completeUponTermination(c, errorCF);
        });
        HttpClient client = getHttpClient();
        try {
            HttpRequest request = getRequest();
            final HttpResponse<Void> response1 = client.send(
                            request,
                            BodyHandlers.discarding());
            fail("Expected the request to fail, got " + response1);
        } catch (Exception e) {
            System.out.println("Got expected exception: " +e);
            e.printStackTrace();
            assertFalse(errorCF.isDone(), "Expected the connection to be open");
        } finally {
            client.shutdownNow();
        }
    }

    /**
     * Server sends a malformed response that might close connection
     */
    @Test(dataProvider = "malformedResponse2")
    public void testMalformedResponse2(String desc, byte[] response) throws Exception {
        server.addHandler((c,s)-> {
            try (OutputStream outputStream = s.outputStream()) {
                outputStream.write(response);
            }
        });
        HttpClient client = getHttpClient();
        try {
            HttpRequest request = getRequest();
            final HttpResponse<Void> response1 = client.send(
                            request,
                            BodyHandlers.discarding());
            fail("Expected the request to fail, got " + response1);
        } catch (Exception e) {
            System.out.println("Got expected exception: " +e);
            e.printStackTrace();
        } finally {
            client.shutdownNow();
        }
    }

    private HttpRequest getRequest() throws URISyntaxException {
        final URI reqURI = new URI(requestURIBase + "/hello");
        final HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(reqURI)
                .version(Version.HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);
        return reqBuilder.build();
    }

    private HttpClient getHttpClient() {
        final HttpClient client = newClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .version(Version.HTTP_3)
                .sslContext(sslContext).build();
        return client;
    }

    private static void completeUponTermination(final QuicServerConnection serverConnection,
                                                final CompletableFuture<TerminationCause> cf) {
        serverConnection.futureTerminationCause().handle(
                (r,t) -> t != null ? cf.completeExceptionally(t) : cf.complete(r));
    }
}
