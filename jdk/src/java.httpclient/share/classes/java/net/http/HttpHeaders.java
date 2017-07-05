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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A read-only view of a set of received HTTP headers.
 *
 * @since 9
 */
public interface HttpHeaders {

    /**
     * Returns an {@link java.util.Optional} containing the first value of the
     * given named (and possibly multi-valued) header. If the header is not
     * present, then the returned {@code Optional} is empty.
     *
     * @param name the header name
     * @return an {@code Optional<String>} for the first named value
     */
    public Optional<String> firstValue(String name);

    /**
     * Returns an {@link java.util.Optional} containing the first value of the
     * named header field as an {@literal Optional<Long>}. If the header is not
     * present, then the Optional is empty. If the header is present but
     * contains a value that does not parse as a {@code Long} value, then an
     * exception is thrown.
     *
     * @param name the header name
     * @return  an {@code Optional<Long>}
     * @throws NumberFormatException if a value is found, but does not parse as
     *                               a Long
     */
    public Optional<Long> firstValueAsLong(String name);

    /**
     * Returns an unmodifiable List of all of the values of the given named
     * header. Always returns a List, which may be empty if the header is not
     * present.
     *
     * @param name the header name
     * @return a List of String values
     */
    public List<String> allValues(String name);

    /**
     * Returns an unmodifiable multi Map view of this HttpHeaders. This
     * interface should only be used when it is required to iterate over the
     * entire set of headers.
     *
     * @return the Map
     */
    public Map<String,List<String>> map();
}
