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

package jdk.nashorn.internal.parser;

import static jdk.nashorn.internal.runtime.Source.readFully;
import static jdk.nashorn.internal.runtime.Source.sourceFor;

import java.io.File;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ErrorManager;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.options.Options;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Run tests to check Nashorn's parser.
 */
public class ParserTest {
    private static final boolean VERBOSE   = Boolean.valueOf(System.getProperty("parsertest.verbose"));
    private static final boolean TEST262   = Boolean.valueOf(System.getProperty("parsertest.test262"));

    private static final String TEST_BASIC_DIR  = System.getProperty("test.basic.dir");
    private static final String TEST262_SUITE_DIR = System.getProperty("test262.suite.dir");


    interface TestFilter {
        public boolean exclude(File file, String content);
    }

    private static void log(final String msg) {
        org.testng.Reporter.log(msg, true);
    }

    private Context context;

    @BeforeClass
    public void setupTest() {
        final Options options = new Options("nashorn");
        options.set("anon.functions", true);
        options.set("parse.only", true);
        options.set("scripting", true);
        options.set("const.as.var", true);

        final ErrorManager errors = new ErrorManager();
        this.context = new Context(options, errors, Thread.currentThread().getContextClassLoader());
    }

    @AfterClass
    public void tearDownTest() {
        this.context = null;
    }

    @Test
    public void parseAllTests() {
        if (TEST262) {
            parseTestSet(TEST262_SUITE_DIR, new TestFilter() {
                @Override
                public boolean exclude(final File file, final String content) {
                    return content.indexOf("@negative") != -1;
                }
            });
        }
        parseTestSet(TEST_BASIC_DIR, null);
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

        log(testSet + " parse done!");
        log("parse ok: " + passed);
        log("parse failed: " + failed);
        log("parse skipped: " + skipped);
        if (failed != 0) {
            Assert.fail(failed + " tests failed to compile in " + testSetDir.getAbsolutePath());
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
            boolean excluded = false;
            if (filter != null) {
                final String content = new String(buffer);
                excluded = filter.exclude(file, content);
            }

            if (excluded) {
                if (VERBOSE) {
                    log("Skipping " + file.getAbsolutePath());
                }
                skipped++;
                return;
            }

            final ErrorManager errors = new ErrorManager() {
                @Override
                public void error(final String msg) {
                    log(msg);
                }
            };
            errors.setLimit(0);
            final Source source = sourceFor(file.getAbsolutePath(), buffer);
            new Parser(context.getEnv(), source, errors, context.getEnv()._strict, null).parse();
            if (errors.getNumberOfErrors() > 0) {
                log("Parse failed: " + file.getAbsolutePath());
                failed++;
            } else {
                passed++;
            }
        } catch (final Throwable exp) {
            exp.printStackTrace();
            log("Parse failed: " + file.getAbsolutePath() + " : " + exp);
            if (VERBOSE) {
                exp.printStackTrace(System.out);
            }
            failed++;
        }

        if (VERBOSE) {
            log("Done parsing " + file.getAbsolutePath());
        }
    }
}
