/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * This is a high-level generic interface for decoding textual or binary format
 * into a cryptographic object. The generic {@code T} defines what class or
 * interface the class will restrict operations on.
 *
 * @param <T> the type parameter
 */
public interface Decoder<T> {

    /**
     * Decode data from a {@code String} and returning a cryptographic object
     * of type {@code T}.
     *
     * @param string a String object containing the encoded data
     * @return an object of type {@code T}.
     * @throws IOException on decoding or read failures.
     */
    T decode(String string) throws IOException;

    /**
     * Decode data from an {@code InputStream} and returning a cryptographic
     * object of type {@code T}.
     *
     * @param is InputStream to read the encoded data from.
     * @return an object of type {@code T}.
     * @throws IOException on decoding or read failures.
     */
    T decode(InputStream is) throws IOException;

    /**
     * Decode data from a {@code String} and returning a cryptographic object
     * of {@code Class<S>}.  If {@code tClass} is not appropriate for the data,
     * an IOException is thrown.
     *
     * @param <S> the type parameter that implements {@code T}
     * @param string a String object containing the encoded data
     * @param tClass a class that implements {@code T}
     * @return an object of type {@code T}.
     * @throws IOException on decoding or read failures.
     */
    <S extends T> S decode(String string, Class <S> tClass) throws IOException;

    /**
     *  Decode data from an {@code InputStream} and returning a cryptographic object
     *  of {@code Class<S>}.  If {@code tClass} is not appropriate for the data,
     *  an IOException is thrown.
     *
     * @param <S> the type parameter that implements {@code T}
     * @param is InputStream to read the encoded data from.
     * @param tClass a class that implements {@code T}.
     * @return an object of type {@code T}.
     * @throws IOException on decoding or read failures.
     */
    <S extends T> S decode(InputStream is, Class <S> tClass) throws IOException;
}
