
/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// See [#main].
public class SortIncludes {
    private static final String INCLUDE_LINE = "^ *# *include *(<[^>]+>|\"[^\"]+\") *$\\n";
    private static final String BLANK_LINE = "^$\\n";
    private static final Pattern INCLUDES_RE = Pattern.compile(
                    String.format("%s(?:(?:%s)*%s)*", INCLUDE_LINE, BLANK_LINE, INCLUDE_LINE),
                    Pattern.MULTILINE);

    /// Gets a sorting key for an include which is its substring as of `delim`, lowercased.
    /// Note that using lowercase will sort `_` before letters.
    private static Comparator<String> sortKeyForInclude(char delim) {
        return Comparator.comparing(s -> s.toLowerCase().substring(s.indexOf(delim)));
    }

    /// Gets the first substring in `s` enclosed by `start` and `end`.
    private static String extract(String s, char start, char end) {
        int startIndex = s.indexOf(start);
        int endIndex = s.indexOf(end, startIndex + 1);
        if (startIndex == -1 || endIndex == -1) {
            throw new IllegalArgumentException(s);
        }
        return s.substring(startIndex + 1, endIndex);
    }

    /// Sorts the include statements in `block`.
    ///
    /// @param path path of the file containing `block`
    /// @param block source code chunk containing 1 or more include statements
    /// @return `block` with the include statements sorted and a blank line between user and
    /// sys includes
    private static String sortedIncludes(Path path, String block) {
        String[] lines = block.split("\\n");
        SortedSet<String> userIncludes = new TreeSet<>(sortKeyForInclude('"'));
        SortedSet<String> sysIncludes = new TreeSet<>(sortKeyForInclude('<'));

        // From the style guide:
        //
        // All .inline.hpp files should include their corresponding .hpp file
        // as the first include line with a blank line separating it from the
        // rest of the include lines. Declarations needed by other files should
        // be put in the .hpp file, and not in the .inline.hpp file. This rule
        // exists to resolve problems with circular dependencies between
        // .inline.hpp files.
        String pathString = path.toString();
        boolean isInlineHpp = pathString.endsWith(".inline.hpp");
        String nonInlineHpp = pathString.replace(".inline.hpp", ".hpp");
        if (File.separatorChar != '/') {
            nonInlineHpp = nonInlineHpp.replace(File.separatorChar, '/');
        }

        List<String> result = new ArrayList<>(lines.length);

        // Partition lines into user include and sys includes and discard blank lines
        for (String line : lines) {
            if (line.contains("\"")) {
                if (isInlineHpp && nonInlineHpp.endsWith(extract(line, '"', '"'))) {
                    result.add(line);
                } else {
                    userIncludes.add(line);
                }
            } else if (line.contains("<")) {
                sysIncludes.add(line);
            }
        }

        if (!result.isEmpty() && (!userIncludes.isEmpty() || !sysIncludes.isEmpty())) {
            // Insert blank line between include of .hpp from .inline.hpp
            // and the rest of the includes
            result.add("");
        }
        result.addAll(userIncludes);
        if (!userIncludes.isEmpty() && !sysIncludes.isEmpty()) {
            // Insert blank line between user and sys includes
            result.add("");
        }
        result.addAll(sysIncludes);

        return String.join("\n", result) + "\n";
    }

    /// Processes the C++ source file in `path` to sort its include statements.
    ///
    /// @param path a path of a C++ source file
    /// @param update updates the source file if sorting changed its content
    /// @return `true` if sorting changes were made,`false` otherwise
    public static boolean sortIncludes(Path path, boolean update) throws IOException {
        String source = Files.readString(path);
        Matcher matcher = INCLUDES_RE.matcher(source);
        StringBuilder buf = new StringBuilder();
        int end = 0;

        while (matcher.find()) {
            if (matcher.start() != end) {
                buf.append(source, end, matcher.start());
            }
            buf.append(sortedIncludes(path, matcher.group()));
            end = matcher.end();
        }

        if (end == 0) {
            return false;
        }
        buf.append(source.substring(end));

        String newSource = buf.toString();
        if (!newSource.equals(source)) {
            if (update) {
                Files.writeString(path, newSource);
            }
            return true;
        }
        return false;
    }

    /// Record of the files processed by [#process(List, boolean)] and those
    /// that had unsorted includes.
    public record Result(List<Path> files, List<Path> unsorted) {
    }

    /// Processes the C++ source files in `paths` to check if their include statements are sorted.
    /// Include statements with any non-space characters after the closing `"` or `>` will not
    /// be re-ordered.
    ///
    /// @param paths list of directory and file paths
    /// @param update if `true`, files with unsorted includes are updated to sort the includes
    /// @return the files that had unsorted include statements.
    public static Result process(List<Path> paths, boolean update) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path path : paths) {
            if (Files.isRegularFile(path)) {
                files.add(path);
            } else {
                Files.walk(path).forEach(p -> {
                    if (Files.isRegularFile(p)) {
                        String name = p.getFileName().toString();
                        if (name.endsWith(".cpp") || name.endsWith(".hpp")) {
                            files.add(p);
                        }
                    }
                });
            }
        }

        List<Path> unsorted = new ArrayList<>();
        for (Path file : files) {
            if (sortIncludes(file, update)) {
                unsorted.add(file);
            }
        }
        return new Result(files, unsorted);
    }

    /// Exception thrown by [#main] if `"--update"` is in `args` and
    /// files with unsorted includes were seen.
    public static class UnsortedIncludesException extends Exception {
        /// Files with unsorted includes.
        public final List<Path> files;

        public UnsortedIncludesException(List<Path> files) {
            this.files = files;
        }

        @Override
        public String getMessage() {
            String unsorted = files.stream().map(Path::toString).collect(Collectors.joining(System.lineSeparator()));
            return String.format("%d files with unsorted headers found:%n%s", files.size(), unsorted);
        }
    }

    /// Processes C++ files to check if their include statements are sorted.
    ///
    /// @param args `[--update] dir|file...` where `update` means the processed
    ///        files are updated to sort any unsorted includes and `dir|file` are the
    ///        roots to scan for the C++ files to be processed
    /// @throws UnsortedIncludesException if `args` includes `"--update"` and
    ///         files with unsorted includes were found
    public static void main(String[] args) throws IOException, UnsortedIncludesException {
        boolean update = false;
        List<Path> paths = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("--update")) {
                update = true;
            } else {
                paths.add(Paths.get(arg));
            }
        }

        Result result = process(paths, update);
        if (update) {
            System.out.printf("Processed %d files, updated %d to sort include statements%n",
                            result.files.size(),
                            result.unsorted().size());
        } else if (!result.unsorted().isEmpty()) {
            throw new UnsortedIncludesException(result.unsorted);
        }
    }
}
