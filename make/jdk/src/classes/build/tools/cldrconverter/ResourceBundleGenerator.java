/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.cldrconverter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;

class ResourceBundleGenerator implements BundleGenerator {
    // preferred timezones - keeping compatibility with JDK1.1 3 letter abbreviations
    private static final String[] preferredTZIDs = {
        "America/Los_Angeles",
        "America/Denver",
        "America/Phoenix",
        "America/Chicago",
        "America/New_York",
        "America/Indianapolis",
        "Pacific/Honolulu",
        "America/Anchorage",
        "America/Halifax",
        "America/Sitka",
        "America/St_Johns",
        "Europe/Paris",
        // Although CLDR does not support abbreviated zones, handle "GMT" as a
        // special case here, as it is specified in the javadoc.
        "GMT",
        "Africa/Casablanca",
        "Asia/Jerusalem",
        "Asia/Tokyo",
        "Europe/Bucharest",
        "Asia/Shanghai",
        "UTC",
    };

    // For duplicated values
    private static final String META_VALUE_PREFIX = "metaValue_";

    @Override
    public void generateBundle(String packageName, String baseName, String localeID, boolean useJava,
                               Map<String, ?> map, BundleType type) throws IOException {
        String suffix = useJava ? ".java" : ".properties";
        String dirName = CLDRConverter.DESTINATION_DIR + File.separator + "sun" + File.separator
                + packageName + File.separator + "resources" + File.separator + "cldr";
        packageName = packageName + ".resources.cldr";

        if (CLDRConverter.isBaseModule ^ isBaseLocale(localeID)) {
            return;
        }

        // Assume that non-base resources go into jdk.localedata
        if (!CLDRConverter.isBaseModule) {
            dirName = dirName + File.separator + "ext";
            packageName = packageName + ".ext";
        }

        File dir = new File(dirName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, baseName + ("root".equals(localeID) ? "" : "_" + localeID) + suffix);
        if (!file.exists()) {
            file.createNewFile();
        }
        CLDRConverter.info("\tWriting file " + file);

        String encoding;
        if (useJava) {
            if (CLDRConverter.USE_UTF8) {
                encoding = "utf-8";
            } else {
                encoding = "us-ascii";
            }
        } else {
            encoding = "iso-8859-1";
        }

        Formatter fmt = null;
        if (type == BundleType.TIMEZONE) {
            fmt = new Formatter();
            Set<String> metaKeys = new HashSet<>();
            for (String key : map.keySet()) {
                if (key.startsWith(CLDRConverter.METAZONE_ID_PREFIX)) {
                    String meta = key.substring(CLDRConverter.METAZONE_ID_PREFIX.length());
                    String[] value;
                    value = (String[]) map.get(key);
                    fmt.format("        final String[] %s = new String[] {\n", meta);
                    for (String s : value) {
                        fmt.format("               \"%s\",\n", CLDRConverter.saveConvert(s, useJava));
                    }
                    fmt.format("            };\n");
                    metaKeys.add(key);
                }
            }
            for (String key : metaKeys) {
                map.remove(key);
            }

            // Make it preferred ordered
            LinkedHashMap<String, Object> newMap = new LinkedHashMap<>();
            for (String preferred : preferredTZIDs) {
                if (map.containsKey(preferred)) {
                    newMap.put(preferred, map.remove(preferred));
                } else if (("GMT".equals(preferred) || "UTC".equals(preferred)) &&
                           metaKeys.contains(CLDRConverter.METAZONE_ID_PREFIX+preferred)) {
                    newMap.put(preferred, preferred);
                }
            }
            newMap.putAll(map);
            map = newMap;
        } else {
            // generic reduction of duplicated values
            Map<String, Object> newMap = new LinkedHashMap<>(map);
            Map<BundleEntryValue, BundleEntryValue> dedup = new HashMap<>(map.size());
            for (Map.Entry<String, ?> entry : map.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                BundleEntryValue newEntry = new BundleEntryValue(key, val);
                BundleEntryValue oldEntry = dedup.putIfAbsent(newEntry, newEntry);
                if (oldEntry != null) {
                    if (oldEntry.meta()) {
                        if (fmt == null) {
                            fmt = new Formatter();
                        }
                        String metaVal = oldEntry.metaKey();
                        if (val instanceof String[] values) {
                            fmt.format("        final String[] %s = new String[] {\n", metaVal);
                            for (String s : values) {
                                fmt.format("            \"%s\",\n", CLDRConverter.saveConvert(s, useJava));
                            }
                            fmt.format("        };\n");
                        } else {
                            fmt.format("        final String %s = \"%s\";\n", metaVal, CLDRConverter.saveConvert((String)val, useJava));
                        }
                        newMap.put(oldEntry.key, oldEntry.metaKey());
                    }
                    newMap.put(key, oldEntry.metaKey());
                }
            }
            map = newMap;
        }

        try (PrintWriter out = new PrintWriter(file, encoding)) {
            // Output copyright headers
            out.println(getOpenJDKCopyright());
            out.println(CopyrightHeaders.getUnicodeCopyright());

            if (useJava) {
                out.println("package sun." + packageName + ";\n");
                out.printf("import %s;\n\n", type.getPathName());
                out.printf("public class %s%s extends %s {\n", baseName, "root".equals(localeID) ? "" : "_" + localeID, type.getClassName());

                out.println("    @Override\n" +
                            "    protected final Object[][] getContents() {");
                if (fmt != null) {
                    out.print(fmt.toString());
                }
                out.println("        final Object[][] data = new Object[][] {");
            }
            for (String key : map.keySet()) {
                if (useJava) {
                    Object value = map.get(key);
                    if (value == null) {
                        CLDRConverter.warning("null value for " + key);
                    } else if (value instanceof String) {
                        String valStr = (String)value;
                        if (type == BundleType.TIMEZONE &&
                            !key.startsWith(CLDRConverter.EXEMPLAR_CITY_PREFIX) ||
                            valStr.startsWith(META_VALUE_PREFIX)) {
                            out.printf("            { \"%s\", %s },\n", key, CLDRConverter.saveConvert(valStr, useJava));
                        } else {
                            out.printf("            { \"%s\", \"%s\" },\n", key, CLDRConverter.saveConvert(valStr, useJava));
                        }
                    } else if (value instanceof String[]) {
                        String[] values = (String[]) value;
                        out.println("            { \"" + key + "\",\n                new String[] {");
                        for (String s : values) {
                            out.println("                    \"" + CLDRConverter.saveConvert(s, useJava) + "\",");
                        }
                        out.println("                }\n            },");
                    } else {
                        throw new RuntimeException("unknown value type: " + value.getClass().getName());
                    }
                } else {
                    out.println(key + "=" + CLDRConverter.saveConvert((String) map.get(key), useJava));
                }
            }
            if (useJava) {
                out.println("        };\n        return data;\n    }\n}");
            }
        }
    }

