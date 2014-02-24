/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

public class EquivMapsGenerator {

    /*
     * IANA Language Subtag Registry file downloaded from
     *     http://www.iana.org/assignments/language-subtag-registry
     */
    private static final String DEFAULT_LSR_FILE =
        "language-subtag-registry.txt";

    private static boolean verbose = false;

    public static void main(String[] args) throws Exception {
        String fileLSR = DEFAULT_LSR_FILE;

        for (int i = 0; i < args.length; i++) {
            String s = args[i];
            if (s.equals("-lsr")) {
                fileLSR = args[++i];
            } else if (s.equals("-verbose")) {
                verbose = true;
            }
        }

        readLSRfile(fileLSR);
        generateEquivalentMap();
        generateSourceCode();
    }

    private static String LSRrevisionDate;
    private static Map<String, StringBuilder> initialLanguageMap =
        new TreeMap<>();
    private static Map<String, StringBuilder> initialRegionVariantMap =
        new TreeMap<>();

    private static Map<String, String> sortedLanguageMap1 = new TreeMap<>();
    private static Map<String, String[]> sortedLanguageMap2 = new TreeMap<>();
    private static Map<String, String> sortedRegionVariantMap =
        new TreeMap<>();

    private static void readLSRfile(String filename) throws Exception {
        String type = null;
        String tag = null;
        String preferred = null;
        int mappingNum = 0;

        for (String line : Files.readAllLines(Paths.get(filename),
                                              Charset.forName("UTF-8"))) {
            line = line.toLowerCase();
            int index = line.indexOf(' ')+1;
            if (line.startsWith("file-date:")) {
                LSRrevisionDate = line.substring(index);
                if (verbose) {
                    System.out.println("LSR revision date=" + LSRrevisionDate);
                }
            } else if (line.startsWith("type:")) {
                type = line.substring(index);
            } else if (line.startsWith("tag:") || line.startsWith("subtag:")) {
                tag = line.substring(index);
            } else if (line.startsWith("preferred-value:")
                       && !type.equals("extlang")) {
                preferred = line.substring(index);
                mappingNum++;
                processDeprecatedData(type, tag, preferred);
            } else if (line.equals("%%")) {
                type = null;
                tag = null;
                preferred = null;
            }
        }

        if (verbose) {
            System.out.println("readLSRfile(" + filename + ")");
            System.out.println("  Total number of mapping=" + mappingNum);
            System.out.println("\n  Map for language. Size="
                + initialLanguageMap.size());

            for (String key : initialLanguageMap.keySet()) {
                System.out.println("    " + key + ": \""
                    + initialLanguageMap.get(key) + "\"");
            }

            System.out.println("\n  Map for region and variant. Size="
                + initialRegionVariantMap.size());

            for (String key : initialRegionVariantMap.keySet()) {
                System.out.println("    " + key + ": \""
                    + initialRegionVariantMap.get(key) + "\"");
            }
        }
    }

    private static void processDeprecatedData(String type,
                                              String tag,
                                              String preferred) {
        StringBuilder sb;
        if (type.equals("region") || type.equals("variant")) {
            if (!initialRegionVariantMap.containsKey(preferred)) {
                sb = new StringBuilder("-");
                sb.append(preferred);
                sb.append(",-");
                sb.append(tag);
                initialRegionVariantMap.put("-"+preferred, sb);
            } else {
                throw new RuntimeException("New case, need implementation."
                    + " A region/variant subtag \"" + preferred
                    + "\" is registered for more than one subtags.");
            }
        } else { // language, grandfahered, and redundant
            if (!initialLanguageMap.containsKey(preferred)) {
                sb = new StringBuilder(preferred);
                sb.append(',');
                sb.append(tag);
                initialLanguageMap.put(preferred, sb);
            } else {
                sb = initialLanguageMap.get(preferred);
                sb.append(',');
                sb.append(tag);
                initialLanguageMap.put(preferred, sb);
            }
        }
    }

