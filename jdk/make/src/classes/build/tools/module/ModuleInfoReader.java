/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package build.tools.module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import build.tools.module.Module.Builder;

/**
 * Source reader of module-info.java
 */
public class ModuleInfoReader {
    private final Path sourcefile;
    private final Builder builder;
    private ModuleInfoReader(Path file) {
        this.sourcefile = file;
        this.builder = new Builder();
    }

    public static Builder builder(Path file) throws IOException {
        ModuleInfoReader reader = new ModuleInfoReader(file);
        reader.readFile();
        return reader.builder;
    }

    /**
     * Reads the source file.
     */
    void readFile() throws IOException {
        List<String> lines = Files.readAllLines(sourcefile);
        boolean done = false;
        int lineNumber = 0;
        boolean inBlockComment = false;
        boolean inRequires = false;
        boolean reexports = false;
        boolean inProvides = false;
        boolean inWith = false;
        String serviceIntf = null;
        String providerClass = null;
        boolean inUses = false;
        boolean inExports = false;
        boolean inExportsTo = false;
        String qualifiedExports = null;
        Counter counter = new Counter();

        for (String line : lines) {
            lineNumber++;
            if (inBlockComment) {
                int c = line.indexOf("*/");
                if (c >= 0) {
                    line = line.substring(c + 2, line.length());
                    inBlockComment = false;
                } else {
                    // skip lines until end of comment block
                    continue;
                }
            }
            inBlockComment = beginBlockComment(line);

            line = trimComment(line).trim();
            // ignore empty lines
            if (line.length() == 0) {
                continue;
            }
            String values;
            if (inRequires || inExports | inUses | (inWith && providerClass == null)) {
                values = line;
            } else {
                String[] s = line.split("\\s+");
                String keyword = s[0].trim();
                int nextIndex = keyword.length();
                switch (keyword) {
                    case "module":
                        if (s.length != 3 || !s[2].trim().equals("{")) {
                            throw new RuntimeException(sourcefile + ", line " +
                                    lineNumber + ", is malformed");
                        }
                        builder.name(s[1].trim());
                        continue;  // next line
                    case "requires":
                        inRequires = true;
                        counter.numRequires++;
                        if (s.length >= 2) {
                            String ss = s[1].trim();
                            if (ss.equals("public")) {
                                nextIndex = line.indexOf(ss) + ss.length();
                                reexports = true;
                            }
                        }
                        break;
                    case "exports":
                        inExports = true;
                        inExportsTo = false;
                        counter.numExports++;
                        qualifiedExports = null;
                        if (s.length >= 3) {
                            qualifiedExports = s[1].trim();
                            nextIndex = line.indexOf(qualifiedExports, nextIndex)
                                            + qualifiedExports.length();
                            if (s[2].trim().equals("to")) {
                                inExportsTo = true;
                                nextIndex = line.indexOf("to", nextIndex) + "to".length();
                            } else {
                                throw new RuntimeException(sourcefile + ", line " +
                                        lineNumber + ", is malformed: " + s[2]);
                            }
                        }
                        break;
                    case "to":
                        if (!inExports || qualifiedExports == null) {
                            throw new RuntimeException(sourcefile + ", line " +
                                    lineNumber + ", is malformed");
                        }
                        inExportsTo = true;
                        break;
                    case "uses":
                        inUses = true;
                        counter.numUses++;
                        break;
                    case "provides":
                        inProvides = true;
                        inWith = false;
                        counter.numProvides++;
                        serviceIntf = null;
                        providerClass = null;
                        if (s.length >= 2) {
                            serviceIntf = s[1].trim();
                            nextIndex = line.indexOf(serviceIntf) + serviceIntf.length();
                        }
                        if (s.length >= 3) {
                            if (s[2].trim().equals("with")) {
                                inWith = true;
                                nextIndex = line.indexOf("with") + "with".length();
                            } else {
                                throw new RuntimeException(sourcefile + ", line " +
                                        lineNumber + ", is malformed: " + s[2]);
                            }
                        }
                        break;
                    case "with":
                        if (!inProvides || serviceIntf == null) {
                            throw new RuntimeException(sourcefile + ", line " +
                                    lineNumber + ", is malformed");
                        }
                        inWith = true;
                        nextIndex = line.indexOf("with") + "with".length();
                        break;
                    case "}":
                        counter.validate(builder);
                        done = true;
                        continue;  // next line
                    default:
                        throw new RuntimeException(sourcefile + ", \"" +
                                keyword + "\" on line " +
                                lineNumber + ", is not recognized");
                }
                values = line.substring(nextIndex, line.length()).trim();
            }

            int len = values.length();
            if (len == 0) {
                continue;  // next line
            }
            char lastchar = values.charAt(len - 1);
            if (lastchar != ',' && lastchar != ';') {
                throw new RuntimeException(sourcefile + ", line " +
                        lineNumber + ", is malformed:" +
                        " ',' or ';' is missing.");
            }

            values = values.substring(0, len - 1).trim();
            // parse the values specified for a keyword specified
            for (String s : values.split(",")) {
                s = s.trim();
                if (s.length() > 0) {
                    if (inRequires) {
                        if (builder.requires.contains(s)) {
                            throw new RuntimeException(sourcefile + ", line "
                                    + lineNumber + " duplicated requires: \"" + s + "\"");
                        }
                        builder.require(s, reexports);
                    } else if (inExports) {
                        if (!inExportsTo && qualifiedExports == null) {
                            builder.export(s);
                        } else {
                            builder.exportTo(qualifiedExports, s);
                        }
                    } else if (inUses) {
                        builder.use(s);
                    } else if (inProvides) {
                        if (!inWith) {
                            serviceIntf = s;
                        } else {
                            providerClass = s;
                            builder.provide(serviceIntf, providerClass);
                        }
                    }
                }
            }
            if (lastchar == ';') {
                inRequires = false;
                reexports = false;
                inExports = false;
                inExportsTo = false;
                inProvides = false;
                inWith = false;
                inUses = false;
            }
        }

        if (inBlockComment) {
            throw new RuntimeException(sourcefile + ", line " +
                    lineNumber + ", missing \"*/\" to end a block comment");
        }
        if (!done) {
            throw new RuntimeException(sourcefile + ", line " +
                    lineNumber + ", missing \"}\" to end module definition" +
                    " for \"" + builder + "\"");
        }
        return;
    }

