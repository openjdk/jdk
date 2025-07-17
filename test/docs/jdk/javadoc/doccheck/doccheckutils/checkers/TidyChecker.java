/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package doccheckutils.checkers;


import doccheckutils.FileChecker;
import doccheckutils.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jtreg.SkippedException;

public class TidyChecker implements FileChecker, AutoCloseable {
    private final Path TIDY;
    final Map<Pattern, Integer> counts = new HashMap<>();
    final Pattern okPattern = Pattern.compile("No warnings or errors were found.");
    final Pattern countPattern = Pattern.compile("([0-9]+) warnings, ([0-9]+) errors were found!.*?(Not all warnings/errors were shown.)?");
    final Pattern countPattern2 = Pattern.compile("Tidy found ([0-9]+) warning[s]? and ([0-9]+) error[s]?!.*?(Not all warnings/errors were shown.)?");
    final Pattern cssPattern = Pattern.compile("You are recommended to use CSS.*");
    final Pattern guardPattern = Pattern.compile("(line [0-9]+ column [0-9]+ - |[^:]+:[0-9]+:[0-9]+: )(Error|Warning):.*");

    final Pattern[] patterns = {
            Pattern.compile(".*Error: <.*> is not recognized!"),
            Pattern.compile(".*Error: missing quote mark for attribute value"),
            Pattern.compile(".*Warning: '<' \\+ '/' \\+ letter not allowed here"),
            Pattern.compile(".*Warning: <.*> anchor \".*\" already defined"),
            Pattern.compile(".*Warning: <.*> attribute \".*\" has invalid value \".*\""),
            Pattern.compile(".*Warning: <.*> attribute \".*\" lacks value"),
            Pattern.compile(".*Warning: <.*> attribute \".*\" lacks value"),
            Pattern.compile(".*Warning: <.*> attribute with missing trailing quote mark"),
            Pattern.compile(".*Warning: <.*> dropping value \".*\" for repeated attribute \".*\""),
            Pattern.compile(".*Warning: <.*> inserting \".*\" attribute"),
            Pattern.compile(".*Warning: <.*> is probably intended as </.*>"),
            Pattern.compile(".*Warning: <.*> isn't allowed in <.*> elements"),
            Pattern.compile(".*Warning: <.*> lacks \".*\" attribute"),
            Pattern.compile(".*Warning: <.*> missing '>' for end of tag"),
            Pattern.compile(".*Warning: <.*> proprietary attribute \".*\""),
            Pattern.compile(".*Warning: <.*> unexpected or duplicate quote mark"),
            Pattern.compile(".*Warning: <a> id and name attribute value mismatch"),
            Pattern.compile(".*Warning: <a> cannot copy name attribute to id"),
            Pattern.compile(".*Warning: <a> escaping malformed URI reference"),
            Pattern.compile(".*Warning: <blockquote> proprietary attribute \"pre\""),
            Pattern.compile(".*Warning: discarding unexpected <.*>"),
            Pattern.compile(".*Warning: discarding unexpected </.*>"),
            Pattern.compile(".*Warning: entity \".*\" doesn't end in ';'"),
            Pattern.compile(".*Warning: inserting implicit <.*>"),
            Pattern.compile(".*Warning: inserting missing 'title' element"),
            Pattern.compile(".*Warning: missing <!DOCTYPE> declaration"),
            Pattern.compile(".*Warning: missing <.*>"),
            Pattern.compile(".*Warning: missing </.*> before <.*>"),
            Pattern.compile(".*Warning: nested emphasis <.*>"),
            Pattern.compile(".*Warning: plain text isn't allowed in <.*> elements"),
            Pattern.compile(".*Warning: removing whitespace preceding XML Declaration"),
            Pattern.compile(".*Warning: replacing <p> (by|with) <br>"),
            Pattern.compile(".*Warning: replacing invalid numeric character reference .*"),
            Pattern.compile(".*Warning: replacing obsolete element <xmp> with <pre>"),
            Pattern.compile(".*Warning: replacing unexpected .* (by|with) </.*>"),
            Pattern.compile(".*Warning: trimming empty <.*>"),
            Pattern.compile(".*Warning: unescaped & or unknown entity \".*\""),
            Pattern.compile(".*Warning: unescaped & which should be written as &amp;"),
            Pattern.compile(".*Warning: using <br> in place of <p>"),
            Pattern.compile(".*Warning: <.*> element removed from HTML5"),
            Pattern.compile(".*Warning: <.*> attribute \".*\" not allowed for HTML5"),
            Pattern.compile(".*Warning: The summary attribute on the <table> element is obsolete in HTML5"),
            Pattern.compile(".*Warning: replacing invalid UTF-8 bytes \\(char. code U\\+.*\\)")
    };
    private final Log errors;
    private int files = 0;
    private int ok;
    private int warns;
    private int errs;
    private int css;
    private int overflow;

