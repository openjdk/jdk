/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package build.tools.generatespecialcasing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parses UCD's "SpecialCasing.txt" file and extract selected special case
 * mappings, then reformats each entry into `Entry` instances in
 * `java.lang.ConditionalSpecialCasing` class.
 *
 * Arguments to this utility:
 *    args[0]: Full path to the "ConditionalSpecialCasing" template file
 *    args[1]: Full path to the "SpecialCasing.txt" UCD file
 *    args[2]: Full path to the generated output file
 */
public class GenerateSpecialCasing {
    // Represents a code point with selected special casing.
    private record Entry(String codePoint, List<String> lowerCase,
                         List<String> upperCase, String language,
                         String condition) {};

    public static void main(String[] args) throws IOException {
        var templateFile = Paths.get(args[0]);
        var specialCasingFile = Paths.get(args[1]);
        var gensrcFile = Paths.get(args[2]);

        List<Entry> entries = Files.lines(specialCasingFile)
            .filter(Predicate.not(l -> l.startsWith("#") || l.isBlank()))
            .map(l -> l.replaceFirst("#.*$", "").split(";"))
            // "U+0130" (LATIN_CAPITAL_LETTER_I_WITH_DOT_ABOVE)
            // is needed even if it is non-conditional
            .filter(l -> !l[4].isBlank() || l[0].equals("0130"))
            .map(l -> new Entry(l[0],
                Arrays.asList(l[1].trim().split(" ")),
                Arrays.asList(l[3].trim().split(" ")),
                l[4].replaceFirst("[A-Z].*$", "").trim(),
                l[4].replaceFirst("^[ a-z]*", "").toUpperCase(Locale.ROOT).trim()))
            .toList();

        // Generate output file
        Files.write(gensrcFile,
            Files.lines(templateFile)
                .flatMap(l -> {
                    if (l.trim().equals("%%%SpecialCasing%%%")) {
                        return entries.stream().map(GenerateSpecialCasing::entryToString);
                    } else {
                        return Stream.of(l);
                    }
                })
                .collect(Collectors.toList()),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    static String entryToString(Entry e) {
        var codePoint = e.codePoint();
        if (codePoint == null || codePoint.isBlank()) {
            throw new RuntimeException("Corrupt entry: " + e);
        }

        return "        new Entry(%s, new char[]{%s}, new char[]{%s}, %s, %s),"
           .formatted(
               "0x" + codePoint,
               e.lowerCase().stream().map(cp -> cp.isEmpty() ? "" : "0x"+cp).collect(Collectors.joining(",")),
               e.upperCase().stream().map(cp -> cp.isEmpty() ? "" : "0x"+cp).collect(Collectors.joining(",")),
               "\"" + e.language() + "\"",
               e.condition().isEmpty() ? "NONE" : e.condition());
    }
}
