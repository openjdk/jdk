/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.test.framework;

import static jdk.nashorn.internal.test.framework.TestConfig.OPTIONS_CHECK_COMPILE_MSG;
import static jdk.nashorn.internal.test.framework.TestConfig.OPTIONS_COMPARE;
import static jdk.nashorn.internal.test.framework.TestConfig.OPTIONS_EXPECT_COMPILE_FAIL;
import static jdk.nashorn.internal.test.framework.TestConfig.OPTIONS_EXPECT_RUN_FAIL;
import static jdk.nashorn.internal.test.framework.TestConfig.OPTIONS_FORK;
import static jdk.nashorn.internal.test.framework.TestConfig.OPTIONS_IGNORE_STD_ERROR;
import static jdk.nashorn.internal.test.framework.TestConfig.OPTIONS_RUN;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_FAILED_LIST_FILE;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_ENABLE_STRICT_MODE;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_EXCLUDES_FILE;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_EXCLUDE_DIR;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_EXCLUDE_LIST;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_FRAMEWORK;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_INCLUDES;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_LIST;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_ROOTS;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_UNCHECKED_DIR;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Utility class to find/parse script test files and to create 'test' instances.
 * Actual 'test' object type is decided by clients of this class.
 */
final class TestFinder {
    private TestFinder() {}

    interface TestFactory<T> {
        // 'test' instance type is decided by the client.
        T createTest(final String framework, final File testFile, final List<String> engineOptions, final Map<String, String> testOptions, final List<String> arguments);
        // place to log messages from TestFinder
        void log(String mg);
    }


    // finds all tests from configuration and calls TestFactory to create 'test' instance for each script test found
    static <T> void findAllTests(final List<T> tests, final Set<String> orphans, final TestFactory<T> testFactory) throws Exception {
        final String framework = System.getProperty(TEST_JS_FRAMEWORK);
        final String testList = System.getProperty(TEST_JS_LIST);
        final String failedTestFileName = System.getProperty(TEST_FAILED_LIST_FILE);
        if(failedTestFileName != null) {
            File failedTestFile = new File(failedTestFileName);
            if(failedTestFile.exists() && failedTestFile.length() > 0L) {
                try(final BufferedReader r = new BufferedReader(new FileReader(failedTestFile))) {
                    for(;;) {
                        final String testFileName = r.readLine();
                        if(testFileName == null) {
                            break;
                        }
                        handleOneTest(framework, new File(testFileName).toPath(), tests, orphans, testFactory);
                    }
                }
                return;
            }
        }
        if (testList == null || testList.length() == 0) {
            // Run the tests under the test roots dir, selected by the
            // TEST_JS_INCLUDES patterns
            final String testRootsString = System.getProperty(TEST_JS_ROOTS, "test/script");
            if (testRootsString == null || testRootsString.length() == 0) {
                throw new Exception("Error: " + TEST_JS_ROOTS + " must be set");
            }
            final String testRoots[] = testRootsString.split(" ");
            final FileSystem fileSystem = FileSystems.getDefault();
            final Set<String> testExcludeSet = getExcludeSet();
            final Path[] excludePaths = getExcludeDirs();
            for (final String root : testRoots) {
                final Path dir = fileSystem.getPath(root);
                findTests(framework, dir, tests, orphans, excludePaths, testExcludeSet, testFactory);
            }
        } else {
            // TEST_JS_LIST contains a blank speparated list of test file names.
            final String strArray[] = testList.split(" ");
            for (final String ss : strArray) {
                handleOneTest(framework, new File(ss).toPath(), tests, orphans, testFactory);
            }
        }
    }

    private static boolean inExcludePath(final Path file, final Path[] excludePaths) {
        if (excludePaths == null) {
            return false;
        }

        for (final Path excludePath : excludePaths) {
            if (file.startsWith(excludePath)) {
                return true;
            }
        }
        return false;
    }