    public TidyChecker() {
        TIDY = initTidy();
        errors = new Log();
    }

    @Override
    public void checkFiles(List<Path> sb) {
        files += sb.size();
        try {
            for (int i = 0; i < sb.size(); i += 1024) {
                List<String> command = new ArrayList<>();
                command.add(TIDY.toString());
                command.add("-q");
                command.add("-e");
                command.add("--gnu-emacs");
                command.add("true");
                List<Path> sublist = sb.subList(i, Math.min(i + 1024, sb.size()));
                for (Path p : sublist) {
                    command.add(p.toString());
                }
                Process p = new ProcessBuilder()
                        .command(command)
                        .redirectErrorStream(true)
                        .start();
                try (BufferedReader r =
                             new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        checkLine(line);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    private Path initTidy() {
        Path tidyExePath;
        String tidyProperty = System.getProperty("tidy");
        if (tidyProperty != null) {
            tidyExePath = Path.of(tidyProperty);
            if (!Files.exists(tidyExePath)) {
                System.err.println("tidy not found: " + tidyExePath);
            }
            if (!Files.isExecutable(tidyExePath)) {
                System.err.println("tidy not executable: " + tidyExePath);
            }
        } else {
            boolean isWindows = System.getProperty("os.name")
                    .toLowerCase(Locale.US)
                    .startsWith("windows");
            String tidyExe = isWindows ? "tidy.exe" : "tidy";
            Optional<Path> p = Stream.of(System.getenv("PATH")
                            .split(File.pathSeparator))
                    .map(Path::of)
                    .map(d -> d.resolve(tidyExe))
                    .filter(Files::exists)
                    .filter(Files::isExecutable)
                    .findFirst();
            if (p.isPresent()) {
                tidyExePath = p.get();
            } else {
                throw new jtreg.SkippedException("tidy not found on PATH");
            }
        }

        try {
            Process p = new ProcessBuilder()
                    .command(tidyExePath.toString(), "-version")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader r =
                         new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                List<String> lines = r.lines().collect(Collectors.toList());
                // Look for a line containing "version" and a dotted identifier beginning 5.
                // If not found, look for known old/bad versions, to report in error message
                Pattern version = Pattern.compile("version.* [5678]\\.\\d+(\\.\\d+)");
                if (lines.stream().noneMatch(line -> version.matcher(line).find())) {
                    Pattern oldVersion = Pattern.compile("2006");  // 2006 implies old macOS version
                    String lineSep = System.lineSeparator();
                    String message = lines.stream().anyMatch(line -> oldVersion.matcher(line).find())
                            ? "old version of 'tidy' found on the PATH\n"
                            : "could not determine the version of 'tidy' on the PATH\n";
                    System.err.println(message + String.join(lineSep, lines));
                }
            }
        } catch (IOException e) {
            System.err.println("Could not execute 'tidy -version': " + e);
        }

        return tidyExePath;
    }

    @Override
    public void report() {
        if (files > 0) {
            System.err.println("Tidy found errors in the generated HTML");
            if (!errors.noErrors()) {
                for (String s : errors.getErrors()) {
                    System.err.println(s);
                }
                System.err.println("Tidy output end.");
                System.err.println();
                System.err.println();
                throw new RuntimeException("Tidy found errors in the generated HTML");
            }
        }
    }

    @Override
    public boolean isOK() {
        return (ok == files)
                && (overflow == 0)
                && (errs == 0)
                && (warns == 0)
                && (css == 0);
    }

    void checkLine(String line) {
        Matcher m;
        if (okPattern.matcher(line).matches()) {
            ok++;
        } else if ((m = countPattern.matcher(line)).matches() || (m = countPattern2.matcher(line)).matches()) {
            warns += Integer.parseInt(m.group(1));
            errs += Integer.parseInt(m.group(2));
            if (m.group(3) != null)
                overflow++;
        } else if (guardPattern.matcher(line).matches()) {
            boolean found = false;
            for (Pattern p : patterns) {
                if (p.matcher(line).matches()) {
                    errors.log("%s", line);
                    found = true;
                    count(p);
                    break;
                }
            }
            if (!found)
                errors.log("unrecognized line: " + line);
        } else if (cssPattern.matcher(line).matches()) {
            css++;
        }
    }

    void count(Pattern p) {
        Integer i = counts.get(p);
        counts.put(p, (i == null) ? 1 : i + 1);
    }

    @Override
    public void close() {
        report();
    }
}
