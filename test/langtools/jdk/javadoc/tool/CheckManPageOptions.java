/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8274211 8278538
 * @summary Test man page that options are documented
 * @modules jdk.javadoc/jdk.javadoc.internal.tool:+open
 * @run main CheckManPageOptions
 */

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.StandardDoclet;
import jdk.javadoc.internal.tool.ToolOptions;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks the set of options found by fuzzy-parsing the troff or Markdown versions
 * of the javadoc man page against the set of options declared in the source code.
 */
public class CheckManPageOptions {
    static class SourceDirNotFound extends Error { }

    public static void main(String... args) throws Exception {
        try {
            new CheckManPageOptions().run(args);
        } catch (SourceDirNotFound e) {
            System.err.println("NOTE: Cannot find src directory; test skipped");
        }
    }

    static final PrintStream out = System.err;

    List<String> MISSING_IN_MAN_PAGE = List.of("--date");

    void run(String... args) throws Exception {
        var file = args.length == 0 ? findDefaultFile() : Path.of(args[0]);
        out.println("File: " + file);
        out.println();

        var manPageOptions = getManPageOptions(file);
        out.println("Man page options: " + manPageOptions);
        out.println();

        var toolOptions = getToolOptions();
        out.println("ToolOptions: " + toolOptions);
        out.println();

        var docletOptions = getDocletOptions();
        out.println("DocletOptions: " + docletOptions);
        out.println();

        var toolDocletOnly = new TreeSet<String>();
        toolDocletOnly.addAll(toolOptions);
        toolDocletOnly.addAll(docletOptions);
        toolDocletOnly.removeAll(manPageOptions);
        toolDocletOnly.removeAll(MISSING_IN_MAN_PAGE);
        if (!toolDocletOnly.isEmpty()) {
            error("The following options are defined by the tool or doclet, but not defined in the man page:\n"
                    + toSimpleList(toolDocletOnly));
        }

        var manPageOnly = new TreeSet<String>();
        manPageOnly.addAll(manPageOptions);
        manPageOnly.removeAll(toolOptions);
        manPageOnly.removeAll(docletOptions);
        if (!manPageOnly.isEmpty()) {
            error("The following options are defined in the man page, but not defined by the tool or doclet:\n"
                    + toSimpleList(manPageOnly));
        }

        if (!MISSING_IN_MAN_PAGE.isEmpty()) {
            var notMissing = new TreeSet<>(MISSING_IN_MAN_PAGE);
            notMissing.retainAll(manPageOptions);
            if (!notMissing.isEmpty()) {
                error("The following options were declared as missing, but were found on the man page:\n"
                        + toSimpleList(notMissing));
            }

            out.println("NOTE: the following options are currently excluded and need to be documented in the man page:");
            out.println(toSimpleList(MISSING_IN_MAN_PAGE));
        }

        if (errors > 0) {
            out.println(errors + " errors found");
            throw new Exception(errors + " errors found");
        }
    }

    int errors = 0;
    void error(String message) {
        ("Error: " + message).lines().forEach(out::println);
        errors++;
    }

    String toSimpleList(Collection<String> items) {
        return items.stream().collect(Collectors.joining(", ", "    ", ""));
    }

    Path findDefaultFile() {
        return findRootDir().resolve("src/jdk.javadoc/share/man/javadoc.1");
    }

