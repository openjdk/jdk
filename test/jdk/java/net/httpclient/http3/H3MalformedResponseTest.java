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
import jdk.httpclient.test.lib.quic.QuicStandaloneServer;
import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.quic.QuicVersion;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8369595
 * @summary Verifies that the HTTP/3 malformed responses are correctly handled
 * @library /test/jdk/java/net/httpclient/lib
 *          /test/lib
 * @run junit ${test.main.class}
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
class H3MalformedResponseTest {

    private static final String CLASS_NAME = H3MalformedResponseTest.class.getSimpleName();

    private static final Logger LOGGER = Utils.getDebugLogger(CLASS_NAME::toString, Utils.DEBUG);

    private static SSLContext SSL_CONTEXT;

    private static QuicStandaloneServer SERVER;

    private static HttpRequest REQUEST;

    @BeforeAll
    static void setUp() throws Exception {

        // Obtain an `SSLContext`
        SSL_CONTEXT = new SimpleSSLContext().get();
        assertNotNull(SSL_CONTEXT);

        // Create and start the server
        SERVER = QuicStandaloneServer.newBuilder()
                .availableVersions(new QuicVersion[]{QuicVersion.QUIC_V1})
                .sslContext(SSL_CONTEXT)
                .alpn("h3")
                .build();
        SERVER.start();
        LOGGER.log("Server is started at {}", SERVER.getAddress());

        // Create the request
        var requestURI = URIBuilder.newBuilder()
                .scheme("https")
                .loopback()
                .port(SERVER.getAddress().getPort())
                .path("/" + CLASS_NAME)
                .build();
        REQUEST = HttpRequest.newBuilder(requestURI)
                .version(Version.HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                .build();

    }

    @AfterAll
    static void tearDown() {
        close("server", SERVER);
    }

    private static void close(String name, AutoCloseable closeable) {
        if (closeable != null) {
            LOGGER.log("Closing {}", name);
            try {
                closeable.close();
            } catch (Exception e) {
                LOGGER.log("Could not close " + name, e);
            }
        }
    }

    /// Malformed responses that should not be accepted by the client, but
    /// should neither cause the connection to get closed.
    static Object[][] malformedResponsesPreservingConnection() {
        return new Object[][]{
                {"empty", IOException.class, parseHex("")},
                {"non-final response", IOException.class, parseHex(
                        "01040000", // headers, length 4, section prefix
                        "ff00" // :status:100
                )},
                {"uppercase header name", ProtocolException.class, parseHex(
                        "01090000", // headers, length 9, section prefix
                        "d9", // :status:200
                        "234147450130", // AGE:0
                        "000100" // data, 1 byte
                )},
                {"content too long", IOException.class, parseHex(
                        "01040000", // headers, length 4, section prefix
                        "d9", // :status:200
                        "c4", // content-length:0
                        "000100" // data, 1 byte
                )},
                {"content too short", IOException.class, parseHex(
                        "01060000", // headers, length 6, section prefix
                        "d9", // :status:200
                        "540132", // content-length:2
                        "000100" // data, 1 byte
                )},
                {"text in content-length", ProtocolException.class, parseHex(
                        "01060000" + // headers, length 6, section prefix
                                "d9" + // :status:200
                                "540161" + // content-length:a
                                "000100" // data, 1 byte
                )},
                {"connection: close", ProtocolException.class, parseHex(
                        "01150000", // headers, length 21, section prefix
                        "d9", // :status:200
                        "2703636F6E6E656374696F6E05636C6F7365" + // connection:close
                                "000100" // data, 1 byte
                )},
                // request pseudo-headers in response
                {":method in response", ProtocolException.class, parseHex(
                        "01040000", // headers, length 4, section prefix
                        "d9", // :status:200
                        "d1", // :method:get
                        "000100" // data, 1 byte
                )},
                {":authority in response", ProtocolException.class, parseHex(
                        "01100000", // headers, length 16, section prefix
                        "d9", // :status:200
                        "508b089d5c0b8170dc702fbce7", // :authority
                        "000100" // data, 1 byte
                )},
                {":path in response", ProtocolException.class, parseHex(
                        "010a0000", // headers, length 10, section prefix
                        "d9", // :status:200
                        "51856272d141ff", // :path
                        "000100" // data, 1 byte
                )},
                {":scheme in response", ProtocolException.class, parseHex(
                        "01040000", // headers, length 4, section prefix
                        "d9", // :status:200
                        "d7", // :scheme:https
                        "000100" // data, 1 byte
                )},
                {"undefined pseudo-header", ProtocolException.class, parseHex(
                        "01080000", // headers, length 8, section prefix
                        "d9", // :status:200
                        "223A6D0130", // :m:0
                        "000100" // data, 1 byte
                )},
                {"pseudo-header after regular", ProtocolException.class, parseHex(
                        "011a0000", // headers, length 26, section prefix
                        "5f5094ca3ee35a74a6b589418b5258132b1aa496ca8747", //user-agent
                        "d9", // :status:200
                        "000100" // data, 1 byte
                )},
                {"trailer", IOException.class, parseHex(
                        "01020000" // headers, length 2, section prefix
                )},
                {"trailer+data", IOException.class, parseHex(
                        "01020000", // headers, length 2, section prefix
                        "000100" // data, 1 byte
                )},
                //  valid characters include \t, 0x20-0x7e, 0x80-0xff (RFC 9110, section 5.5)
                {"invalid character in field value 00", ProtocolException.class, parseHex(
                        "01060000", // headers, length 6, section prefix
                        "d9", // :status:200
                        "570100", // etag:\0
                        "000100" // data, 1 byte
                )},
                {"invalid character in field value 0a", ProtocolException.class, parseHex(
                        "01060000", // headers, length 6, section prefix
                        "d9", // :status:200
                        "57010a", // etag:\n
                        "000100" // data, 1 byte
                )},
                {"invalid character in field value 0d", ProtocolException.class, parseHex(
                        "01060000", // headers, length 6, section prefix
                        "d9", // :status:200
                        "57010d", // etag:\r
                        "000100" // data, 1 byte
                )},
                {"invalid character in field value 7f", ProtocolException.class, parseHex(
                        "01060000", // headers, length 6, section prefix
                        "d9", // :status:200
                        "57017f", // etag: 0x7f
                        "000100" // data, 1 byte
                )},
        };
    }

    /// Malformed responses that should not be accepted by the client.
    /// They might or might not cause the connection to get closed (`H3_FRAME_UNEXPECTED`).
    static Object[][] malformedResponses() {
        // data before headers is covered by H3ErrorHandlingTest
        return new Object[][]{
                {"100+data", IOException.class, parseHex(
                        "01040000", // headers, length 4, section prefix
                        "ff00", // :status:100
                        "000100" // data, 1 byte
                )},
                {"100+data+200", IOException.class, parseHex(
                        "01040000", // headers, length 4, section prefix
                        "ff00", // :status:100
                        "000100", // data, 1 byte
                        "01030000", // headers, length 3, section prefix
                        "d9" // :status:200
                )},
                {"200+data+200", IOException.class, parseHex(
                        "01030000", // headers, length 3, section prefix
                        "d9", // :status:200
                        "000100", // data, 1 byte
                        "01030000", // headers, length 3, section prefix
                        "d9" // :status:200
                )},
                {"200+data+100", IOException.class, parseHex(
                        "01030000", // headers, length 3, section prefix
                        "d9", // :status:200
                        "000100", // data, 1 byte
                        "01040000", // headers, length 4, section prefix
                        "ff00" // :status:100
                )},
                {"200+data+trailers+data", ProtocolException.class, parseHex(
                        "01030000", // headers, length 3, section prefix
                        "d9", // :status:200
                        "000100", // data, 1 byte
                        "01020000", // trailers, length 2, section prefix
                        "000100" // data, 1 byte
                )},
                {"200+trailers+data", IOException.class, parseHex(
                        "01030000", // headers, length 3, section prefix
                        "d9", // :status:200
                        "01020000", // trailers, length 2, section prefix
                        "000100" // data, 1 byte
                )},
                {"200+200", IOException.class, parseHex(
                        "01030000", // headers, length 3, section prefix
                        "d9", // :status:200
                        "01030000", // headers, length 3, section prefix
                        "d9" // :status:200
                )},
                {"200+100", IOException.class, parseHex(
                        "01030000", // headers, length 3, section prefix
                        "d9", // :status:200
                        "01040000", // headers, length 4, section prefix
                        "ff00" // :status:100
                )},
        };
    }

    /// Well-formed responses that should be accepted by the client.
    static Object[][] wellFormedResponses() {
        return new Object[][]{
                {"100+200+data+reserved", parseHex(
                        "01040000", // headers, length 4, section prefix
                        "ff00", // :status:100
                        "01030000", // headers, length 3, section prefix
                        "d9", // :status:200
                        "000100", // data, 1 byte
                        "210100" // reserved, 1 byte
                )},
                {"200+data+reserved", parseHex(
                        "01030000", // headers, length 3, section prefix
                        "d9", // :status:200
                        "000100", // data, 1 byte
                        "210100" // reserved, 1 byte
                )},
                {"200+data", parseHex(
                        "01030000", // headers, length 3, section prefix
                        "d9", // :status:200
                        "000100" // data, 1 byte
                )},
                {"200+user-agent+data", parseHex(
                        "011a0000", // headers, length 26, section prefix
                        "d9", // :status:200
                        "5f5094ca3ee35a74a6b589418b5258132b1aa496ca8747", //user-agent
                        "000100" // data, 1 byte
                )},
                {"200", parseHex(
                        "01030000", // headers, length 3, section prefix
                        "d9" // :status:200
                )},
                {"200+data+data", parseHex(
                        "01030000", // headers, length 3, section prefix
                        "d9", // :status:200
                        "000100", // data, 1 byte
                        "000100" // data, 1 byte
                )},
                {"200+data+trailers", parseHex(
                        "01030000", // headers, length 3, section prefix
                        "d9", // :status:200
                        "000100", // data, 1 byte
                        "01020000" // trailers, length 2, section prefix
                )},
                {"200+trailers", parseHex(
                        "01030000", // headers, length 3, section prefix
                        "d9", // :status:200
                        "01020000" // trailers, length 2, section prefix
                )},
        };
    }

    private static byte[] parseHex(String... strings) {
        var buffer = new StringBuilder();
        for (String string : strings) {
            buffer.append(string);
        }
        return HexFormat.of().parseHex(buffer.toString());
    }

    @ParameterizedTest
    @MethodSource("wellFormedResponses")
    void testWellFormedResponse(String desc, byte[] serverResponseBytes) throws Exception {
        var connectionTerminated = configureServerResponse(serverResponseBytes);
        try (var client = createClient()) {
            final HttpResponse<Void> response = client.send(REQUEST, BodyHandlers.discarding());
            assertEquals(200, response.statusCode());
            assertFalse(connectionTerminated.getAsBoolean(), "Expected the connection to be open");
        }
    }

    @ParameterizedTest
    @MethodSource("malformedResponsesPreservingConnection")
    void testMalformedResponsePreservingConnection(
            String desc,
            Class<? extends Exception> exceptionClass,
            byte[] serverResponseBytes) {
        var connectionTerminated = configureServerResponse(serverResponseBytes);
        try (var client = createClient()) {
            var exception = assertThrows(exceptionClass, () -> client.send(REQUEST, BodyHandlers.discarding()));
            LOGGER.log("Got expected exception for: " + desc, exception);
            assertFalse(connectionTerminated.getAsBoolean(), "Expected the connection to be open");
        }
    }

    @ParameterizedTest
    @MethodSource("malformedResponses")
    void testMalformedResponse(
            String desc,
            Class<? extends Exception> exceptionClass,
            byte[] serverResponseBytes) {
        configureServerResponse(serverResponseBytes);
        try (var client = createClient()) {
            var exception = assertThrows(exceptionClass, () -> client.send(REQUEST, BodyHandlers.discarding()));
            LOGGER.log("Got expected exception for: " + desc, exception);
        }
    }

    private static HttpClient createClient() {
        return HttpServerAdapters.createClientBuilderForH3()
                .proxy(NO_PROXY)
                .version(Version.HTTP_3)
                .sslContext(SSL_CONTEXT)
                .build();
    }

    private static BooleanSupplier configureServerResponse(byte[] serverResponseBytes) {
        var connectionTerminated = new AtomicBoolean();
        SERVER.addHandler((c, s)-> {
            try (OutputStream outputStream = s.outputStream()) {
                outputStream.write(serverResponseBytes);
            }
            c.futureTerminationCause().handle((_, _) -> {
                connectionTerminated.set(true);
                return true;
            });
        });
        return connectionTerminated::get;
    }

}
