/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.generatelsrequivmaps;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * This tool reads the IANA Language Subtag Registry data file downloaded from
 * http://www.iana.org/assignments/language-subtag-registry, which is specified
 * in the command line and generates a .java source file as specified in
 * command line. The generated .java source file contains equivalent language
 * maps. These equivalent language maps are used by LocaleMatcher.java
 * for the locale matching mechanism specified in RFC 4647 "Matching of Language
 * Tags".
 */
public class EquivMapsGenerator {

    public static void main(String[] args) throws Exception {
        int i = 0;
        boolean valid = true;
        if (args.length != 5 && args.length !=3) {
            valid = false;
        } else if (args.length == 5) {
            if ("-jdk-header-template".equals(args[i])) {
                jdkHeaderTemplate = Files.readString(Paths.get(args[++i]));
                i++;
            } else {
                valid = false;
            }
        }
        if (!valid) {
            System.err.println("Usage: java EquivMapsGenerator"
                    + " [-jdk-header-template <file>]"
                    + " language-subtag-registry.txt LocaleEquivalentMaps.java copyrightYear");
            System.exit(1);
        }
        String lsrFile = args[i++];
        String outputFile = args[i++];
        copyrightYear = Integer.parseInt(args[i++]);

        readLSRfile(lsrFile);
        // Builds the maps from the IANA data
        generateEquivalentMap();
        // Writes the maps out to LocaleEquivalentMaps.java
        generateSourceCode(outputFile);
    }

    private static String LSRrevisionDate;
    private static int copyrightYear;
    private static String jdkHeaderTemplate;
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
        String prefix = null;

        for (String line : Files.readAllLines(Paths.get(filename))) {
            line = line.toLowerCase(Locale.ROOT);
            int index = line.indexOf(' ') + 1;
            if (line.startsWith("file-date:")) {
                LSRrevisionDate = line.substring(index);
            } else if (line.startsWith("type:")) {
                type = line.substring(index);
            } else if (line.startsWith("tag:") || line.startsWith("subtag:")) {
                tag = line.substring(index);
            } else if (line.startsWith("preferred-value:")) {
                preferred = line.substring(index);
            } else if (line.startsWith("prefix:")) {
                prefix = line.substring(index);
            } else if (line.equals("%%")) {
                processDeprecatedData(type, tag, preferred, prefix);
                type = null;
                tag = null;
                preferred = null;
                prefix = null;
            }
        }

