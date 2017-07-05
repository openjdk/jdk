/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.docs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.module.ModuleFinder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Build tool to generate the docs bundle index page.
 */
public class GenDocsBundlePage {
    private static String DOCS_BUNDLE_PAGE = "docs-bundle-page.html";
    private static String MODULE_GROUPS_PROPS = "docs-module-groups.properties";

    private static String USAGE =
        "GenDocsBundlePage --output <file path> --title <title>" +
        "                  [--template <template>]";

    public static void main(String... args) throws IOException {
        String title = null;
        Path outputfile = null;
        Path template = null;
        for (int i=0; i < args.length; i++) {
            String option = args[i];
            if (option.equals("--output")) {
                outputfile = Paths.get(getArgument(args, option, ++i));
            } else if (option.equals("--title")) {
                title = getArgument(args, option, ++i);
            } else if (option.equals("--template")) {
                template = Paths.get(getArgument(args, option, ++i));
            } else if (option.startsWith("-")) {
                throw new IllegalArgumentException("Invalid option: " + option);
            }
        }

        if (outputfile == null) {
            System.err.println("ERROR: must specify --output option");
            System.exit(1);
        }
        if (title == null) {
            System.err.println("ERROR: must specify --title option");
            System.exit(1);
        }

        try (InputStream is = readTemplate(template);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is)))
        {
            new GenDocsBundlePage(title, outputfile).run(reader);
        }
    }

    private static String getArgument(String[] args, String option, int index) {
        if (index < args.length) {
            return args[index];
        }
        throw new IllegalArgumentException("Argument must be specified for " + option);
    }

    private static InputStream readTemplate(Path template) throws IOException {
        if (template != null) {
            return Files.newInputStream(template);
        } else {
            return GenDocsBundlePage.class.getResourceAsStream(DOCS_BUNDLE_PAGE);
        }
    }

    private static final String HEADER_TITLE = "@HEADER_TITLE@";
    final Path outputfile;
    final String title;
    final Map<String, String> moduleGroups;

    GenDocsBundlePage(String title, Path outputfile) throws IOException
    {
        this.outputfile = outputfile;
        this.title = title;
        this.moduleGroups = moduleGroups();
    }

    static Map<String, String> moduleGroups() throws IOException {
        ModuleFinder finder = ModuleFinder.ofSystem();
        Map<String, String> groups = new HashMap<>();
        try (InputStream in = GenDocsBundlePage.class.getResourceAsStream(MODULE_GROUPS_PROPS)) {
            Properties props = new Properties();
            props.load(in);
            for (String key: props.stringPropertyNames()) {
                Set<String> mods = Stream.of(props.getProperty(key).split("\\s+"))
                                         .filter(mn -> finder.find(mn).isPresent())
                                         .map(String::trim)
                                         .collect(Collectors.toSet());

                // divide into 3 columns: Java SE, JDK, JavaFX
                StringBuilder sb = new StringBuilder();
                sb.append(mods.stream()
                              .filter(mn -> mn.startsWith("java."))
                              .sorted()
                              .map(GenDocsBundlePage::toHRef)
                              .collect(Collectors.joining("\n")));
                sb.append("</td>\n<td>")
                  .append(mods.stream()
                              .filter(mn -> mn.startsWith("jdk."))
                              .sorted()
                              .map(GenDocsBundlePage::toHRef)
                              .collect(Collectors.joining("\n")));
                sb.append("</td>\n<td>");
                if (mods.stream().anyMatch(mn -> mn.startsWith("javafx."))) {
                    sb.append(mods.stream()
                                  .filter(mn -> mn.startsWith("javafx."))
                                  .sorted()
                                  .map(GenDocsBundlePage::toHRef)
                                  .collect(Collectors.joining("\n")));
                }
                String name = "@" + key.toUpperCase(Locale.ENGLISH) + "@";
                groups.put(name, sb.toString());
            }
        }
        return groups;
    }

    static String toHRef(String mn) {
        return String.format("<a href=\"api/%s-summary.html\">%s</a><br>", mn, mn);
    }

    void run(BufferedReader reader) throws IOException {
        if (Files.notExists(outputfile.getParent())) {
            Files.createDirectories(outputfile.getParent());
        }
        try (BufferedWriter bw = Files.newBufferedWriter(outputfile, StandardCharsets.UTF_8);
             PrintWriter writer = new PrintWriter(bw)) {
            reader.lines().map(this::genOutputLine)
                  .forEach(writer::println);
        }
    }

    String genOutputLine(String line) {
        if (line.contains(HEADER_TITLE)) {
            line = line.replace(HEADER_TITLE, title);
        }
        if (line.contains("@")) {
            for (Map.Entry<String,String> e: moduleGroups.entrySet()) {
                if (line.contains(e.getKey())) {
                    line = line.replace(e.getKey(), e.getValue());
                }
            }
        }
        return line;
    }
}
