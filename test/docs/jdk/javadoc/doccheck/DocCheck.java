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

import doccheckutils.FileChecker;
import doccheckutils.FileProcessor;
import doccheckutils.HtmlFileChecker;
import doccheckutils.checkers.BadCharacterChecker;
import doccheckutils.checkers.DocTypeChecker;
import doccheckutils.checkers.LinkChecker;
import doccheckutils.checkers.TidyChecker;
import doccheckutils.checkers.ExtLinkChecker;
import toolbox.TestRunner;

import java.nio.file.Path;
import java.util.*;

/**
 * DocCheck
 * <p>
 * For the sake of brevity, to run all of these checkers use
 * <p>
 * `make test-docs_all TEST_DEPS=docs-jdk`
 * <p>
 * This collection of tests provide a variety of checks for JDK documentation bundle.
 * <p>
 * It is meant to provide a convenient way to alert users of any errors in their documentation
 * before a push and verify the quality of the documentation.
 * It is not meant to replace more authoritative checkers; instead,
 * it is more focused on providing a convenient, easy overview of any possible issues.
 * <p>
 * It supports the following checks:
 * <p>
 * *HTML* -- We use the standard `tidy` utility to check for HTML compliance,
 * according to the declared version of HTML.
 * The output from `tidy` is analysed to generate a report summarizing any issues that were found.
 * <p>
 * Version `5.9.20` of `tidy` is expected, or the output from the `--version` option should contain the string `version 5`.
 * The test warns the user if he is using an earlier version.
 * <p>
 * *Bad Characters* -- We assumee that HTML files are encoded in UTF-8,
 * and reports any character encoding issues that it finds.
 * <p>
 * *DocType* --  We assume that HTML files should use HTML5, and reports
 * any files for which that is not the case.
 * <p>
 * *Links* -- We check links within a set of files, and reports on links
 * to external resources, without otherwise checking them.
 * <p>
 *  *External Links* -- We scan the files for URLs that refer to
 *     external resources, and validates those references using a "golden file" that includes a list of vetted links.
 * <p>
 * Each external reference is only checked once; but if an issue is found, all the files containing the
 * reference will be reported.
 */
public class DocCheck extends TestRunner {

    private static final String DOCCHECK_DIR = System.getProperty("doccheck.dir");
    private static final Path DIR = Path.of(DOCCHECK_DIR != null ? DOCCHECK_DIR : "");
    private static final Set<String> CHECKS_LIST = new HashSet<>();
    private static Path DOCS_DIR;

    private static boolean html;
    private static boolean links;
    private static boolean badchars;
    private static boolean doctype;
    private static boolean extlinks;

    private List<Path> files;

    public DocCheck() {
        super(System.err);
        init();
    }

    public static void main(String... args) throws Exception {
        chooseCheckers();
        DocCheck docCheck = new DocCheck();
        docCheck.runTests();
    }

    private static void chooseCheckers() {
        final String checks = System.getProperty("doccheck.checks");

        if (!checks.isEmpty()) {
            if (checks.contains(",")) {
                CHECKS_LIST.addAll(Arrays.asList(checks.split(",")));
            } else {
                CHECKS_LIST.add(checks);
            }
        }

        if (CHECKS_LIST.contains("all")) {
            html = true;
            links = true;
            badchars = true;
            doctype = true;
            extlinks = true;
        } else {
            if (CHECKS_LIST.contains("html")) {
                html = true;
            }
            if (CHECKS_LIST.contains("links")) {
                links = true;
            }
            if (CHECKS_LIST.contains("badchars")) {
                badchars = true;
            }
            if (CHECKS_LIST.contains("doctype")) {
                doctype = true;
            }
            if (CHECKS_LIST.contains("extlinks")) {
                extlinks = true;
            }
        }
    }

    public void init() {
        var fileTester = new FileProcessor();
        DOCS_DIR = DocTester.resolveDocs();
        var baseDir = DOCS_DIR.resolve(DIR);
        fileTester.processFiles(baseDir);
        files = fileTester.getFiles();
        if (html) {
            new TidyChecker();
        }
    }

    public List<FileChecker> getCheckers() {

        List<FileChecker> checkers = new ArrayList<>();
        if (html) {
            checkers.add(new TidyChecker());
        }
        if (links) {
            var linkChecker = new LinkChecker();
            linkChecker.setBaseDir(DOCS_DIR);
            checkers.add(new HtmlFileChecker(linkChecker, DOCS_DIR));
        }

        if (extlinks) {
            checkers.add(new HtmlFileChecker(new ExtLinkChecker(), DOCS_DIR));
        }

        // there should be almost nothing reported from these two checkers
        // most reports should be broken anchors/links, missing files and errors in html
        if (badchars) {
            checkers.add(new BadCharacterChecker());
        }
        if (doctype) {
            checkers.add(new HtmlFileChecker(new DocTypeChecker(), DOCS_DIR));
        }

        return checkers;
    }

    @Test
    public void test() throws Exception {
        List<FileChecker> checkers = getCheckers();
        runCheckersSequentially(checkers);
    }

    private void runCheckersSequentially(List<FileChecker> checkers) throws Exception {
        List<Throwable> exceptions = new ArrayList<>();

        for (FileChecker checker : checkers) {
            try (checker) {
                checker.checkFiles(files);
            } catch (Exception e) {
                exceptions.add(e);
            }
        }

        if (!exceptions.isEmpty()) {
            throw new Exception("One or more HTML checkers failed: " + exceptions);
        }
    }
}
