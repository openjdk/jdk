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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExternalSpecs {

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
    }

    private final Map<URI, String> fileEntries = new HashMap<>();
    private final Map<String, List<Entry>> dirEntries = new HashMap<>();

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
            if (s.startsWith(e.uri.toString())) {
                return e.title;
            }
        }
        return null;
    }

    private void addEntry(URI url, String title) {
        Entry e = new Entry(url, title);
        if (!url.getPath().endsWith("/")) {
            fileEntries.put(url, title);
        } else {
            dirEntries.computeIfAbsent(url.getHost(), h -> new ArrayList<>()).add(e);
        }
    }

    private void sortDirEntries() {
        Comparator<Entry> c =
                Comparator.comparing((Entry e) -> e.uri.getPath().length()).reversed()
                        .thenComparing(e -> e.uri.toString());
        dirEntries.values().forEach(l -> Collections.sort(l, c));
    }
}
