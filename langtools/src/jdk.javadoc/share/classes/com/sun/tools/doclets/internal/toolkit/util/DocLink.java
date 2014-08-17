/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.util;

/**
 * Abstraction for simple relative URIs, consisting of a path,
 * an optional query, and an optional fragment. DocLink objects can
 * be created by the constructors below or from a DocPath using the
 * convenience methods, {@link DocPath#fragment fragment} and
 * {@link DocPath#query query}.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 */
public class DocLink {
    final String path;
    final String query;
    final String fragment;

    /** Create a DocLink representing the URI {@code #fragment}. */
    public static DocLink fragment(String fragment) {
        return new DocLink((String) null, (String) null, fragment);
    }

    /** Create a DocLink representing the URI {@code path}. */
    public DocLink(DocPath path) {
        this(path.getPath(), null, null);
    }

    /**
     * Create a DocLink representing the URI {@code path?query#fragment}.
     * query and fragment may be null.
     */
    public DocLink(DocPath path, String query, String fragment) {
        this(path.getPath(), query, fragment);
    }

    /**
     * Create a DocLink representing the URI {@code path?query#fragment}.
     * Any of the component parts may be null.
     */
    public DocLink(String path, String query, String fragment) {
        this.path = path;
        this.query = query;
        this.fragment = fragment;
    }

    /**
     * Return the link in the form "path?query#fragment", omitting any empty
     * components.
     */
    @Override
    public String toString() {
        // common fast path
        if (path != null && isEmpty(query) && isEmpty(fragment))
            return path;

        StringBuilder sb = new StringBuilder();
        if (path != null)
            sb.append(path);
        if (!isEmpty(query))
            sb.append("?").append(query);
        if (!isEmpty(fragment))
            sb.append("#").append(fragment);
        return sb.toString();
    }

    private static boolean isEmpty(String s) {
        return (s == null) || s.isEmpty();
    }
}
