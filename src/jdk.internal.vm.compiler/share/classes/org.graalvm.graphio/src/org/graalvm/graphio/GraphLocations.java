/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.graphio;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Provides source location information about compiled code. This interface is an extension of
 * {@link GraphElements} - by default the elements work with classical {@link StackTraceElement}.
 * Should the default behavior not be sufficient, feel free to implement the additional operations
 * available in this interface and register your implementation when {@link GraphOutput.Builder
 * building} the {@link GraphOutput} instance.
 *
 * @param <M> type representing methods
 * @param <P> type representing source code location
 * @param <L> represeting {@link StackTraceElement stack element} location
 *
 * @since 0.33 part of GraalVM 0.33
 */
public interface GraphLocations<M, P, L> {
    /**
     * Stack trace element for a method, index and position. Returns all applicable source locations
     * for given code position. Each provided location is expected to represent location in a
     * different {@link #locationLanguage(java.lang.Object) language}.
     *
     * @param method the method
     * @param bci the index
     * @param pos the position
     * @return elements representing location for all language strata
     */
    Iterable<L> methodLocation(M method, int bci, P pos);

    /**
     * Identification of the language. Each location can point to a source in a different language -
     * e.g. each location can have multiple <em>strata</em>.
     *
     * @param location the location
     * @return id of the language/strata
     */
    String locationLanguage(L location);

    /**
     * The universal resource identification that contains the location.If the location can be found
     * in an assummably accessible resource, then use such resource identification. It is up to the
     * side processing the URI to load the content from the location. Protocols scheme {@code file},
     * {@code http}, or {@code https} are assumed to be accessible.
     * <p>
     * If the location is inside of a virtual source, or source which is unlikely to be accessible
     * outside of running program, then it may be better to encode the whole source into the
     * resource identifier. This can be done by using
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/Data_URIs">data
     * URIs</a> like:
     *
     * <pre>
     * data:text/javascript,alert('Vivat graphs!')
     * </pre>
     *
     * @param location the location
     * @return the file name for the given location or {@code null} if it is not known
     * @throws URISyntaxException yielding this exception aborts the graph dumping
     */
    URI locationURI(L location) throws URISyntaxException;

    /**
     * Line number of a location. The first line in the source file is one. Negative value means the
     * line location isn't available. In such situation one can provide an offset driven location
     * co-ordinates via {@link #locationOffsetStart(java.lang.Object)} and
     * {@link #locationOffsetEnd(java.lang.Object)} methods.
     *
     * @param location the location
     * @return the line number for given location, negative value means no line
     */
    int locationLineNumber(L location);

    /**
     * Offset of the location. In certain situations it is preferrable to specify offset rather than
     * {@link #locationLineNumber(java.lang.Object) line number} of a location in source. In such
     * case return the start offset from this method and end offset via
     * {@link #locationOffsetEnd(java.lang.Object)} method. Offsets are counted from {@code 0}.
     *
     * @param location the location
     * @return the starting offset of the location, negative value means no offset
     */
    int locationOffsetStart(L location);

    /**
     * Offset of the location. In certain situations it is preferrable to specify offset rather than
     * {@link #locationLineNumber(java.lang.Object) line number} of a location in source. In such
     * case return the start offset via {@link #locationOffsetStart(java.lang.Object)} method and
     * end from this method. Offsets are counted from {@code 0}.
     *
     * @param location the location
     * @return the end offset (exclusive) of the location, negative value means no offset
     */
    int locationOffsetEnd(L location);
}
