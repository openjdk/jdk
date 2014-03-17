/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Formatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

class ResourceBundleGenerator implements BundleGenerator {
    @Override
    public void generateBundle(String packageName, String baseName, String localeID, boolean useJava,
                               Map<String, ?> map, BundleType type) throws IOException {
        String suffix = useJava ? ".java" : ".properties";
        String lang = CLDRConverter.getLanguageCode(localeID);
        String dirName = CLDRConverter.DESTINATION_DIR + File.separator + "sun" + File.separator
                + packageName + File.separator + "resources" + File.separator + "cldr";
        if (lang.length() > 0) {
            dirName = dirName + File.separator + lang;
            packageName = packageName + ".resources.cldr." + lang;
        } else {
            packageName = packageName + ".resources.cldr";
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
        }

        try (PrintWriter out = new PrintWriter(file, encoding)) {
            // Output copyright headers
            out.println(CopyrightHeaders.getOpenJDKCopyright());
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
                        if (type == BundleType.TIMEZONE) {
                            out.printf("            { \"%s\", %s },\n", key, CLDRConverter.saveConvert((String) value, useJava));
                        } else {
                            out.printf("            { \"%s\", \"%s\" },\n", key, CLDRConverter.saveConvert((String) value, useJava));
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

    private static final String METAINFO_CLASS = "CLDRLocaleDataMetaInfo";

    @Override
    public void generateMetaInfo(Map<String, SortedSet<String>> metaInfo) throws IOException {
        String dirName = CLDRConverter.DESTINATION_DIR + File.separator + "sun" + File.separator + "util" + File.separator
                + "cldr" + File.separator;
        File dir = new File(dirName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, METAINFO_CLASS + ".java");
        if (!file.exists()) {
            file.createNewFile();
        }
        CLDRConverter.info("Generating file " + file);

        try (PrintWriter out = new PrintWriter(file, "us-ascii")) {
            out.println(CopyrightHeaders.getOpenJDKCopyright());

            out.println("package sun.util.cldr;\n\n"
                      + "import java.util.ListResourceBundle;\n");
            out.printf("public class %s extends ListResourceBundle {\n", METAINFO_CLASS);
            out.println("    @Override\n" +
                        "    protected final Object[][] getContents() {\n" +
                        "        final Object[][] data = new Object[][] {");
            for (String key : metaInfo.keySet()) {
                out.printf("            { \"%s\",\n", key);
                out.printf("              \"%s\" },\n", toLocaleList(metaInfo.get(key)));
            }
            out.println("        };\n        return data;\n    }\n}");
        }
    }

    private static String toLocaleList(SortedSet<String> set) {
        StringBuilder sb = new StringBuilder(set.size() * 6);
        for (String id : set) {
            if (!"root".equals(id)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(id);
            }
        }
        return sb.toString();
    }
}
