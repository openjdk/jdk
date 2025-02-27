/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package java.net.http;

import java.io.IOException;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.util.Objects;

/**
 * An exception thrown when the limit imposed for stream creation on an
 * HTTP connection is reached.
 * <p>
 * A {@code StreamLimitException} may be raised on any {@linkplain #version()
 * protocol version} that supports multiplexing on a single connection. Both
 * {@linkplain HttpClient.Version#HTTP_2 HTTP/2} and {@linkplain
 * HttpClient.Version#HTTP_3 HTTP/3} allow multiplexing concurrent requests
 * to the same server on a single connection. Each request/response exchange
 * is carried over a single stream, as defined by the corresponding
 * protocol.
 * <p>
 * The HTTP/2 and HTTP/3 protocols provide a means for HTTP servers to control
 * how many streams a client is allowed to open on a particular connection. If an
 * application attempts to open too many streams, and the
 * server doesn't allow more streams to be opened, a {@code StreamLimitException}
 * may be raised. See
 * <a href="https://www.rfc-editor.org/rfc/rfc7540.html#section-5.1.2">
 *     RFC 7540: Hypertext Transfer Protocol Version 2 (HTTP/2)</a>
 * and
 * <a href="https://www.rfc-editor.org/info/rfc9114">RFC 9114: HTTP/3</a>
 * for more information on stream limits.
 * For the HTTP/3 protocol, the actual implementation of the limit is
 * delegated to the underlying
 * <a href="https://www.rfc-editor.org/rfc/rfc9000.html#section-4.6">
 *     QUIC Protocol</a>.
 * <p>
 * An {@link HttpClient} implementation may choose to implement different
 * strategies to deal with stream limits. For instance, an implementation may
 * choose to:
 * <ul>
 *     <li>
 *         immediately relay a {@code StreamLimitException} to the code that
 *         initiated the request, when the stream limit is reached, or
 *     </li>
 *     <li>
 *         wait for a {@linkplain HttpRequest.Builder#timeout(Duration) reasonable time} in
 *         the hope that more streams will become available, or
 *     </li>
 *     <li>
 *         retire the connection on which the limit was reached, and attempt
 *         to send the request on a new connection, up to a certain limit, or
 *     </li>
 *     <li>
 *         ...
 *     </li>
 * </ul>
 * <p>
 * As a consequence, whether and when a {@code  StreamLimitException} may be
 * relayed to the code initiating a request/response exchange is entirely
 * implementation and protocol version dependent.
 *
 * @spec https://www.rfc-editor.org/info/rfc7540
 *      RFC 7540: Hypertext Transfer Protocol Version 2 (HTTP/2)
 * @spec https://www.rfc-editor.org/info/rfc9114
 *      RFC 9114: HTTP/3
 * @spec https://www.rfc-editor.org/rfc/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 *
 * @since tbd
 */
public final class StreamLimitException extends IOException {

    @java.io.Serial
    private static final long serialVersionUID = 2614981180406031159L;

    /**
     * The version of the HTTP protocol on which the stream limit exception occurred
     * @serial
     */
    private final Version version;

    /**
     * Creates a new {@code StreamLimitException}
     * @param version the version of the protocol on which the stream limit exception occurred
     * @param message the detailed exception message
     */
    public StreamLimitException(final Version version, final String message) {
        super(message);
        this.version = Objects.requireNonNull(version);
    }

    /**
     * {@return the protocol version for which the exception was raised}
     */
    public final Version version() {
        return version;
    }
}
