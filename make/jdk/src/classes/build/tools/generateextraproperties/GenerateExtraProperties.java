/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package build.tools.generateextraproperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parses extra properties files of UCD, and replaces the placeholders in
 * the given template source file with the generated conditions, then emits
 * .java files. For example, if the properties file has:
 * <blockquote>
 *     0009..000D   ; Type (; Value)
 *     0020         ; Type (; Value)
 *     2000..200A   ; Type (; Value)
 * </blockquote>
 * and the template file contains
 * <blockquote>
 *     %%%Type(=Value)%%%
 * </blockquote>
 * then the generated .java file would have the following in place:
 * <blockquote>
 *     (cp >= 0x0009 && cp <= 0x000D) ||
 *      cp == 0x0020 ||
 *     (cp >= 0x2000 && cp <= 0x200A);
 * </blockquote>
 * Note that those in parentheses in the properties file and the
 * template file are optional.
 *
 * Arguments to this utility:
 *    args[0]: Full path string to the template file
 *    args[1]: Full path string to the properties file
 *    args[2]: Full path string to the generated .java file
 *    args[3...]: Names of the property to generate the conditions
 */
public class GenerateExtraProperties {
    public static void main(String[] args) {
        var templateFile = Paths.get(args[0]);
        var propertiesFile = Paths.get(args[1]);
        var gensrcFile = Paths.get(args[2]);
        var propertyNames = Arrays.copyOfRange(args, 3, args.length);
        var replacementMap = new HashMap<String, String>();

        try {
            for (var propertyName: propertyNames) {
                var pn = "; " + propertyName.replaceFirst("=", "; ");

                List<Range> ranges = Files.lines(propertiesFile)
                        .filter(Predicate.not(l -> l.startsWith("#") || l.isBlank()))
                        .filter(l -> l.contains(pn))
                        .map(l -> new Range(l.replaceFirst(" .*", "")))
                        .sorted()
                        .collect(ArrayList<Range>::new,
                                (list, r) -> {
                                    // collapsing consecutive pictographic ranges
                                    int lastIndex = list.size() - 1;
                                    if (lastIndex >= 0) {
                                        Range lastRange = list.get(lastIndex);
                                        if (lastRange.last + 1 == r.start) {
                                            list.set(lastIndex, new Range(lastRange.start, r.last));
                                            return;
                                        }
                                    }
                                    list.add(r);
                                },
                                ArrayList<Range>::addAll);


                replacementMap.put("%%%" + propertyName + "%%%",
                        ranges.stream()
                                .map(GenerateExtraProperties::rangeToString)
                                .collect(Collectors.joining(" ||\n", "", ";")));
            }

            // Generate .java file
            Files.write(gensrcFile,
                    Files.lines(templateFile)
                            .flatMap(l -> Stream.of(replacementMap.getOrDefault(l.trim(), l)))
                            .collect(Collectors.toList()),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static String rangeToString(Range r) {
        if (r.start == r.last) {
            return (" ".repeat(12) + "cp == 0x" + toHexString(r.start));
        } else if (r.start == r.last - 1) {
            return " ".repeat(12) + "cp == 0x" + toHexString(r.start) + " ||\n" +
                    " ".repeat(12) + "cp == 0x" + toHexString(r.last);
        } else {
            return " ".repeat(11) + "(cp >= 0x" + toHexString(r.start) +
                    " && cp <= 0x" + toHexString(r.last) + ")";
        }
    }

    static int toInt(String hexStr) {
        return Integer.parseUnsignedInt(hexStr, 16);
    }

    static String toHexString(int cp) {
        String ret = Integer.toUnsignedString(cp, 16).toUpperCase();
        if (ret.length() < 4) {
            ret = "0".repeat(4 - ret.length()) + ret;
        }
        return ret;
    }

    static class Range implements Comparable<Range> {
        int start;
        int last;

        Range (int start, int last) {
            this.start = start;
            this.last = last;
        }

        Range (String input) {
            input = input.replaceFirst("\\s#.*", "");
            start = toInt(input.replaceFirst("[\\s\\.].*", ""));
            last = input.contains("..") ?
                    toInt(input.replaceFirst(".*\\.\\.", "")
                            .replaceFirst(";.*", "").trim())
                    : start;
        }

        @Override
        public String toString() {
            return "Start: " + toHexString(start) + ", Last: " + toHexString(last);
        }

        @Override
        public int compareTo(Range other) {
            return Integer.compare(start, other.start);
        }
    }
}