/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * An exception used to signal the opening handshake failed.
 *
 * @since 9
 */
public final class WebSocketHandshakeException extends Exception {

    private static final long serialVersionUID = 1L;
    private final transient HttpResponse response;

    WebSocketHandshakeException(HttpResponse response) {
        this(null, response);
    }

    WebSocketHandshakeException(String message, HttpResponse response) {
        super(statusCodeOrFullMessage(message, response));
        this.response = response;
    }

    /**
     * // FIXME: terrible toString (+ not always status should be displayed I guess)
     */
    private static String statusCodeOrFullMessage(String m, HttpResponse response) {
        return (m == null || m.isEmpty())
                ? String.valueOf(response.statusCode())
                : response.statusCode() + ": " + m;
    }

    /**
     * Returns a HTTP response from the server.
     *
     * <p> The value may be unavailable ({@code null}) if this exception has
     * been serialized and then read back in.
     *
     * @return server response
     */
    public HttpResponse getResponse() {
        return response;
    }
}