    private static class BundleEntryValue {
        private final String key;
        private final Object value;
        private final int hashCode;
        private String metaKey;

        BundleEntryValue(String key, Object value) {
            this.key = Objects.requireNonNull(key);
            this.value = Objects.requireNonNull(value);
            if (value instanceof String) {
                hashCode = value.hashCode();
            } else if (value instanceof String[] arr) {
                hashCode = Arrays.hashCode(arr);
            } else {
                throw new InternalError("Expected a string or a string array");
            }
        }

        /**
         * mark the entry as meta
         * @return true if the entry was not meta before, false otherwise
         */
        public boolean meta() {
            if (metaKey == null) {
                metaKey = META_VALUE_PREFIX + key.replaceAll("[\\.-]", "_");
                return true;
            }
            return false;
        }

        public String metaKey() {
            return metaKey;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof BundleEntryValue entry) {
                if (value instanceof String s) {
                    return s.equals(entry.value);
                } else if (entry.value instanceof String[] otherVal) {
                    return Arrays.equals((String[]) value, otherVal);
                }
            }
            return false;
        }
    }

    @Override
    public void generateMetaInfo(Map<String, SortedSet<String>> metaInfo) throws IOException {
        String dirName = CLDRConverter.DESTINATION_DIR + File.separator + "sun" + File.separator + "util" +
            File.separator +
            (CLDRConverter.isBaseModule ? "cldr" + File.separator + File.separator :
                      "resources" + File.separator + "cldr" + File.separator + "provider" + File.separator);
        File dir = new File(dirName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String className =
            (CLDRConverter.isBaseModule ? "CLDRBaseLocaleDataMetaInfo" : "CLDRLocaleDataMetaInfo");
        File file = new File(dir, className + ".java");
        if (!file.exists()) {
            file.createNewFile();
        }
        CLDRConverter.info("Generating file " + file);

        try (PrintWriter out = new PrintWriter(file, "us-ascii")) {
            out.printf(getOpenJDKCopyright());

            out.printf("""
                package sun.util.%s;

                import java.util.HashMap;
                import java.util.Locale;
                import java.util.Map;
                import sun.util.locale.provider.LocaleDataMetaInfo;
                import sun.util.locale.provider.LocaleProviderAdapter;

                public class %s implements LocaleDataMetaInfo {
                """,
                    CLDRConverter.isBaseModule ? "cldr" : "resources.cldr.provider",
                    className);

            if (CLDRConverter.isBaseModule) {
                out.printf("""
                        private static final Map<Locale, String[]> parentLocalesMap = HashMap.newHashMap(%d);
                        private static final Map<String, String> languageAliasMap = HashMap.newHashMap(%d);
                        static final boolean nonlikelyScript = %s; // package access from CLDRLocaleProviderAdapter

                        static {
                    """.formatted(
                        metaInfo.keySet().stream().filter(k -> k.startsWith(CLDRConverter.PARENT_LOCALE_PREFIX)).count(),
                        CLDRConverter.handlerSupplMeta.getLanguageAliasData().size(),
                        Boolean.valueOf(CLDRConverter.nonlikelyScript)));

                for (String key : metaInfo.keySet()) {
                    if (key.startsWith(CLDRConverter.PARENT_LOCALE_PREFIX)) {
                        String parentTag = key.substring(CLDRConverter.PARENT_LOCALE_PREFIX.length());
                        if ("root".equals(parentTag)) {
                            out.printf("        parentLocalesMap.put(Locale.ROOT,\n");
                        } else {
                            out.printf("        parentLocalesMap.put(Locale.forLanguageTag(\"%s\"),\n",
                                    parentTag);
                        }
                        generateStringArray(metaInfo.get(key), out);
                    }
                }
                out.println();

                // for languageAliasMap
                CLDRConverter.handlerSupplMeta.getLanguageAliasData().forEach((key, value) -> {
                    out.printf("        languageAliasMap.put(\"%s\", \"%s\");\n", key, value);
                });
                out.printf("    }\n\n");

                // end of static initializer block.

                // Delayed initialization section
                out.printf("""
                               private static class CLDRMapHolder {
                                   private static final Map<String, String> tzCanonicalIDMap = HashMap.newHashMap(%d);
                                   private static final Map<String, String> likelyScriptMap = HashMap.newHashMap(%d);

                                   static {
                           """, CLDRConverter.handlerTimeZone.getData().size(),
                                metaInfo.keySet().stream().filter(k -> k.startsWith(CLDRConverter.LIKELY_SCRIPT_PREFIX)).count());
                CLDRConverter.handlerTimeZone.getData().entrySet().stream()
                    .forEach(e -> {
                        String[] ids = ((String)e.getValue()).split("\\s");
                        out.printf("            tzCanonicalIDMap.put(\"%s\", \"%s\");\n", e.getKey(),
                            ids[0]);
                        for (int i = 1; i < ids.length; i++) {
                            out.printf("            tzCanonicalIDMap.put(\"%s\", \"%s\");\n", ids[i],
                                ids[0]);
                        }
                    });
                out.println();

                // for likelyScript map
                for (String key : metaInfo.keySet()) {
                    if (key.startsWith(CLDRConverter.LIKELY_SCRIPT_PREFIX)) {
                        // ensure spaces at the begin/end for delimiting purposes
                        out.printf("            likelyScriptMap.put(\"%s\", \"%s\");\n",
                                key.substring(CLDRConverter.LIKELY_SCRIPT_PREFIX.length()),
                                " " + metaInfo.get(key).stream().collect(Collectors.joining(" ")) + " ");
                    }
                }
                out.printf("        }\n    }\n");
            }
            out.println();

            out.printf("""
                    @Override
                    public LocaleProviderAdapter.Type getType() {
                        return LocaleProviderAdapter.Type.CLDR;
                    }

                    @Override
                    public String availableLanguageTags(String category) {
                        return " %s";
                    }
                """,
                toLocaleList(applyLanguageAliases(metaInfo.get("AvailableLocales")), false));

            if(CLDRConverter.isBaseModule) {
                out.printf("""

                    @Override
                    public Map<String, String> getLanguageAliasMap() {
                        return languageAliasMap;
                    }

                    @Override
                    public Map<String, String> tzCanonicalIDs() {
                        return CLDRMapHolder.tzCanonicalIDMap;
                    }

                    public Map<Locale, String[]> parentLocales() {
                        return parentLocalesMap;
                    }

                    // package access from CLDRLocaleProviderAdapter
                    Map<String, String> likelyScriptMap() {
                        return CLDRMapHolder.likelyScriptMap;
                    }
                """);
            }
            out.printf("}\n");
        }
    }

    private static void generateStringArray(SortedSet<String> set, PrintWriter out) throws IOException {
        String[] children = toLocaleList(set, true).split(" ");
        Arrays.sort(children);
        out.printf("            new String[] {\n" +
                "                ");
        int count = 0;
        for (int i = 0; i < children.length; i++) {
            String child = children[i];
            out.printf("\"%s\", ", child);
            count += child.length() + 4;
            if (i != children.length - 1 && count > 64) {
                out.printf("\n                ");
                count = 0;
            }
        }
        out.printf("\n            });\n");
    }

    private static final Locale.Builder LOCALE_BUILDER = new Locale.Builder();
    private static boolean isBaseLocale(String localeID) {
        localeID = localeID.replaceAll("-", "_");
        Locale locale = LOCALE_BUILDER
                            .clear()
                            .setLanguage(CLDRConverter.getLanguageCode(localeID))
                            .setRegion(CLDRConverter.getRegionCode(localeID))
                            .setScript(CLDRConverter.getScriptCode(localeID))
                            .build();
        return CLDRConverter.BASE_LOCALES.contains(locale);
    }

    private static String toLocaleList(SortedSet<String> set, boolean all) {
        StringBuilder sb = new StringBuilder(set.size() * 6);
        for (String id : set) {
            if (!"root".equals(id)) {
                if (!all && CLDRConverter.isBaseModule ^ isBaseLocale(id)) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(id);
            }
        }
        return sb.toString();
    }

    private static SortedSet<String> applyLanguageAliases(SortedSet<String> tags) {
        CLDRConverter.handlerSupplMeta.getLanguageAliasData().forEach((key, value) -> {
            if (tags.remove(key)) {
                tags.add(value);
            }
        });
        return tags;
    }

    private static String getOpenJDKCopyright() {
        if (CLDRConverter.jdkHeaderTemplate != null) {
            return String.format(CLDRConverter.jdkHeaderTemplate, CLDRConverter.copyrightYear);
        } else {
            return CopyrightHeaders.getOpenJDKCopyright(CLDRConverter.copyrightYear);
        }
    }
}
