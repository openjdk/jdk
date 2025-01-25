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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Checks the external links referenced in HTML files.
 */
public class ExtLinkChecker implements HtmlChecker, AutoCloseable {
    private static final Path testBasePath = Path.of(System.getProperty("test.src"));
    private static final Set<String> extLinks = new HashSet<>();

    private static final String currentVersion = String.valueOf(Runtime.version().feature());

    static {
        String input = null;
        try {
            input = Files.readString(testBasePath.getParent().resolve("ExtLinksJdk.txt"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        extLinks.addAll(input.lines()
                .filter(line -> !line.startsWith("#"))
                .map(line -> line.replaceAll("\\@\\@JAVASE_VERSION\\@\\@", currentVersion))
                .collect(Collectors.toUnmodifiableSet()));
    }

    private final Log log;
    private final Map<URI, Set<Path>> allURIs;
    private int badURIs;
    private Path currFile;

    public ExtLinkChecker() {
        this.log = new Log();
        allURIs = new TreeMap<>();
    }

    @Override
    public void startFile(Path path) {
        currFile = path.toAbsolutePath().normalize();
    }

    @Override
    public void endFile() {
    }

    @Override
    public void xml(int line, Map<String, String> attrs) {
    }

    @Override
    public void docType(int line, String doctype) {
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void startElement(int line, String name, Map<String, String> attrs, boolean selfClosing) {
        switch (name) {
            case "a":
            case "link":
                String href = attrs.get("href");
                if (href != null) {
                    foundReference(line, href);
                }
                break;
        }
    }

    @Override
    public void endElement(int line, String name) {
    }

    private void foundReference(int line, String ref) {
        try {
            String uriPath = ref;
            String fragment = null;

            // The checker runs into a problem with links that have more than one hash character.
            // You cannot create a URI unless the second hash is escaped.

            int firstHashIndex = ref.indexOf('#');
            int lastHashIndex = ref.lastIndexOf('#');
            if (firstHashIndex != -1 && firstHashIndex != lastHashIndex) {
                uriPath = ref.substring(0, firstHashIndex);
                fragment = ref.substring(firstHashIndex + 1).replace("#", "%23");
            } else if (firstHashIndex != -1) {
                uriPath = ref.substring(0, firstHashIndex);
                fragment = ref.substring(firstHashIndex + 1);
            }

            URI uri = new URI(uriPath);
            if (fragment != null) {
                uri = new URI(uri + "#" + fragment);
            }

            if (uri.isAbsolute()) {
                if (Objects.equals(uri.getScheme(), "javascript")) {
                    // ignore JavaScript URIs
                    return;
                }
                String rawFragment = uri.getRawFragment();
                URI noFrag = new URI(uri.toString().replaceAll("#\\Q" + rawFragment + "\\E$", ""));
                allURIs.computeIfAbsent(noFrag, _ -> new LinkedHashSet<>()).add(currFile);
            }
        } catch (URISyntaxException e) {
            log.log(currFile, line, "invalid URI: " + e);
        }
    }

    @Override
    public void report() {
        checkURIs();
    }

    @Override
    public boolean isOK() {
        return badURIs == 0;
    }

    @Override
    public void close() {
        report();
    }

    private void checkURIs() {
        System.err.println("ExtLinkChecker: checking external links");
        allURIs.forEach(this::checkURI);
        System.err.println("ExtLinkChecker: finished checking external links");
    }

    private void checkURI(URI uri, Set<Path> files) {
        try {
            switch (uri.getScheme()) {
                case "ftp":
                case "http":
                case "https":
                  isVettedLink(uri, files);
                    break;
                default:
                    warning(files, uri);
            }
        } catch (Throwable t) {
            badURIs++;
            error(files, uri, t);
        }
    }

    private void isVettedLink(URI uri, Set<Path> files) {
        if (!extLinks.contains(uri.toString())) {
            System.err.println(MessageFormat.format("""
                    The external link {0} needs to be added to the whitelist test/docs/jdk/javadoc/doccheck/ExtLinksJdk.txt in order to be checked regularly\s
                    The link is present in:
                        {1}\n
                    """, uri, files.stream().map(Path::toString).collect(Collectors.joining("\n    "))));
        }
    }

    private void warning(Set<Path> files, Object... args) {
        Iterator<Path> iter = files.iterator();
        Path first = iter.next();
        log.log(String.valueOf(first), "URI not supported: %s", args);
        reportAlsoFoundIn(iter);
    }

    private void error(Set<Path> files, Object... args) {
        Iterator<Path> iter = files.iterator();
        Path first = iter.next();
        log.log(String.valueOf(first), "Exception accessing uri: %s%n    [%s]", args);
        reportAlsoFoundIn(iter);
    }

    private void reportAlsoFoundIn(Iterator<Path> iter) {
        int MAX_EXTRA = 10;
        int n = 0;
        while (iter.hasNext()) {
            log.log("    Also found in %s", log.relativize(iter.next()));
            if (n++ == MAX_EXTRA) {
                int rest = 0;
                while (iter.hasNext()) {
                    iter.next();
                    rest++;
                }
                log.log("    ... and %d more", rest);
            }
        }
    }
}
