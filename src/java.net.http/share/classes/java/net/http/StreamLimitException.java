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
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.net.http.HttpClient.Version;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.util.Objects;

/**
 * An exception raised when the limit imposed for stream creation on an
 * HTTP connection is reached, and the client is unable to create a new
 * stream.
 * <p>
 * A {@code StreamLimitException} may be raised when attempting to send
 * a new request on any {@linkplain #version()
 * protocol version} that supports multiplexing on a single connection. Both
 * {@linkplain HttpClient.Version#HTTP_2 HTTP/2} and {@linkplain
 * HttpClient.Version#HTTP_3 HTTP/3} allow multiplexing concurrent requests
 * to the same server on a single connection. Each request/response exchange
 * is carried over a single stream, as defined by the corresponding
 * protocol.
 * <p>
 * Whether and when a {@code  StreamLimitException} may be
 * relayed to the code initiating a request/response exchange is
 * implementation and protocol version dependent.
 *
 * @see HttpClient#send(HttpRequest, BodyHandler)
 * @see HttpClient#sendAsync(HttpRequest, BodyHandler)
 * @see HttpClient#sendAsync(HttpRequest, BodyHandler, PushPromiseHandler)
 *
 * @since 26
 */
public final class StreamLimitException extends IOException {

    @java.io.Serial
    private static final long serialVersionUID = 2614981180406031159L;

    /**
     * The version of the HTTP protocol on which the stream limit exception occurred.
     * Must not be null.
     * @serial
     */
    private final Version version;

    /**
     * Creates a new {@code StreamLimitException}
     * @param version the version of the protocol on which the stream limit exception
     *                occurred. Must not be null.
     * @param message the detailed exception message, which can be null.
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

    /**
     * Restores the state of a {@code StreamLimitException} from the stream
     * @param in the input stream
     * @throws IOException if the class of a serialized object could not be found.
     * @throws ClassNotFoundException if an I/O error occurs.
     * @throws InvalidObjectException if {@code version} is null.
     */
    @java.io.Serial
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (version == null) {
            throw new InvalidObjectException("version must not be null");
        }
    }
}
