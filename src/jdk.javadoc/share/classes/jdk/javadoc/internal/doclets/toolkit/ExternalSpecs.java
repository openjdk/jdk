/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class to manage an external file listing URLs and canonical titles for
 * external specifications.
 *
 * In the file, blank lines are lines beginning with {@code #} are ignored.
 * Otherwise, lines must be of the form:
 *
 * <pre>{@code
 *     URL title
 * }</pre>
 *
 * where <i>URL</i> is the URL for the external specification, and
 * <i>title</i> is the canonical title of the specification.
 *
 * <p>The URL may not contain a fragment ({@code #frag}) or query ({@code ?query}.
 * It may be absolute or relative. The URL should end with {@code /} if it
 * if the specification consists of a group of pages rooted at that URL.
 *
 * <p>The title must not be empty, plain text, and not contain any HTML or entities.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ExternalSpecs {

    /**
     * Reads a file containing a list of external specifications.
     *
     * @param file the file
     * @param messages used to report any errors while reading the content of the file
     * @return the contents of the file
     * @throws IOException if an error occurs while accessing the file
     */
    static ExternalSpecs read(Path file, Messages messages) throws IOException {
        ExternalSpecs sl = new ExternalSpecs();
        int lineNo = 0;
        for (String line : Files.readAllLines(file)) {
            lineNo++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int sep = line.indexOf(" ");
            if (sep < 0) {
                messages.error("doclet.err.specs.no.title", file, lineNo);
                continue;
            }
            try {
                URI uri = new URI(line.substring(0, sep));
                if (uri.getFragment() != null) {
                    messages.error("doclet.err.specs.uri.has.fragment", file, lineNo);
                    continue;
                }
                if (uri.getQuery() != null) {
                    messages.error("doclet.err.specs.uri.has.query", file, lineNo);
                    continue;
                }
                String title = line.substring(sep + 1).trim();
                if (title.isEmpty()) {
                    messages.error("doclet.err.specs.no.title", file, lineNo);
                    continue;
                }
                sl.addEntry(uri, title);
            } catch (URISyntaxException e) {
                messages.error("doclet.err.specs.bad.uri", file, lineNo, e.getMessage());
            }
        }

        sl.sortDirEntries();
        return sl;
    }

    static class Entry {
        final URI uri;
        final String title;

        Entry(URI uri, String title) {
            this.uri = uri;
            this.title = title;
        }

        boolean match(String s) {
            String u = uri.toString();
            if (u.endsWith("/")) {
                return s.startsWith(u) || s.equals(u.substring(u.length() - 1));
            } else {
                return s.equals(u);
            }
        }
    }

    /**
     * A map of the entries in the file whose URLs did not end with {@code /}.
     */
    private final Map<URI, String> fileEntries = new HashMap<>();

    /**
     * A multi-map of the entries in the file whose URLs did end with {@code /}.
     * The map is grouped by host name, to reduce the linear cost of searching
     * for the best match for a given URL.
     */
    private final Map<String, List<Entry>> dirEntries = new HashMap<>();

    /**
     * {@return the title of an external specification}
     *
     * The external specification is determined by looking up an exact
     * match for an entry in the file that did not end with {@code /},
     * or the longest match for an entry that did end with {@code /}.
     *
     * @param u the URL for the specification
     */
    public String getTitle(URI u) {
        if (u.getQuery() != null || u.getFragment() != null) {
            try {
                // rewrite the URI without the query or fragment
                u = new URI(u.getScheme(), u.getUserInfo(), u.getHost(), u.getPort(), u.getPath(), null, null);
            } catch (URISyntaxException e) {
                // should not happen; leave u unchanged
            }
        }
        String t = fileEntries.get(u);
        if (t != null) {
            return t;
        }
        List<Entry> l = dirEntries.get(u.getHost());
        if (l == null) {
            return null;
        }
        String s = u.toString();
        for (Entry e : l) {
            if (e.match(s)) {
                return e.title;
            }
        }
        return null;
    }

    /**
     * Adds a new entry to the collection.
     * Entries that do not end with {@code /} are put in a simple hash map for direct lookup.
     * Entries that end with {@code /} are put in a multi-map, indexed by host name.
     *
     * @param url   the URL
     * @param title the title
     */
    private void addEntry(URI url, String title) {
        Entry e = new Entry(url, title);
        if (!url.getPath().endsWith("/")) {
            fileEntries.put(url, title);
        } else {
            dirEntries.computeIfAbsent(url.getHost(), h -> new ArrayList<>()).add(e);
        }
    }

    /**
     * Sorts the directory entries first by length (longest first) and then by name.
     * The sort by length ensures that we find the longest match first in {@link #getTitle(URI)}.
     */
    private void sortDirEntries() {
        Comparator<Entry> c =
                Comparator.comparing((Entry e) -> e.uri.getPath().length()).reversed()
                        .thenComparing(e -> e.uri.toString());
        dirEntries.values().forEach(l -> l.sort(c));
    }
}
