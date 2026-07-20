/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;

/**
 * Checks the links defined by and referenced in HTML files.
 */
public class LinkChecker implements HtmlChecker {

    private final Log log;
    private final Map<Path, IDTable> allFiles;
    private final Map<URI, IDTable> allURIs;
    // left for debugging
    private final boolean checkInwardReferencesOnly = false;
    private int files;
    private int links;
    private int duplicateIds;
    private int missingFiles;
    private int missingIds;
    private int badSchemes;
    private Path currFile;
    private IDTable currTable;
    private boolean html5;
    public LinkChecker() {
        this.log = new Log();
        allFiles = new HashMap<>();
        allURIs = new HashMap<>();
    }

    public void setBaseDir(Path dir) {
        log.setBaseDirectory(dir);
    }

    @Override
    public void startFile(Path path) {
        currFile = path.toAbsolutePath().normalize();
        currTable = allFiles.computeIfAbsent(currFile, p -> new IDTable(log.relativize(p)));
        html5 = false;
        files++;
    }

    @Override
    public void endFile() {
        currTable.check();
    }


    //unused
    public List<Path> getUncheckedFiles() {
        return allFiles.entrySet().stream()
                .filter(e -> !e.getValue().checked
                        && e.getKey().toString().endsWith(".html")
                        && Files.exists(e.getKey()))
                .map(Map.Entry::getKey)
                .toList();
    }

    public List<Path> getMissingFiles() {
        return allFiles.keySet().stream()
                .filter(idTable -> !Files.exists(idTable)).toList();
    }

    @Override
    public void xml(int line, Map<String, String> attrs) {
    }

