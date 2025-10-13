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
package jdk.internal.net.http.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ProtocolException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/*
 * Checks RFC 9113 rules (relaxed) compliance regarding pseudo-headers.
 */
public class ValidatingHeadersConsumer {

    private final Context context;

    public ValidatingHeadersConsumer(Context context) {
        this.context = Objects.requireNonNull(context);
    }

    public enum Context {
        REQUEST,
        RESPONSE,
    }

    // Map of permitted pseudo headers in requests and responses
    private static final Map<String, Context> PSEUDO_HEADERS =
            Map.of(":authority", Context.REQUEST,
                    ":method", Context.REQUEST,
                    ":path", Context.REQUEST,
                    ":scheme", Context.REQUEST,
                    ":status", Context.RESPONSE);

    // connection-specific, prohibited by RFC 9113 section 8.2.2
    private static final Set<String> PROHIBITED_HEADERS =
            Set.of("connection", "proxy-connection", "keep-alive",
                    "transfer-encoding", "upgrade");

    /** Used to check that if there are pseudo-headers, they go first */
    private boolean pseudoHeadersEnded;

    /**
     * Called when END_HEADERS was received. This consumer may be invoked
     * again after reset() is called, but for a whole new set of headers.
     */
    public void reset() {
        pseudoHeadersEnded = false;
    }

    /**
     * Called when a header field (name, value) pair has been decoded
     * @param name    the decoded name
     * @param value   the decoded value
     * @throws UncheckedIOException if the name or value are illegal
     */
    public void onDecoded(CharSequence name, CharSequence value)
            throws UncheckedIOException
    {
        String n = name.toString();
        if (n.startsWith(":")) {
            if (pseudoHeadersEnded) {
                throw newException("Unexpected pseudo-header '%s'", n);
            } else {
                Context expectedContext = PSEUDO_HEADERS.get(n);
                if (expectedContext == null) {
                    throw newException("Unknown pseudo-header '%s'", n);
                } else if (expectedContext != context) {
                    throw newException("Pseudo-header '%s' is not valid in context " + context, n);
                }
            }
        } else {
            pseudoHeadersEnded = true;
            // Check for prohibited connection-specific headers.
            // Some servers echo request headers in push promises.
            // If the request was a HTTP/1.1 upgrade, it included some prohibited headers.
            // For compatibility, we ignore prohibited headers in push promises.
            if (context != Context.REQUEST) {
                if (PROHIBITED_HEADERS.contains(n)) {
                    throw newException("Prohibited header name '%s'", n);
                }
            }
            // RFC-9113, section 8.2.1 for HTTP/2 and RFC-9114, section 4.2 state that
            // header name MUST be lowercase (and allowed characters)
            if (!Utils.isValidLowerCaseName(n)) {
                throw newException("Bad header name '%s'", n);
            }
        }
        String v = value.toString();
        if (!Utils.isValidValue(v)) {
            throw newException("Bad header value '%s'", v);
        }
    }

    protected String formatMessage(String message, String header) {
        return String.format(message, header);
    }

    protected UncheckedIOException newException(String message, String header)
    {
        return new UncheckedIOException(
                new ProtocolException(formatMessage(message, header)));
    }
}