        // Last entry
        processDeprecatedData(type, tag, preferred, prefix);
    }

    private static void processDeprecatedData(String type,
                                              String tag,
                                              String preferred,
                                              String prefix) {
        StringBuilder sb;

        if (type == null || tag == null || preferred == null) {
            return;
        }

        if (type.equals("extlang") && prefix != null) {
            tag = prefix + "-" + tag;
        }

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
        } else { // language, extlang, legacy, and redundant
            if (!initialLanguageMap.containsKey(preferred)) {
                // IANA update 4/13 introduced case where a preferred value
                // can have a preferred value itself.
                // eg: ar-ajp has pref ajp which has pref apc
                boolean foundInOther = false;
                Pattern pattern = Pattern.compile(","+preferred+"(,|$)");
                // Check if current pref exists inside a value for another pref
                List<StringBuilder> doublePrefs = initialLanguageMap
                        .values()
                        .stream()
                        .filter(e -> pattern.matcher(e.toString()).find())
                        .toList();
                for (StringBuilder otherPrefVal : doublePrefs) {
                    otherPrefVal.append(",");
                    otherPrefVal.append(tag);
                    foundInOther = true;
                }
                if (!foundInOther) {
                    // does not exist in any other pref's values, so add as new entry
                    sb = new StringBuilder(preferred);
                    sb.append(',');
                    sb.append(tag);
                    initialLanguageMap.put(preferred, sb);
                }
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
            // There are cases where the same tag may appear in two entries, e.g.,
            // "yue" is defined both as extlang and redundant. Remove the dup.
            subtags = Arrays.stream(initialLanguageMap.get(preferred).toString().split(","))
                    .distinct()
                    .toList()
                    .toArray(new String[0]);

            if (subtags.length == 2) {
                sortedLanguageMap1.put(subtags[0], subtags[1]);
                sortedLanguageMap1.put(subtags[1], subtags[0]);
            } else if (subtags.length > 2) {
                for (int i = 0; i < subtags.length; i++) {
                    sortedLanguageMap2.put(subtags[i], createLangArray(i, subtags));
                }
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

    }

    /* create the array of subtags excluding the subtag at index location */
    private static String[] createLangArray(int index, String[] subtags) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < subtags.length; i++) {
            if (i != index) {
                list.add(subtags[i]);
            }
        }
        return list.toArray(new String[list.size()]);
    }

    /**
     * The input lsr data file is in UTF-8, so theoretically for the characters
     * beyond US-ASCII, the generated Java String literals need to be Unicode
     * escaped (\\uXXXX) while writing to a file. But as of now, there is not
     * the case since we don't use "description", "comment" or alike.
     */
    private static void generateSourceCode(String fileName) {
        try (BufferedWriter writer = Files.newBufferedWriter(
                Paths.get(fileName))) {
            writer.write(getOpenJDKCopyright());
            writer.write("\n");
            writer.write(HEADER_TEXT);
            writer.write(getMapsText());
            writer.write(getLSRText());
            writeEquiv(writer, "singleEquivMap", sortedLanguageMap1);
            writer.newLine();
            writeMultiEquiv(writer);
            writer.newLine();
            writeEquiv(writer, "regionVariantEquivMap", sortedRegionVariantMap);
            writer.write(FOOTER_TEXT);
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static String getOpenJDKCopyright() {
        return String.format(Locale.US,
                (jdkHeaderTemplate != null ? jdkHeaderTemplate : COPYRIGHT), copyrightYear);
    }

    private static final String COPYRIGHT =
            """
            /*
             * Copyright (c) 2012, %d, Oracle and/or its affiliates. All rights reserved.
             * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
             *
             * This code is free software; you can redistribute it and/or modify it
             * under the terms of the GNU General Public License version 2 only, as
             * published by the Free Software Foundation.  Oracle designates this
             * particular file as subject to the \"Classpath\" exception as provided
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
            """;

    private static final String HEADER_TEXT =
            """
            package sun.util.locale;

            import java.util.HashMap;
            import java.util.Map;

            final class LocaleEquivalentMaps {

                static final Map<String, String> singleEquivMap;
                static final Map<String, String[]> multiEquivsMap;
                static final Map<String, String> regionVariantEquivMap;

            """;

    private static final String FOOTER_TEXT =
            """
                }

            }
            """;

    private static String getMapsText() {
        return  """
                    static {
                        singleEquivMap = HashMap.newHashMap(%s);
                        multiEquivsMap = HashMap.newHashMap(%s);
                        regionVariantEquivMap = HashMap.newHashMap(%s);

                """.formatted(
                sortedLanguageMap1.size(),
                sortedLanguageMap2.size(),
                sortedRegionVariantMap.size());
    }

    private static final String getLSRText(){
        return  """
                        // This is an auto-generated file and should not be manually edited.
                        //   LSR Revision: %s
                """.formatted(LSRrevisionDate);
    }

    private static void writeMultiEquiv(BufferedWriter writer) throws IOException {
        for (String key : sortedLanguageMap2.keySet()) {
            String[] values = sortedLanguageMap2.get(key);
            if (values.length >= 2) {
                writer.write(String.format(
                        "        multiEquivsMap.put(\"%s\", new String[] {%s});"
                        , key, generateValuesString(values)));
                writer.newLine();
            }
        }
    }

    // Use for writing an equivlancy map with one value
    private static void writeEquiv(BufferedWriter writer, String name, Map<String, String> map) throws IOException {
        for (String key : map.keySet()) {
            String value = map.get(key);
            writer.write(String.format(
                    "        %s.put(\"%s\", \"%s\");"
                    , name, key, value));
            writer.newLine();
        }
    }

    private static String generateValuesString(String[] values) {
        String outputStr = "";
        for (int i = 0; i < values.length; i++) {
            if (i != values.length - 1) {
                outputStr = String.format("%s\"%s\", ", outputStr, values[i]);
            } else {
                outputStr = String.format("%s\"%s\"", outputStr, values[i]);
            }

        }
        return outputStr;
    }
}
