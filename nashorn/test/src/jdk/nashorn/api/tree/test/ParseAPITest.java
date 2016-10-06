/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.nashorn.api.tree.test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import jdk.nashorn.api.tree.Parser;
import jdk.nashorn.api.tree.SimpleTreeVisitorES5_1;
import jdk.nashorn.api.tree.Tree;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Test for nashorn Parser API (jdk.nashorn.api.tree.*)
 *
 * @test
 * @run testng jdk.nashorn.api.tree.test.ParseAPITest
 */
public class ParseAPITest {

    private static final boolean VERBOSE   = Boolean.valueOf(System.getProperty("parserapitest.verbose"));
    private static final boolean TEST262   = Boolean.valueOf(System.getProperty("parserapitest.test262"));

    private static final String TEST_BASIC_DIR;
    private static final String TEST_MAPTESTS_DIR;
    private static final String TEST_SANDBOX_DIR;
    private static final String TEST_TRUSTED_DIR;
    private static final String TEST262_SUITE_DIR;

    static {
        final String testSrc = System.getProperty("test.src");
        if (testSrc != null) {
            final String testScriptDir = testSrc + "/../../../../../../script/";
            TEST_BASIC_DIR    = testScriptDir + "basic";
            TEST_MAPTESTS_DIR = testScriptDir + "maptests";
            TEST_SANDBOX_DIR  = testScriptDir + "sandbox";
            TEST_TRUSTED_DIR  = testScriptDir + "trusted";
            TEST262_SUITE_DIR = testScriptDir + "external/test262/test/suite";
        } else {
            TEST_BASIC_DIR     = System.getProperty("test.basic.dir");
            TEST_MAPTESTS_DIR  = System.getProperty("test.maptests.dir");
            TEST_SANDBOX_DIR   = System.getProperty("test.sandbox.dir");
            TEST_TRUSTED_DIR   = System.getProperty("test.trusted.dir");
            TEST262_SUITE_DIR  = System.getProperty("test262.suite.dir");
        }
    }

    interface TestFilter {
        public boolean exclude(File file, String content);
    }

    private void log(final String msg) {
        org.testng.Reporter.log(msg, true);
    }

    private static final String[] options = new String[] {
        "-scripting", "--const-as-var"
    };

    @Test
    public void parseAllTests() {
        if (TEST262) {
            parseTestSet(TEST262_SUITE_DIR, new TestFilter() {
                @Override
                public boolean exclude(final File file, final String content) {
                    return content.contains("@negative");
                }
            });
        }
        parseTestSet(TEST_BASIC_DIR, new TestFilter() {
            @Override
            public boolean exclude(final File file, final String content) {
                return file.getParentFile().getName().equals("es6");
            }
        });
        parseTestSet(TEST_MAPTESTS_DIR, null);
        parseTestSet(TEST_SANDBOX_DIR, null);
        parseTestSet(TEST_TRUSTED_DIR, null);
    }

    private void parseTestSet(final String testSet, final TestFilter filter) {
        passed  = 0;
        failed  = 0;
        skipped = 0;

        final File testSetDir = new File(testSet);
        if (! testSetDir.isDirectory()) {
            log("WARNING: " + testSetDir + " not found or not a directory");
            return;
        }
        log(testSetDir.getAbsolutePath());
        parseJSDirectory(testSetDir, filter);

        log(testSet + " parse API done!");
        log("parse API ok: " + passed);
        log("parse API failed: " + failed);
        log("parse API skipped: " + skipped);
        if (failed != 0) {
            Assert.fail(failed + " tests failed to parse in " + testSetDir.getAbsolutePath());
        }
    }

    // number of scripts that parsed fine
    private int passed;
    // number of scripts resulting in parse failure
    private int failed;
    // scripts that were skipped - all tests with @negative are
    // skipped for now.
    private int skipped;

    private void parseJSDirectory(final File dir, final TestFilter filter) {
        for (final File f : dir.listFiles()) {
            if (f.isDirectory()) {
                parseJSDirectory(f, filter);
            } else if (f.getName().endsWith(".js")) {
                parseJSFile(f, filter);
            }
        }
    }

    private void parseJSFile(final File file, final TestFilter filter) {
        if (VERBOSE) {
            log("Begin parsing " + file.getAbsolutePath());
        }

        try {
            final char[] buffer = readFully(file);
            final String content = new String(buffer);
            boolean excluded = false;
            if (filter != null) {
                excluded = filter.exclude(file, content);
            }

            if (excluded) {
                if (VERBOSE) {
                    log("Skipping " + file.getAbsolutePath());
                }
                skipped++;
                return;
            }

            final Parser parser = Parser.create(options);
            final Tree tree = parser.parse(file.getAbsolutePath(), content, null);
            tree.accept(new SimpleTreeVisitorES5_1<Void, Void>(), null);
            passed++;
        } catch (final Throwable exp) {
            log("Parse API failed: " + file.getAbsolutePath() + " : " + exp);
            //if (VERBOSE) {
                exp.printStackTrace(System.out);
            //}
            failed++;
        }

        if (VERBOSE) {
            log("Done parsing via parser API " + file.getAbsolutePath());
        }
    }

    private static char[] byteToCharArray(final byte[] bytes) {
        Charset cs = StandardCharsets.UTF_8;
        int start = 0;
        // BOM detection.
        if (bytes.length > 1 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            start = 2;
            cs = StandardCharsets.UTF_16BE;
        } else if (bytes.length > 1 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            start = 2;
            cs = StandardCharsets.UTF_16LE;
        } else if (bytes.length > 2 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            start = 3;
            cs = StandardCharsets.UTF_8;
        } else if (bytes.length > 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE && bytes[2] == 0 && bytes[3] == 0) {
            start = 4;
            cs = Charset.forName("UTF-32LE");
        } else if (bytes.length > 3 && bytes[0] == 0 && bytes[1] == 0 && bytes[2] == (byte) 0xFE && bytes[3] == (byte) 0xFF) {
            start = 4;
            cs = Charset.forName("UTF-32BE");
        }

        return new String(bytes, start, bytes.length - start, cs).toCharArray();
    }

    private static char[] readFully(final File file) throws IOException {
        final byte[] buf = Files.readAllBytes(file.toPath());
        return byteToCharArray(buf);
    }
}
