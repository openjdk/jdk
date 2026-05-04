/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package doccheckutils.checkers;

import doccheckutils.HtmlChecker;
import doccheckutils.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the DocType declared at the head of an HTML file.
 *
 * @see <a href="https://www.w3.org/TR/html5/syntax.html#syntax-doctype">
 * W3C HTML5 8.1.1 The DOCTYPE</a>
 */
public class DocTypeChecker implements HtmlChecker {
    private final Log log;
    private final Map<String, Integer> counts = new HashMap<>();
    private int html5;
    private int html5_legacy;
    private int xml;
    private int other;

    private Path path;

    public DocTypeChecker() {
        log = new Log();
    }

    @Override
    public void startFile(Path path) {
        this.path = path;
    }

    @Override
    public void endFile() {
    }

    @Override
    public void xml(int line, Map<String, String> attrs) {
        xml++;
    }

    @Override
    public void docType(int line, String docType) {
        if (docType.equalsIgnoreCase("doctype html")) {
            html5++;
        } else {
            Pattern p = Pattern.compile("(?i)doctype"
                    + "\\s+html"
                    + "\\s+([a-z]+)"
                    + "\\s+(?:\"([^\"]+)\"|'([^']+)')"
                    + "(?:\\s+(?:\"([^\"]+)\"|'([^']+)'))?"
                    + "\\s*");
            Matcher m = p.matcher(docType);
            if (m.matches()) {
                // See http://www.w3.org/tr/html52/syntax.html#the-doctype
                if (m.group(1).equalsIgnoreCase("system")
                        && m.group(2).equals("about:legacy-compat")) {
                    html5_legacy++;
                } else {
                    String version = m.group(2);
                    List<String> allowedVersions = List.of(
                            "-//W3C//DTD XHTML 1.0 Strict//EN"
                    );
                    if (allowedVersions.stream().noneMatch(v -> v.equals(version))) {
                        log.log(path, line, "unexpected doctype: " + version);
                    }
                    counts.put(version, counts.getOrDefault(version, 0) + 1);
                }
            } else {
                log.log(path, line, "doctype not recognized: " + docType);
                other++;
            }
        }
    }

    @Override
    public void startElement(int line, String name, Map<String, String> attrs, boolean selfClosing) {
    }

    @Override
    public void endElement(int line, String name) {
    }

    @Override
    public void report() {
        log.log("DocType Report");
        if (xml > 0) {
            log.log("%6d: XHTML%n", xml);
        }
        if (html5 > 0) {
            log.log("%6d: HTML5%n", html5);
        }
        if (html5_legacy > 0) {
            log.log("%6d: HTML5 (legacy)%n", html5_legacy);
        }

        Map<Integer, Set<String>> sortedCounts = new TreeMap<>(Comparator.reverseOrder());

        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String s = e.getKey();
            Integer n = e.getValue();
            Set<String> set = sortedCounts.computeIfAbsent(n, k -> new TreeSet<>());
            set.add(s);
        }

        for (Map.Entry<Integer, Set<String>> e : sortedCounts.entrySet()) {
            for (String p : e.getValue()) {
                log.log("%6d: %s%n", e.getKey(), p);
            }
        }

        if (other > 0) {
            log.log("%6d: other/unrecognized%n", other);
        }

        for (var line : log.getErrors()) {
            System.err.println(line);
        }
    }

    @Override
    public boolean isOK() {
        return counts.isEmpty() && (other == 0);
    }

    @Override
    public void close() {
        if (!isOK()) {
            report();
            throw new RuntimeException("Found HTML files with missing doctype declaration");
        }
    }
}
