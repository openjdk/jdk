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

package jdk.incubator.http;

import sun.net.www.MessageHeader;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static java.lang.String.format;
import static jdk.incubator.http.internal.common.Utils.isValidName;
import static jdk.incubator.http.internal.common.Utils.isValidValue;
import static java.util.Objects.requireNonNull;

/*
 * Reads entire header block off channel, in blocking mode.
 * This class is not thread-safe.
 */
final class ResponseHeaders implements HttpHeaders {

    private static final char CR = '\r';
    private static final char LF = '\n';

    private final ImmutableHeaders delegate;

    /*
     * This constructor takes a connection from which the header block is read
     * and a buffer which may contain an initial portion of this header block.
     *
     * After the headers have been parsed (this constructor has returned) the
     * leftovers (i.e. data, if any, beyond the header block) are accessible
     * from this same buffer from its position to its limit.
     */
    ResponseHeaders(HttpConnection connection, ByteBuffer buffer) throws IOException {
        requireNonNull(connection);
        requireNonNull(buffer);
        InputStreamWrapper input = new InputStreamWrapper(connection, buffer);
        delegate = ImmutableHeaders.of(parse(input));
    }

    static final class InputStreamWrapper extends InputStream {
        final HttpConnection connection;
        final ByteBuffer buffer;
        int lastRead = -1; // last byte read from the buffer
        int consumed = 0; // number of bytes consumed.
        InputStreamWrapper(HttpConnection connection, ByteBuffer buffer) {
            super();
            this.connection = connection;
            this.buffer = buffer;
        }
        @Override
        public int read() throws IOException {
            if (!buffer.hasRemaining()) {
                buffer.clear();
                int n = connection.read(buffer);
                if (n == -1) {
                    return lastRead = -1;
                }
            }
            // don't let consumed become positive again if it overflowed
            // we just want to make sure that consumed == 1 really means
            // that only one byte was consumed.
            if (consumed >= 0) consumed++;
            return lastRead = buffer.get();
        }
    }

    private Map<String, List<String>> parse(InputStreamWrapper input)
         throws IOException
    {
        // The bulk of work is done by this time-proven class
        MessageHeader h = new MessageHeader();
        h.parseHeader(input);

        // When there are no headers (and therefore no body), the status line
        // will be followed by an empty CRLF line.
        // In that case MessageHeader.parseHeader() will consume the first
        // CR character and stop there. In this case we must consume the
        // remaining LF.
        if (input.consumed == 1 && CR == (char) input.lastRead) {
            // MessageHeader will not consume LF if the first character it
            // finds is CR. This only happens if there are no headers, and
            // only one byte will be consumed from the buffer. In this case
            // the next byte MUST be LF
            //System.err.println("Last character read is: " + (byte)lastRead);
            if (input.read() != LF) {
                throw new IOException("Unexpected byte sequence when no headers: "
                     + ((int)CR) + " " + input.lastRead
                     + "(" + ((int)CR) + " " + ((int)LF) + " expected)");
            }
        }

        Map<String, List<String>> rawHeaders = h.getHeaders();

        // Now some additional post-processing to adapt the results received
        // from MessageHeader to what is needed here
        Map<String, List<String>> cookedHeaders = new HashMap<>();
        for (Map.Entry<String, List<String>> e : rawHeaders.entrySet()) {
            String key = e.getKey();
            if (key == null) {
                throw new ProtocolException("Bad header-field");
            }
            if (!isValidName(key)) {
                throw new ProtocolException(format(
                        "Bad header-name: '%s'", key));
            }
            List<String> newValues = e.getValue();
            for (String v : newValues) {
                if (!isValidValue(v)) {
                    throw new ProtocolException(format(
                            "Bad header-value for header-name: '%s'", key));
                }
            }
            String k = key.toLowerCase(Locale.US);
            cookedHeaders.merge(k, newValues,
                    (v1, v2) -> {
                        if (v1 == null) {
                            ArrayList<String> newV = new ArrayList<>();
                            newV.addAll(v2);
                            return newV;
                        } else {
                            v1.addAll(v2);
                            return v1;
                        }
                    });
        }
        return cookedHeaders;
    }

    int getContentLength() throws IOException {
        return (int) firstValueAsLong("Content-Length").orElse(-1);
    }

    @Override
    public Optional<String> firstValue(String name) {
        return delegate.firstValue(name);
    }

    @Override
    public OptionalLong firstValueAsLong(String name) {
        return delegate.firstValueAsLong(name);
    }

    @Override
    public List<String> allValues(String name) {
        return delegate.allValues(name);
    }

    @Override
    public Map<String, List<String>> map() {
        return delegate.map();
    }
}