    Path findRootDir() {
        Path dir = Path.of(System.getProperty("test.src", ".")).toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("src"))) {
                return dir;
            } else {
                Path openDir = dir.resolve("open");
                if (Files.exists(openDir.resolve("src"))) {
                    return openDir;
                }
            }
            dir = dir.getParent();
        }
        throw new SourceDirNotFound();
    }

    List<String> getToolOptions() throws Error {
        try {
            Class<ToolOptions> toolOptionsClass = ToolOptions.class;

            Constructor<ToolOptions> constr = toolOptionsClass.getDeclaredConstructor();
            constr.setAccessible(true);

            Method getSupportedOptions = toolOptionsClass.getMethod("getSupportedOptions");
            Class<?> toolOptionClass = List.of(toolOptionsClass.getDeclaredClasses()).stream()
                    .filter(c -> c.getSimpleName().equals("ToolOption"))
                    .findFirst()
                    .orElseThrow();

            Field kindField = toolOptionClass.getDeclaredField("kind");
            kindField.setAccessible(true);
            Method getNames = toolOptionClass.getDeclaredMethod("getNames");
            getNames.setAccessible(true);

            ToolOptions t = constr.newInstance();
            var list = new ArrayList<String>();
            var options = (List<?>) getSupportedOptions.invoke(t);
            for (var option : options) {
                Object kind = kindField.get(option);
                if (kind.toString().equals("HIDDEN")) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                var oNames = (List<String>) getNames.invoke(option);
                oNames.stream()
                        .filter(o -> !o.equals("@"))
                        .forEach(list::add);
            }
            return list;
        } catch (ReflectiveOperationException e) {
            throw new Error(e);
        }
    }

    List<String> getDocletOptions() {
        StandardDoclet d = new StandardDoclet();
        d.init(Locale.getDefault(), null);
        return getDocletOptions(d);
    }

    List<String> getDocletOptions(Doclet d) {
        return d.getSupportedOptions().stream()
                .filter(o -> o.getKind() != Doclet.Option.Kind.OTHER)
                .flatMap(o -> o.getNames().stream())
                .map(n -> n.replaceAll(":$", ""))
                .toList();
    }

    List<String> getManPageOptions(Path file) throws IOException {
        String page = Files.readString(file);
        String name = file.getFileName().toString();
        String extn = name.substring(name.lastIndexOf('.'));
        return switch (extn) {
            case ".1" -> parseNRoff(page);
            case ".md" -> parseMarkdown(page);
            default -> throw new IllegalArgumentException(file.toString());
        };
    }

    List<String> parseNRoff(String page) {
        var list = new ArrayList<String>();

        // In the troff man page, options are defined in one of two forms:
        // 1. options delegated to javac appear in pairs of lines of the form
        //      .IP \[bu] 2
        //      \f[CB]\-....
        // 2. options implemented by the tool or doclet appear in lines of the form
        //      .B \f[CB]\-...

        Pattern p1 = Pattern.compile("\\R" + Pattern.quote(".IP \\[bu] 2") + "\\R" + Pattern.quote("\\f[CB]\\-") + ".*");
        Pattern p2 = Pattern.compile("\\R" + Pattern.quote(".B \\f[CB]\\-") + ".*");
        Pattern outer = Pattern.compile("(" + p1.pattern() + "|" + p2.pattern() + ")");
        Matcher outerMatcher = outer.matcher(page);

        // In the defining areas, option names are represented as follows:
        //      \f[CB]OPTION\f[R] or \f[CB]OPTION:
        // where OPTION is the shortest string not containing whitespace or colon,
        // and in which all '-' characters are escaped with a single backslash.

        Pattern inner = Pattern.compile("\\s\\\\f\\[CB](\\\\-[^ :]+?)(:|\\\\f\\[R])");

        while (outerMatcher.find()) {
            String lines = outerMatcher.group();
            out.println("found:" + lines + "\n");

            Matcher innerMatcher = inner.matcher(lines);
            while (innerMatcher.find()) {
                String option = innerMatcher.group(1).replace("\\-", "-");
                list.add(option);
            }
        }

        return list;
    }

    List<String> parseMarkdown(String page) {
        var list = new ArrayList<String>();
        // In the Markdown man page, options are defined in one of two forms:
        // 1. options delegated to javac appear in lines of the form
        //      -   `-...
        // 2. options implemented by the tool or doclet appear in lines of the form
        //      `-...`

        Pattern p1 = Pattern.compile("\\R-   `-.*");
        Pattern p2 = Pattern.compile("\\R`-.*");
        Pattern outer = Pattern.compile("(" + p1.pattern() + "|" + p2.pattern() + ")");
        Matcher outerMatcher = outer.matcher(page);

        // In the defining areas, option names are represented as follows:
        //      `OPTION`
        // where OPTION is the shortest string not containing whitespace or colon
        Pattern inner = Pattern.compile("\\s`([^:`]+)");

        while (outerMatcher.find()) {
            String lines = outerMatcher.group();
            out.println("found:" + lines + "\n");

            Matcher innerMatcher = inner.matcher(lines);
            while (innerMatcher.find()) {
                String option = innerMatcher.group(1);
                list.add(option);
            }
        }

        return list;
    }
 }