    @Override
    public void docType(int line, String doctype) {
        html5 = doctype.matches("(?i)<\\?doctype\\s+html>");
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void startElement(int line, String name, Map<String, String> attrs, boolean selfClosing) {
        switch (name) {
            case "a":
                String nameAttr = html5 ? null : attrs.get("name");
                if (nameAttr != null) {
                    foundAnchor(line, nameAttr);
                }
                // fallthrough
            case "link":
                String href = attrs.get("href");
                if (href != null && !checkInwardReferencesOnly) {
                    foundReference(line, href);
                }
                break;
        }

        String idAttr = attrs.get("id");
        if (idAttr != null) {
            foundAnchor(line, idAttr);
        }
    }

    @Override
    public void endElement(int line, String name) {
    }

    @Override
    public void content(int line, String content) {
        HtmlChecker.super.content(line, content);
    }

    @Override
    public void report() {
        List<Path> pathList = getMissingFiles();
        log.log("");
        log.log("Link Checker Report");

        if (!pathList.isEmpty()) {
            log.log("");
            log.log("Missing files: (" + pathList.size() + ")");
            pathList.stream()
                    .sorted()
                    .forEach(this::reportMissingFile);
        }

        int anchors = 0;
        for (IDTable t : allFiles.values()) {
            anchors += (int) t.map.values().stream()
                    .filter(e -> !e.getReferences().isEmpty())
                    .count();
        }
        for (IDTable t : allURIs.values()) {
            anchors += (int) t.map.values().stream()
                    .filter(e -> !e.references.isEmpty())
                    .count();
        }

        log.log("Checked " + files + " files.");
        log.log("Found " + links + " references to " + anchors + " anchors "
                + "in " + allFiles.size() + " files and " + allURIs.size() + " other URIs.");
        if (!pathList.isEmpty()) {
            log.log("%6d missing files", pathList.size());
        }
        if (duplicateIds > 0) {
            log.log("%6d duplicate ids", duplicateIds);

        }
        if (missingIds > 0) {
            log.log("%6d missing ids", missingIds);

        }

        Map<String, Integer> hostCounts = new TreeMap<>(new HostComparator());
        for (URI uri : allURIs.keySet()) {
            String host = uri.getHost();
            if (host != null) {
                hostCounts.put(host, hostCounts.computeIfAbsent(host, h -> 0) + 1);
            }
        }

//        if (hostCounts.size() > 0) {
//            log.log("");
//            log.log("Hosts");
//            hostCounts.forEach((h, n) -> log.log("%6d %s", n, h));
//        }


        for (String message : log.getErrors()) {
            System.err.println(message);
        }

    }

    private void reportMissingFile(Path file) {
        log.log(log.relativize(file).toString());
        IDTable table = allFiles.get(file);
        Set<Path> refs = new TreeSet<>();
        for (IDInfo id : table.map.values()) {
            if (id.references != null) {
                for (Position ref : id.references) {
                    refs.add(ref.path);
                }
            }
        }
        int n = 0;
        int MAX_REFS = 10;
        for (Path ref : refs) {
            log.log("    in " + log.relativize(ref));
            if (++n == MAX_REFS) {
                log.log("    ... and %d more", refs.size() - n);
                break;
            }
        }
        missingFiles++;
    }

    @Override
    public boolean isOK() {
        return log.noErrors() && (missingFiles == 0);
    }

    @Override
    public void close() {
        if (!log.noErrors()) {
            report();
            throw new RuntimeException("LinkChecker encountered errors; see log above.");
        }
    }

    private void foundAnchor(int line, String name) {
        currTable.addID(line, name);
    }

    private void foundReference(int line, String ref) {
        links++;
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
                foundReference(line, uri);
            } else {
                Path p;
                String resolvedUriPath = uri.getPath();
                if (resolvedUriPath == null || resolvedUriPath.isEmpty()) {
                    p = currFile;
                } else {
                    p = currFile.getParent().resolve(resolvedUriPath).normalize();
                }

                if (!Files.exists(p)) {
                    log.log(currFile, line, "missing file reference: " + log.relativize(p));
                    return;
                }

                if (fragment != null && !fragment.isEmpty()) {
                    foundReference(line, p, fragment);
                }
            }
        } catch (URISyntaxException e) {
            System.err.println("Failed to create URI: " + ref);
            log.log(currFile, line, "invalid URI: " + e);
        }
    }


    private void foundReference(int line, Path p, String fragment) {
        IDTable t = allFiles.computeIfAbsent(p, key -> new IDTable(log.relativize(key)));
        t.addReference(fragment, currFile, line);
    }

    private void foundReference(int line, URI uri) {
        if (!isSchemeOK(uri.getScheme()) && !checkInwardReferencesOnly) {
            log.log(currFile, line, "bad scheme in URI");
            badSchemes++;
        }

        String fragment = uri.getRawFragment();
        if (fragment != null && !fragment.isEmpty()) {
            try {
                URI noFrag = new URI(uri.toString().replaceAll("#\\Q" + fragment + "\\E$", ""));
                IDTable t = allURIs.computeIfAbsent(noFrag, IDTable::new);
                t.addReference(fragment, currFile, line);
            } catch (URISyntaxException e) {
                throw new Error(e);
            }
        }
    }

    private boolean isSchemeOK(String uriScheme) {
        if (uriScheme == null) {
            return true;
        }

        return switch (uriScheme) {
            case "ftp", "http", "https", "javascript" -> true;
            default -> false;
        };
    }

    static class Position implements Comparable<Position> {
        Path path;
        int line;

        Position(Path path, int line) {
            this.path = path;
            this.line = line;
        }

        @Override
        public int compareTo(Position o) {
            int v = path.compareTo(o.path);
            return v != 0 ? v : Integer.compare(line, o.line);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null || getClass() != obj.getClass()) {
                return false;
            } else {
                final Position other = (Position) obj;
                return Objects.equals(this.path, other.path)
                        && this.line == other.line;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(path) * 37 + line;
        }
    }

    static class IDInfo {
        boolean declared;
        Set<Position> references;

        Set<Position> getReferences() {
            return references == null ? Collections.emptySet() : references;
        }
    }

    static class HostComparator implements Comparator<String> {
        @Override
        public int compare(String h1, String h2) {
            List<String> l1 = new ArrayList<>(Arrays.asList(h1.split("\\.")));
            Collections.reverse(l1);
            String r1 = String.join(".", l1);
            List<String> l2 = new ArrayList<>(Arrays.asList(h2.split("\\.")));
            Collections.reverse(l2);
            String r2 = String.join(".", l2);
            return r1.compareTo(r2);
        }
    }

    class IDTable {
        private final Map<String, IDInfo> map = new HashMap<>();
        private final String pathOrURI;
        private boolean checked;

        IDTable(Path path) {
            this.pathOrURI = path.toString();
        }

        IDTable(URI uri) {
            this.pathOrURI = uri.toString();
        }

        void addID(int line, String name) {
            if (checked) {
                throw new IllegalStateException("Adding ID after file has been checked");
            }
            Objects.requireNonNull(name);
            IDInfo info = map.computeIfAbsent(name, _ -> new IDInfo());
            if (info.declared) {
                if (info.references != null || !checkInwardReferencesOnly) {
                    // don't report error if we're only checking inbound references
                    // and there are no references to this ID.
                    log.log(log.relativize(currFile), line, "name already declared: " + name);
                    duplicateIds++;
                }
            } else {
                info.declared = true;
            }
        }

        void addReference(String name, Path from, int line) {
            if (checked) {
                if (name != null) {
                    IDInfo id = map.get(name);
                    if (id == null || !id.declared) {
                        log.log(log.relativize(from), line,
                                "id not found: " + this.pathOrURI + "#" + name);
                        LinkChecker.this.missingIds++;
                    }
                }
            } else {
                IDInfo id = map.computeIfAbsent(name, x -> new IDInfo());
                if (id.references == null) {
                    id.references = new TreeSet<>();
                }
                id.references.add(new Position(from, line));
            }
        }

        void check() {
            map.forEach((name, id) -> {
                if (name != null && !id.declared) {
                    for (Position ref : id.references) {
                        log.log(log.relativize(ref.path), ref.line,
                                "id not found: " + this.pathOrURI + "#" + name);
                    }
                    missingIds++;
                }
            });
            checked = true;
        }
    }
}
