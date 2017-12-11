/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

/**
 * A read-only view of a set of HTTP headers.
 * {@Incubating}
 *
 * <p> The methods of this class ( that accept a String header name ), and the
 * Map returned by the {@linkplain #map() map} method, operate without regard to
 * case when retrieving the header value.
 *
 * <p> HttpHeaders instances are immutable.
 *
 * @since 9
 */
public abstract class HttpHeaders {

    /**
     * Creates an HttpHeaders.
     */
    protected HttpHeaders() {}

    /**
     * Returns an {@link Optional} containing the first value of the given named
     * (and possibly multi-valued) header. If the header is not present, then
     * the returned {@code Optional} is empty.
     *
     * @implSpec
     * The default implementation invokes
     * {@code allValues(name).stream().findFirst()}
     *
     * @param name the header name
     * @return an {@code Optional<String>} for the first named value
     */
    public Optional<String> firstValue(String name) {
        return allValues(name).stream().findFirst();
    }

    /**
     * Returns an {@link OptionalLong} containing the first value of the
     * named header field. If the header is not present, then the Optional is
     * empty. If the header is present but contains a value that does not parse
     * as a {@code Long} value, then an exception is thrown.
     *
     * @implSpec
     * The default implementation invokes
     * {@code allValues(name).stream().mapToLong(Long::valueOf).findFirst()}
     *
     * @param name the header name
     * @return  an {@code OptionalLong}
     * @throws NumberFormatException if a value is found, but does not parse as
     *                               a Long
     */
    public OptionalLong firstValueAsLong(String name) {
        return allValues(name).stream().mapToLong(Long::valueOf).findFirst();
    }

    /**
     * Returns an unmodifiable List of all of the values of the given named
     * header. Always returns a List, which may be empty if the header is not
     * present.
     *
     * @implSpec
     * The default implementation invokes, among other things, the
     * {@code map().get(name)} to retrieve the list of header values.
     *
     * @param name the header name
     * @return a List of String values
     */
    public List<String> allValues(String name) {
        requireNonNull(name);
        List<String> values = map().get(name);
        // Making unmodifiable list out of empty in order to make a list which
        // throws UOE unconditionally
        return values != null ? values : unmodifiableList(emptyList());
    }

    /**
     * Returns an unmodifiable multi Map view of this HttpHeaders.
     *
     * @return the Map
     */
    public abstract Map<String, List<String>> map();

    /**
     * Tests this HTTP headers instance for equality with the given object.
     *
     * <p> If the given object is not an {@code HttpHeaders} then this
     * method returns {@code false}. Two HTTP headers are equal if each
     * of their corresponding {@linkplain #map() maps} are equal.
     *
     * <p> This method satisfies the general contract of the {@link
     * Object#equals(Object) Object.equals} method.
     *
     * @param obj the object to which this object is to be compared
     * @return {@code true} if, and only if, the given object is an {@code
     *         HttpHeaders} that is equal to this HTTP headers
     */
    public final boolean equals(Object obj) {
        if (!(obj instanceof HttpHeaders))
            return false;
        HttpHeaders that = (HttpHeaders)obj;
        return this.map().equals(that.map());
    }

    /**
     * Computes a hash code for this HTTP headers instance.
     *
     * <p> The hash code is based upon the components of the HTTP headers
     * {@linkplain #map() map}, and satisfies the general contract of the
     * {@link Object#hashCode Object.hashCode} method.
     *
     * @return the hash-code value for this HTTP headers
     */
    public final int hashCode() {
        return map().hashCode();
    }

    /**
     * Returns this HTTP headers as a string.
     *
     * @return a string describing the HTTP headers
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString()).append(" ");
        sb.append(map());
        sb.append(" }");
        return sb.toString();
    }
}