    private static void generateEquivalentMap() {
        String[] subtags;
        for (String preferred : initialLanguageMap.keySet()) {
            subtags = initialLanguageMap.get(preferred).toString().split(",");

            if (subtags.length == 2) {
                sortedLanguageMap1.put(subtags[0], subtags[1]);
                sortedLanguageMap1.put(subtags[1], subtags[0]);
            } else if (subtags.length == 3) {
                sortedLanguageMap2.put(subtags[0],
                                     new String[]{subtags[1], subtags[2]});
                sortedLanguageMap2.put(subtags[1],
                                     new String[]{subtags[0], subtags[2]});
                sortedLanguageMap2.put(subtags[2],
                                     new String[]{subtags[0], subtags[1]});
            } else {
                    throw new RuntimeException("New case, need implementation."
                        + " A language subtag \"" + preferred
                        + "\" is registered for more than two subtags. ");
            }
        }

        for (String preferred : initialRegionVariantMap.keySet()) {
            subtags =
                initialRegionVariantMap.get(preferred).toString().split(",");

            sortedRegionVariantMap.put(subtags[0], subtags[1]);
            sortedRegionVariantMap.put(subtags[1], subtags[0]);
        }

        if (verbose) {
            System.out.println("generateEquivalentMap()");
            System.out.println("  \nSorted map for language subtags which have only one equivalent. Size="
                + sortedLanguageMap1.size());
            for (String key : sortedLanguageMap1.keySet()) {
                System.out.println("    " + key + ": \""
                    + sortedLanguageMap1.get(key) + "\"");
            }

            System.out.println("\n  Sorted map for language subtags which have multiple equivalents. Size="
                + sortedLanguageMap2.size());
            for (String key : sortedLanguageMap2.keySet()) {
                String[] s = sortedLanguageMap2.get(key);
                System.out.println("    " + key + ": \""
                    + s[0] + "\", \"" + s[1] + "\"");
            }

            System.out.println("\n  Sorted map for region and variant subtags. Size="
                + sortedRegionVariantMap.size());
            for (String key : sortedRegionVariantMap.keySet()) {
                System.out.println("    " + key + ": \""
                    + sortedRegionVariantMap.get(key) + "\"");
            }
        }
        System.out.println();
    }

    private final static String headerText =
        "final class LocaleEquivalentMaps {\n\n"
        + "    static final Map<String, String> singleEquivMap;\n"
        + "    static final Map<String, String[]> multiEquivsMap;\n"
        + "    static final Map<String, String> regionVariantEquivMap;\n\n"
        + "    static {\n"
        + "        singleEquivMap = new HashMap<>();\n"
        + "        multiEquivsMap = new HashMap<>();\n"
        + "        regionVariantEquivMap = new HashMap<>();\n\n"
        + "        // This is an auto-generated file and should not be manually edited.\n";

    private final static String footerText =
        "    }\n\n"
        + "}";

    private static void generateSourceCode() {
        System.out.println(headerText
            + "        //   LSR Revision: " + LSRrevisionDate);

        for (String key : sortedLanguageMap1.keySet()) {
            String value = sortedLanguageMap1.get(key);
            System.out.println("        singleEquivMap.put(\""
                + key + "\", \"" + value + "\");");
        }
        System.out.println();
        for (String key : sortedLanguageMap2.keySet()) {
            String[] values = sortedLanguageMap2.get(key);
            System.out.println("        multiEquivsMap.put(\""
                + key + "\", new String[] {\"" + values[0] + "\", \""
                + values[1] + "\"});");
        }
        System.out.println();
        for (String key : sortedRegionVariantMap.keySet()) {
            String value = sortedRegionVariantMap.get(key);
            System.out.println("        regionVariantEquivMap.put(\""
                + key + "\", \"" + value + "\");");
        }

        System.out.println(footerText);
    }

}
