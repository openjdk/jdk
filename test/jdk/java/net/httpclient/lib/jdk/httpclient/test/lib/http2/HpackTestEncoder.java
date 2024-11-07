/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.httpclient.test.lib.http2;

import java.util.function.*;

import jdk.internal.net.http.hpack.Encoder;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static jdk.internal.net.http.hpack.HPACK.Logger.Level.EXTRA;
import static jdk.internal.net.http.hpack.HPACK.Logger.Level.NORMAL;

public class HpackTestEncoder extends Encoder {

    public HpackTestEncoder(int maxCapacity) {
        super(maxCapacity);
    }

    /**
     * Sets up the given header {@code (name, value)} with possibly sensitive
     * value.
     *
     * <p> If the {@code value} is sensitive (think security, secrecy, etc.)
     * this encoder will compress it using a special representation
     * (see <a href="https://tools.ietf.org/html/rfc7541#section-6.2.3">6.2.3.  Literal Header Field Never Indexed</a>).
     *
     * <p> Fixates {@code name} and {@code value} for the duration of encoding.
     *
     * @param name
     *         the name
     * @param value
     *         the value
     * @param sensitive
     *         whether or not the value is sensitive
     *
     * @throws NullPointerException
     *         if any of the arguments are {@code null}
     * @throws IllegalStateException
     *         if the encoder hasn't fully encoded the previous header, or
     *         hasn't yet started to encode it
     * @see #header(CharSequence, CharSequence)
     * @see DecodingCallback#onDecoded(CharSequence, CharSequence, boolean)
     */
    public void header(CharSequence name,
                       CharSequence value,
                       boolean sensitive) throws IllegalStateException {
        if (sensitive || getMaxCapacity() == 0) {
            super.header(name, value, true);
        } else {
            header(name, value, false, (n,v) -> false);
        }
    }
    /**
     * Sets up the given header {@code (name, value)} with possibly sensitive
     * value.
     *
     * <p> If the {@code value} is sensitive (think security, secrecy, etc.)
     * this encoder will compress it using a special representation
     * (see <a href="https://tools.ietf.org/html/rfc7541#section-6.2.3">6.2.3.  Literal Header Field Never Indexed</a>).
     *
     * <p> Fixates {@code name} and {@code value} for the duration of encoding.
     *
     * @param name
     *         the name
     * @param value
     *         the value
     * @param insertionPolicy
     *         a bipredicate to indicate whether a name value pair
     *         should be added to the dynamic table
     *
     * @throws NullPointerException
     *         if any of the arguments are {@code null}
     * @throws IllegalStateException
     *         if the encoder hasn't fully encoded the previous header, or
     *         hasn't yet started to encode it
     * @see #header(CharSequence, CharSequence)
     * @see DecodingCallback#onDecoded(CharSequence, CharSequence, boolean)
     */
    public void header(CharSequence name,
                       CharSequence value,
                       BiPredicate<CharSequence, CharSequence> insertionPolicy)
            throws IllegalStateException {
        header(name, value, false, insertionPolicy);
    }

    /**
     * Sets up the given header {@code (name, value)} with possibly sensitive
     * value.
     *
     * <p> If the {@code value} is sensitive (think security, secrecy, etc.)
     * this encoder will compress it using a special representation
     * (see <a href="https://tools.ietf.org/html/rfc7541#section-6.2.3">
     *     6.2.3.  Literal Header Field Never Indexed</a>).
     *
     * <p> Fixates {@code name} and {@code value} for the duration of encoding.
     *
     * @param name
     *         the name
     * @param value
     *         the value
     * @param sensitive
     *         whether or not the value is sensitive
     * @param insertionPolicy
     *         a bipredicate to indicate whether a name value pair
     *         should be added to the dynamic table
     *
     * @throws NullPointerException
     *         if any of the arguments are {@code null}
     * @throws IllegalStateException
     *         if the encoder hasn't fully encoded the previous header, or
     *         hasn't yet started to encode it
     * @see #header(CharSequence, CharSequence)
     * @see DecodingCallback#onDecoded(CharSequence, CharSequence, boolean)
     */
    public void header(CharSequence name,
                       CharSequence value,
                       boolean sensitive,
                       BiPredicate<CharSequence, CharSequence> insertionPolicy)
            throws IllegalStateException {
        if (sensitive == true || getMaxCapacity() == 0 || !insertionPolicy.test(name, value)) {
            super.header(name, value, sensitive);
            return;
        }
        var logger = logger();
        // Arguably a good balance between complexity of implementation and
        // efficiency of encoding
        requireNonNull(name, "name");
        requireNonNull(value, "value");
        var t = getHeaderTable();
        int index = tableIndexOf(name, value);
        if (logger.isLoggable(NORMAL)) {
            logger.log(NORMAL, () -> format("encoding with indexing ('%s', '%s'): index:%s",
                    name, value, index));
        }
        if (index > 0) {
            indexed(index);
        } else {
            boolean huffmanValue = isHuffmanBetterFor(value);
            if (index < 0) {
                literalWithIndexing(-index, value, huffmanValue);
            } else {
                boolean huffmanName = isHuffmanBetterFor(name);
                literalWithIndexing(name, huffmanName, value, huffmanValue);
            }
        }
    }

    protected int calculateCapacity(int maxCapacity) {
        return maxCapacity;
    }

}
