/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @test
 * @summary Checks that all ProblemList files under set of directories have a valid format
 *          and that each problem list entry refers to an existing test file.
 * @run main CheckProblemLists ../../..
 */
public class CheckProblemLists {

    private static final Set<String> OPERATING_SYSTEMS = Set.of("aix", "generic", "linux", "macosx", "windows");
    private static final Set<String> ARCHITECTURES = Set.of("aarch64", "all", "i586", "ppc64", "ppc64le", "s390x", "x64");
    private static final Pattern BUG_NUMBER = Pattern.compile("(?:[A-Z]+-)?(\\d+)");

    record Error(Path path, int lineNum, String message, String line) {
        @Override
        public String toString() {
            return String.format("%s:%d: %s%n%s", path, lineNum, message, line);
        }
    }

    record TestAndIssue(String test, String issueId) {
    }

    /**
     * Searches for test roots (directories containing "TEST.ROOT" files) and problem lists (files
     * whose name starts with "ProblemList"). Relative paths in `args` are resolved against the
     * value of the test.src system property.
     *
     * All problem list files are then checked against the discovered set of test roots.
     */
    public static void main(String[] args) throws Exception {
        List<Path> problemLists = new ArrayList<>();
        Set<Path> testRoots = new HashSet<>();

        scan(args, problemLists, testRoots);
        String[] extraTestRoots = System.getProperty("extraTestRoots", "").split(":");
        if (extraTestRoots.length != 0) {
            scan(extraTestRoots, null, testRoots);
        }

        problemLists.sort(Path::compareTo);
        ArrayList<Error> errors = new ArrayList<>();
        for (Path problemList : problemLists) {
            System.out.printf("Checking %s%n", problemList.normalize());
            new CheckProblemLists(problemList, testRoots, errors).parse();
        }

        System.out.printf("Checked %d problem list files%n", problemLists.size());
        if (!errors.isEmpty()) {
            System.out.println("Test roots:");
            for (Path testRoot : testRoots) {
                System.out.printf("  %s%n", testRoot);
            }
            System.out.println("Following errors found:");
            for (Error error : errors) {
                System.out.printf("%s%n%n", error);
            }
            throw new AssertionError("%d errors found while checking %d problem list files"
                                     .formatted(errors.size(), problemLists.size()));
        }
    }

    static void scan(String[] args, List<Path> problemLists, Set<Path> testRoots) throws IOException {
        for (String arg : args) {
            Path testDir = Path.of(arg);
            if (!testDir.isAbsolute()) {
                String testSrc = Objects.requireNonNull(System.getProperty("test.src"),
                 "test.src property is required to resolve relative path: " + testDir);
                testDir = Path.of(testSrc).resolve(testDir);
            }
            Files.walk(testDir.normalize()).forEach(path -> {
                String fileName = path.toFile().getName();
                if (fileName.startsWith("ProblemList")) {
                    if (problemLists != null) {
                        problemLists.add(path);
                    }
                } else if (fileName.equals("TEST.ROOT")) {
                    testRoots.add(path.getParent());
                }
            });
        }
    }

    private CheckProblemLists(Path path, Set<Path> testRoots, List<Error> errors) {
        this.path = path;
        this.testRoots = testRoots;
        this.errors = errors;
    }

    final Path path;
    final Set<Path> testRoots;
    final List<Error> errors;
    final Map<TestAndIssue, String> seen = new HashMap<>();

    int lineNum = 1;
    String line;

    void error(String format, Object... args) {
        errors.add(new Error(path, lineNum, format.formatted(args), line));
    }

    boolean check(boolean condition, String format, Object... args) {
        if (!condition) {
            error(format, args);
            return false;
        }
        return true;
    }

    void parse() throws IOException {
        Files.lines(path).forEach(l -> {
            line = l.trim();
            if (!line.isEmpty() && line.charAt(0) != '#') {
                var fields = line.split("\\s+");
                if (!check(fields.length >= 3, "expected 3 or more fields, got %d", fields.length)) {
                    return;
                }
                // Check platforms field
                String platforms = fields[2];
                for (var platform : platforms.split(",")) {
                    if (!check(platform.contains("-"), "platform should be <os>-<arch>: %s", platform)) {
                        return;
                    }
                    var parts = platform.split("-", 2);
                    String os = parts[0];
                    String arch = parts[1];
                    if (!check(OPERATING_SYSTEMS.contains(os), "unknown os: %", os)) {
                        return;
                    }
                    if (!check(ARCHITECTURES.contains(arch), "unknown arch: %s", arch)) {
                        return;
                    }
                }

                String test = fields[0];
                Path testPath = checkTestExists(test);
                if (testPath == null) {
                    error("%s does not exist under any test root", test);
                    return;
                }

                String issueIds = fields[1];
                for (String issueId : issueIds.split(",")) {
                    Matcher matcher = BUG_NUMBER.matcher(issueId);
                    if (!check(matcher.matches(), "issue id does not match %s: %s", BUG_NUMBER.pattern(), issueId)) {
                        continue;
                    }
                    TestAndIssue testAndIssue = new TestAndIssue(test, matcher.group(1));
                    String where = seen.get(testAndIssue);
                    if (!check(where == null, "%s duplicates %s", testAndIssue, where)) {
                        return;
                    }
                    seen.put(testAndIssue, "%s:%d".formatted(path, lineNum));
                }
            }

            lineNum++;
        });
    }

    /**
     * Checks that a source file corresponding to `test` exists under a test root.
     */
    private Path checkTestExists(String test) {
        String testFile = test.contains("#") ? test.split("#")[0] : test;
        for (Path testRoot : testRoots) {
            Path testPath = testRoot.resolve(testFile);
            if (Files.exists(testPath)) {
                return testPath;
            }
        }
        return null;
    }
}
