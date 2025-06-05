/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8293177 8324774 8357458
 * @summary Verify version numbers in legal files
 * @library /test/lib
 * @build jtreg.SkippedException
 * @run main CheckLibraryVersions
 */

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jtreg.SkippedException;

/**
 * Checks the names and version strings of 3rd party libraries in legal files
 * against the actual names and versions in library files.
 */
public class CheckLibraryVersions {
    static class SourceDirNotFound extends Error {}
    // Regex pattern for library name and version in legal Markdown file
    static final Pattern versionPattern = Pattern.compile("## ([\\w\\s.]+) v(\\d+(\\.\\d+){1,2})");

    // Map of 3rd party libraries. The keys are the names of files in the legal directory,
    // the values are lists of templates for library files with the following placeholders:
    //  %V is replaced with the version string
    //  %M is replaced twice, once with an empty string and once with ".min"
    static final Map<String, List<String>> libraries = Map.of(
            "jquery.md", List.of("jquery/jquery-%V%M.js"),
            "jqueryUI.md", List.of("jquery/jquery-ui%M.js", "jquery/jquery-ui%M.css"),
            "dejavufonts.md", List.of("fonts/dejavu.css"),
            "highlightjs.md", List.of("highlight.js")
    );

    public static void main(String... args) throws Exception {
        try {
            new CheckLibraryVersions().run(args);
        } catch (SourceDirNotFound e) {
            throw new SkippedException("NOTE: Cannot find src directory; test skipped");
        }
    }

    static final PrintStream out = System.err;

    void run(String... args) throws Exception {
        var rootDir = args.length == 0 ? findRootDir() : Path.of(args[0]);
        var legalDir = rootDir.resolve("src/jdk.javadoc/share/legal");
        var scriptDir = rootDir.resolve("src/jdk.javadoc/share/classes")
                                     .resolve("jdk/javadoc/internal/doclets/formats/html")
                                     .resolve("resources");

        for (var legalFileName : libraries.keySet()) {
            var legalFile = legalDir.resolve(legalFileName);
            out.println();
            if (!Files.exists(legalFile)) {
                error("Legal file not found: " + legalFile);
                continue;
            }
            out.println("Checking legal file: " + legalFile);
            var contents = Files.readString(legalFile);
            var matcher = versionPattern.matcher(contents);
            if (!matcher.find()) {
                error("Library name and version not found in " + legalFile);
                continue;
            }
            var libraryName = matcher.group(1);
            var versionString = matcher.group(2);
            out.println("Found name and version: " + matcher.group(1) + " " + matcher.group(2));
            var templates = libraries.get(legalFileName);
            for (var template : templates) {
                checkLibraryFile(scriptDir, template, libraryName, versionString, "");
                if (template.contains("%M")) {
                    checkLibraryFile(scriptDir, template, libraryName, versionString, ".min");
                }
            }
        }

        if (errors > 0) {
            out.println(errors + " errors found");
            throw new Exception(errors + " errors found");
        }
    }

    void checkLibraryFile(Path scriptDir, String template, String libraryName,
                          String versionString, String minified) throws IOException {
        out.println();
        var libraryFileName = template
                .replaceAll("%V", versionString)
                .replaceAll("%M", minified);
        var libraryFile = scriptDir.resolve(libraryFileName);
        if (!Files.exists(libraryFile)) {
            error("Library file not found: " + libraryFile);
            return;
        }
        out.println("Checking library file: " + libraryFile);
        var libraryContents = Files.readString(libraryFile);
        var pattern = Pattern.compile("\\b" + libraryName + "[^\\n]* v" + versionString + "\\b");
        var matcher = pattern.matcher(libraryContents);
        if (!matcher.find()) {
            error("Matching library name and version not found in " + libraryFileName);
            return;
        }
        out.println("Found matching name and version: " + matcher.group());
    }

    int errors = 0;
    void error(String message) {
        ("Error: " + message).lines().forEach(out::println);
        errors++;
    }

    Path findRootDir() {
        Path dir = Path.of(System.getProperty("test.src", ".")).toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("src").resolve("jdk.javadoc"))) {
                return dir;
            } else {
                Path openDir = dir.resolve("open");
                if (Files.exists(openDir.resolve("src").resolve("jdk.javadoc"))) {
                    return openDir;
                }
            }
            dir = dir.getParent();
        }
        throw new SourceDirNotFound();
    }
}