    // the naming convention for the module names without dashes
    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("[\\w\\.\\*_$/]+");
    private static boolean beginBlockComment(String line) {
        int pos = 0;
        while (pos >= 0 && pos < line.length()) {
            int c = line.indexOf("/*", pos);
            if (c < 0) {
                return false;
            }

            if (c > 0 && !Character.isWhitespace(line.charAt(c - 1))) {
                return false;
            }

            int c1 = line.indexOf("//", pos);
            if (c1 >= 0 && c1 < c) {
                return false;
            }

            int c2 = line.indexOf("*/", c + 2);
            if (c2 < 0) {
                return true;
            }
            pos = c + 2;
        }
        return false;
    }
    private static String trimComment(String line) {
        StringBuilder sb = new StringBuilder();

        int pos = 0;
        while (pos >= 0 && pos < line.length()) {
            int c1 = line.indexOf("//", pos);
            if (c1 > 0 && !Character.isWhitespace(line.charAt(c1 - 1))) {
                // not a comment
                c1 = -1;
            }

            int c2 = line.indexOf("/*", pos);
            if (c2 > 0 && !Character.isWhitespace(line.charAt(c2 - 1))) {
                // not a comment
                c2 = -1;
            }

            int c = line.length();
            int n = line.length();
            if (c1 >= 0 || c2 >= 0) {
                if (c1 >= 0) {
                    c = c1;
                }
                if (c2 >= 0 && c2 < c) {
                    c = c2;
                }
                int c3 = line.indexOf("*/", c2 + 2);
                if (c == c2 && c3 > c2) {
                    n = c3 + 2;
                }
            }
            if (c > 0) {
                if (sb.length() > 0) {
                    // add a whitespace if multiple comments on one line
                    sb.append(" ");
                }
                sb.append(line.substring(pos, c));
            }
            pos = n;
        }
        return sb.toString();
    }


    static class Counter {
        int numRequires;
        int numExports;
        int numUses;
        int numProvides;

        void validate(Builder builder) {
            assertEquals("requires", numRequires, builder.requires.size(),
                         () -> builder.requires.stream()
                                      .map(Module.Dependence::toString));
            assertEquals("exports", numExports, builder.exports.size(),
                         () -> builder.exports.entrySet().stream()
                                      .map(e -> "exports " + e.getKey() + " to " + e.getValue()));
            assertEquals("uses", numUses, builder.uses.size(),
                         () -> builder.uses.stream());
            assertEquals("provides", numProvides,
                         (int)builder.provides.values().stream()
                                     .flatMap(s -> s.stream())
                                     .count(),
                         () -> builder.provides.entrySet().stream()
                                      .map(e -> "provides " + e.getKey() + " with " + e.getValue()));
        }

        private static void assertEquals(String msg, int expected, int got,
                                         Supplier<Stream<String>> supplier) {
            if (expected != got){
                System.err.println("ERROR: mismatched " + msg +
                        " expected: " + expected + " got: " + got );
                supplier.get().sorted()
                        .forEach(System.err::println);
                throw new AssertionError("mismatched " + msg +
                        " expected: " + expected + " got: " + got + " ");
            }
        }
    }
}
