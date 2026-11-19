/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.qpack;

import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.qpack.readers.HeaderFrameReader;

import java.nio.ByteBuffer;

/**
 * Delivers results of the {@link Decoder#decodeHeader(ByteBuffer, boolean, HeaderFrameReader)}
 * decoding operation.
 *
 * <p> Methods of the callback are never called by a decoder with any of the
 * arguments being {@code null}.
 *
 * @apiNote
 *
 * <p> The callback provides methods for all possible
 * <a href="https://www.rfc-editor.org/rfc/rfc9204.html#name-field-line-representations">
 * field line representations</a>.
 *
 * <p> Names and values are {@link CharSequence}s rather than {@link String}s in
 * order to allow users to decide whether they need to create objects. A
 * {@code CharSequence} might be used in-place, for example, to be appended to
 * an {@link Appendable} (e.g. {@link StringBuilder}) and then discarded.
 *
 * <p> That said, if a passed {@code CharSequence} needs to outlast the method
 * call, it needs to be copied.
 *
 */
public interface DecodingCallback {

    /**
     * A method the more specific methods of the callback forward their calls
     * to.
     *
     * @param name
     *         header name
     * @param value
     *         header value
     */
    void onDecoded(CharSequence name, CharSequence value);

    /**
     * A header fields decoding is completed.
     */
    void onComplete();

    /**
     * A connection-level error observed during the decoding process.
     *
     * @param throwable  a {@code Throwable} instance
     * @param http3Error a HTTP3 error code
     */
    void onConnectionError(Throwable throwable, Http3Error http3Error);

    /**
     * A stream-level error observed during the decoding process.
     *
     * @param throwable  a {@code Throwable} instance
     * @param http3Error a HTTP3 error code
     */
    default void onStreamError(Throwable throwable, Http3Error http3Error) {
        onConnectionError(throwable, http3Error);
    }

    /**
     * Reports if {@linkplain #onConnectionError(Throwable, Http3Error) a connection}
     * or {@linkplain #onStreamError(Throwable, Http3Error) a stream} error has been
     * observed during the decoding process
     * @return true - if error was observed; false - otherwise
     */
    default boolean hasError() {
        return false;
    }

    /**
     * Returns request/response stream id or push stream id associated with a decoding callback.
     */
    long streamId();

    /**
     * A more finer-grained version of {@link #onDecoded(CharSequence,
     * CharSequence)} that also reports on value sensitivity.
     *
     * <p> Value sensitivity must be considered, for example, when implementing
     * an intermediary. A {@code value} is sensitive if it was represented as <a
     * href="https://www.rfc-editor.org/rfc/rfc9204.html#section-7.1.3-3">Literal Header
     * Field Never Indexed</a>.
     *
     * @implSpec
     *
     * <p> The default implementation invokes {@code onDecoded(name, value)}.
     *
     * @param name
     *         header name
     * @param value
     *         header value
     * @param sensitive
     *         whether the value is sensitive
     */
    default void onDecoded(CharSequence name,
                           CharSequence value,
                           boolean sensitive) {
        onDecoded(name, value);
    }

    /**
     * An <a href="https://www.rfc-editor.org/rfc/rfc9204.html#section-4.5.2">Indexed
     * Field Line</a> decoded.
     *
     * @implSpec
     *
     * <p> The default implementation invokes
     * {@code onDecoded(name, value, false)}.
     *
     * @param index
     *         index of a name/value pair in static or dynamic table
     * @param name
     *         header name
     * @param value
     *         header value
     */
    default void onIndexed(long index, CharSequence name, CharSequence value) {
        onDecoded(name, value, false);
    }

    /**
     * A <a href="https://www.rfc-editor.org/rfc/rfc9204.html#section-4.5.4">Literal
     * Field Line with Name Reference</a> decoded, where a {@code name} was
     * referred by an {@code index}.
     *
     * @implSpec
     *
     * <p> The default implementation invokes
     * {@code onDecoded(name, value, false)}.
     *
     * @param index
     *         index of an entry in the table
     * @param value
     *         header value
     * @param valueHuffman
     *         if the {@code value} was Huffman encoded
     * @param hideIntermediary
     *         if the header field should be written to intermediary nodes
     */
    default void onLiteralWithNameReference(long index,
                           CharSequence name,
                           CharSequence value,
                           boolean valueHuffman,
                           boolean hideIntermediary) {
        onDecoded(name, value, hideIntermediary);
    }

    /**
     * A <a href="https://www.rfc-editor.org/rfc/rfc9204.html#section-4.5.6">Literal Field
     * Line with Literal Name</a> decoded, where both a {@code name} and a {@code value}
     * were literal.
     *
     * @implSpec
     *
     * <p> The default implementation invokes
     * {@code onDecoded(name, value, false)}.
     *
     * @param name
     *         header name
     * @param nameHuffman
     *         if the {@code name} was Huffman encoded
     * @param value
     *         header value
     * @param valueHuffman
     *         if the {@code value} was Huffman encoded
     */
    default void onLiteralWithLiteralName(CharSequence name, boolean nameHuffman,
                                          CharSequence value, boolean valueHuffman,
                                          boolean hideIntermediary) {
        onDecoded(name, value, hideIntermediary);
    }
}
