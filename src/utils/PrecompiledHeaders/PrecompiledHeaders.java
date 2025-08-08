
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PrecompiledHeaders {

    private static final Pattern DEPENDENCY_LINE_PATTERN = Pattern.compile("\\s*(\\S+.hpp)\\s*\\\\?");
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("^#\\s*include \"([^\"]+)\"$");
    private static final String OBJS_PATH = "hotspot/variant-server/libjvm/objs";
    private static final String PRECOMPILED_HPP_NAME = "precompiled.hpp";
    private static final String PRECOMPILED_HPP_PATH = "src/hotspot/share/precompiled/" + PRECOMPILED_HPP_NAME;
    private static final String INLINE_HPP_SUFFIX = ".inline.hpp";
    private static final String HOTSPOT_SOURCE_PREFIX = "/jdk/src/hotspot/share/";

    private PrecompiledHeaders() {
        throw new UnsupportedOperationException("Instances not allowed");
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: min_inclusion_count build_root [jdk_root=.]");
            System.exit(1);
        }

        int minInclusionCount = Integer.parseInt(args[0]);
        Path buildRoot = Path.of(args[1]).toAbsolutePath();
        if (!Files.isDirectory(buildRoot)) {
            throw new IllegalArgumentException("build_root is not a directory: " + buildRoot);
        }
        Path jdkRoot = Path.of(args.length == 3 ? args[2] : ".").toAbsolutePath();
        if (!Files.isDirectory(jdkRoot)) {
            throw new IllegalArgumentException("jdk_root is not a directory: " + jdkRoot);
        }

        Path objsPath = buildRoot.resolve(OBJS_PATH);
        if (!Files.isDirectory(objsPath)) {
            throw new IllegalArgumentException("Could not find 'objs' directory: " + objsPath);
        }

        // Count inclusion times for each header
        Map<String, Integer> occurrences;
        try (Stream<Path> files = Files.list(objsPath)) {
            occurrences = files
                    .filter(file -> file.getFileName().toString().endsWith(".d"))
                    .filter(Predicate.not(file -> file.getFileName().toString().startsWith("BUILD_LIBJVM")))
                    .filter(Predicate.not(file -> file.getFileName().toString().contains(PRECOMPILED_HPP_NAME)))
                    .flatMap(file -> {
                        try {
                            // The first line contains the object name
                            return Files.lines(file).skip(1);
                        } catch (IOException exception) {
                            throw new UncheckedIOException(exception);
                        }
                    })
                    .map(DEPENDENCY_LINE_PATTERN::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> matcher.group(1))
                    .filter(dependency -> dependency.startsWith(HOTSPOT_SOURCE_PREFIX))
                    // Avoid compiler specific headers
                    .filter(Predicate.not(dependency -> dependency.endsWith("_gcc.hpp")))
                    .filter(Predicate.not(dependency -> dependency.endsWith("_visCPP.hpp")))
                    .filter(Predicate.not(dependency -> dependency.endsWith("/" + PRECOMPILED_HPP_NAME)))
                    .map(dependency -> dependency.replace(HOTSPOT_SOURCE_PREFIX, ""))
                    .collect(Collectors.toMap(Function.identity(), s -> 1, Integer::sum));
        }

        // Keep only the headers which are included at least 'minInclusionCount' times
        Set<String> headers = occurrences.entrySet().stream()
                .filter(entry -> entry.getValue() >= minInclusionCount)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        List<String> inlineIncludes = occurrences.keySet().stream()
                .filter(s -> s.endsWith(INLINE_HPP_SUFFIX))
                .toList();
        // If both inline and non-inline headers are to be included, prefer the inline header
        for (String inlineInclude : inlineIncludes) {
            if (headers.contains(inlineInclude)) {
                String noInlineInclude = inlineInclude.replace(INLINE_HPP_SUFFIX, ".hpp");
                headers.remove(noInlineInclude);
            }
        }

        Path precompiledHpp = jdkRoot.resolve(PRECOMPILED_HPP_PATH);
        try (Stream<String> lines = Files.lines(precompiledHpp)) {
            String precompiledHppHeader = lines
                    .takeWhile(Predicate.not(s -> INCLUDE_PATTERN.matcher(s).matches()))
                    .collect(Collectors.joining(System.lineSeparator()));
            Files.write(precompiledHpp, precompiledHppHeader.getBytes());
        }

        String headerLines = headers.stream()
                .sorted()
                .map(header -> String.format("#include \"%s\"", header))
                .collect(Collectors.joining(System.lineSeparator()));
        Files.write(precompiledHpp,
                (System.lineSeparator() + headerLines + System.lineSeparator()).getBytes(),
                StandardOpenOption.APPEND);
    }

}