    private static <T> void findTests(final String framework, final Path dir, final List<T> tests, final Set<String> orphanFiles, final Path[] excludePaths, final Set<String> excludedTests, final TestFactory<T> factory) throws Exception {
        final String pattern = System.getProperty(TEST_JS_INCLUDES);
        final String extension = pattern == null ? "js" : pattern;
        final Exception[] exceptions = new Exception[1];
        final List<String> excludedActualTests = new ArrayList<>();

        if (! dir.toFile().isDirectory()) {
            factory.log("WARNING: " + dir + " not found or not a directory");
        }

        Files.walkFileTree(dir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                final String fileName = file.getName(file.getNameCount() - 1).toString();
                if (fileName.endsWith(extension)) {
                    final String namex = file.toString().replace('\\', '/');
                    if (!inExcludePath(file, excludePaths) && !excludedTests.contains(file.getFileName().toString())) {
                        try {
                            handleOneTest(framework, file, tests, orphanFiles, factory);
                        } catch (final Exception ex) {
                            exceptions[0] = ex;
                            return FileVisitResult.TERMINATE;
                        }
                    } else {
                        excludedActualTests.add(namex);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(excludedActualTests);

        for (final String excluded : excludedActualTests) {
            factory.log("Excluding " + excluded);
        }

        if (exceptions[0] != null) {
            throw exceptions[0];
        }
    }

    private static final String uncheckedDirs[] = System.getProperty(TEST_JS_UNCHECKED_DIR, "test/script/external/test262/").split(" ");

    private static boolean isUnchecked(final Path testFile) {
        for (final String uncheckedDir : uncheckedDirs) {
            if (testFile.startsWith(uncheckedDir)) {
                return true;
            }
        }
        return false;
    }

    private static <T> void handleOneTest(final String framework, final Path testFile, final List<T> tests, final Set<String> orphans, TestFactory<T> factory) throws Exception {
        final String name = testFile.getFileName().toString();

        assert name.lastIndexOf(".js") > 0 : "not a JavaScript: " + name;

        // defaults: testFile is a test and should be run
        boolean isTest = isUnchecked(testFile);
        boolean isNotTest = false;
        boolean shouldRun = true;
        boolean compileFailure = false;
        boolean runFailure = false;
        boolean checkCompilerMsg = false;
        boolean noCompare = false;
        boolean ignoreStdError = false;
        boolean fork = false;

        final List<String> engineOptions = new ArrayList<>();
        final List<String> scriptArguments = new ArrayList<>();
        boolean inComment = false;

        try (Scanner scanner = new Scanner(testFile)) {
            while (scanner.hasNext()) {
                // TODO: Scan for /ref=file qualifiers, etc, to determine run
                // behavior
                String token = scanner.next();
                if (token.startsWith("/*")) {
                    inComment = true;
                } else if (token.endsWith(("*/"))) {
                    inComment = false;
                } else if (!inComment) {
                    continue;
                }

                // remove whitespace and trailing semicolons, if any
                // (trailing semicolons are found in some sputnik tests)
                token = token.trim();
                final int semicolon = token.indexOf(';');
                if (semicolon > 0) {
                    token = token.substring(0, semicolon);
                }
                switch (token) {
                case "@test":
                    isTest = true;
                    break;
                case "@test/fail":
                    isTest = true;
                    compileFailure = true;
                    break;
                case "@test/compile-error":
                    isTest = true;
                    compileFailure = true;
                    checkCompilerMsg = true;
                    shouldRun = false;
                    break;
                case "@test/warning":
                    isTest = true;
                    checkCompilerMsg = true;
                    break;
                case "@test/nocompare":
                    isTest = true;
                    noCompare = true;
                    break;
                case "@subtest":
                    isTest = false;
                    isNotTest = true;
                    break;
                case "@runif":
                    if (System.getProperty(scanner.next()) != null) {
                        shouldRun = true;
                    } else {
                        isTest = false;
                        isNotTest = true;
                    }
                    break;
                case "@run":
                    shouldRun = true;
                    break;
                case "@run/fail":
                    shouldRun = true;
                    runFailure = true;
                    break;
                case "@run/ignore-std-error":
                    shouldRun = true;
                    ignoreStdError = true;
                    break;
                case "@argument":
                    scriptArguments.add(scanner.next());
                    break;
                case "@option":
                    engineOptions.add(scanner.next());
                    break;
                case "@fork":
                    fork = true;
                    break;
                }

                // negative tests are expected to fail at runtime only
                // for those tests that are expected to fail at compile time,
                // add @test/compile-error
                if (token.equals("@negative") || token.equals("@strict_mode_negative")) {
                    shouldRun = true;
                    runFailure = true;
                }

                if (token.equals("@strict_mode") || token.equals("@strict_mode_negative") || token.equals("@onlyStrict") || token.equals("@noStrict")) {
                    if (!strictModeEnabled()) {
                        return;
                    }
                }
            }
        } catch (final Exception ignored) {
            return;
        }

        if (isTest) {
            final Map<String, String> testOptions = new HashMap<>();
            if (compileFailure) {
                testOptions.put(OPTIONS_EXPECT_COMPILE_FAIL, "true");
            }
            if (shouldRun) {
                testOptions.put(OPTIONS_RUN, "true");
            }
            if (runFailure) {
                testOptions.put(OPTIONS_EXPECT_RUN_FAIL, "true");
            }
            if (checkCompilerMsg) {
                testOptions.put(OPTIONS_CHECK_COMPILE_MSG, "true");
            }
            if (!noCompare) {
                testOptions.put(OPTIONS_COMPARE, "true");
            }
            if (ignoreStdError) {
                testOptions.put(OPTIONS_IGNORE_STD_ERROR, "true");
            }
            if (fork) {
                testOptions.put(OPTIONS_FORK, "true");
            }

            tests.add(factory.createTest(framework, testFile.toFile(), engineOptions, testOptions, scriptArguments));
        } else if (!isNotTest) {
            orphans.add(name);
        }
    }

    private static boolean strictModeEnabled() {
        return Boolean.getBoolean(TEST_JS_ENABLE_STRICT_MODE);
    }

    private static Set<String> getExcludeSet() throws XPathExpressionException {
        final String testExcludeList = System.getProperty(TEST_JS_EXCLUDE_LIST);

        String[] testExcludeArray = {};
        if (testExcludeList != null) {
            testExcludeArray = testExcludeList.split(" ");
        }
        final Set<String> testExcludeSet = new HashSet<>(testExcludeArray.length);
        for (final String test : testExcludeArray) {
            testExcludeSet.add(test);
        }

        final String testExcludesFile = System.getProperty(TEST_JS_EXCLUDES_FILE);
        if (testExcludesFile != null && !testExcludesFile.isEmpty()) {
            try {
                loadExcludesFile(testExcludesFile, testExcludeSet);
            } catch (final XPathExpressionException e) {
                System.err.println("Error: unable to load test excludes from " + testExcludesFile);
                e.printStackTrace();
                throw e;
            }
        }
        return testExcludeSet;
    }

    private static void loadExcludesFile(final String testExcludesFile, final Set<String> testExcludeSet) throws XPathExpressionException {
        final XPath xpath = XPathFactory.newInstance().newXPath();
        final NodeList testIds = (NodeList)xpath.evaluate("/excludeList/test/@id", new InputSource(testExcludesFile), XPathConstants.NODESET);
        for (int i = testIds.getLength() - 1; i >= 0; i--) {
            testExcludeSet.add(testIds.item(i).getNodeValue());
        }
    }

    private static Path[] getExcludeDirs() {
        final String excludeDirs[] = System.getProperty(TEST_JS_EXCLUDE_DIR, "test/script/currently-failing").split(" ");
        final Path[] excludePaths = new Path[excludeDirs.length];
        final FileSystem fileSystem = FileSystems.getDefault();
        int i = 0;
        for (final String excludeDir : excludeDirs) {
            excludePaths[i++] = fileSystem.getPath(excludeDir);
        }
        return excludePaths;
    }
}
