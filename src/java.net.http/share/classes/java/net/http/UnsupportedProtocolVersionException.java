/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.io.Serial;
import java.net.http.HttpClient.Builder;

/**
 * Thrown when the HTTP client doesn't support a particular HTTP version.
 * @apiNote
 * Typically, this exception may be thrown when attempting to
 * {@linkplain Builder#build() build} an {@link java.net.http.HttpClient}
 * configured to use {@linkplain java.net.http.HttpClient.Version#HTTP_3
 * HTTP version 3} by default, when the underlying {@link javax.net.ssl.SSLContext
 * SSLContext} implementation does not meet the requirements for supporting
 * the HttpClient's implementation of the underlying QUIC transport protocol.
 * @since 26
 */
public final class UnsupportedProtocolVersionException extends IOException {

    @Serial
    private static final long serialVersionUID = 981344214212332893L;

    /**
     * Constructs an {@code UnsupportedProtocolVersionException} with the given detail message.
     *
     * @param message The detail message; can be {@code null}
     */
    public UnsupportedProtocolVersionException(String message) {
        super(message);
    }
}
