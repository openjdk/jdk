/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import toolbox.TestRunner;

/**
 * Takes a directory path under the generated documentation directory as input
 * and runs different {@link FileChecker file checkers} on it.
 */
public class DocCheck extends TestRunner {
    private static final Path ROOT_PATH = Path.of(System.getProperty("test.jdk"));
    private static final Path DIR = Path.of(System.getProperty("doccheck.dir"));
    private List<Path> files;
    private static final boolean RUN_PARALLEL = Boolean.parseBoolean(System.getProperty("doccheck.runParallel", "true"));

    public static void main(String... args) throws Exception {
        DocCheck docCheck = new DocCheck();
        docCheck.runTests();
    }

    public DocCheck() {
        super(System.err);
        init();
    }

    public void init() {
        Path root = ROOT_PATH.getParent()
                .resolve("docs")
                .resolve(DIR);
        var fileTester = new FileProcessor();
        fileTester.processFiles(root);
        files = fileTester.getFiles();
    }

    public List<FileChecker> getCheckers() {
        List<FileChecker> checkers = new ArrayList<>();
        checkers.add(new TidyChecker());
        checkers.add(new BadCharacterChecker());
        checkers.add(new HtmlFileChecker(new DocTypeChecker()));
        checkers.add(new HtmlFileChecker(new LinkChecker()));
        return checkers;
    }

    @Test
    public void test() throws Exception {
        List<FileChecker> checkers = getCheckers();
        if (RUN_PARALLEL) {
            runCheckersInParallel(checkers);
        } else {
            runCheckersSequentially(checkers);
        }
    }

    private void runCheckersInParallel(List<FileChecker> checkers) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(checkers.size());
        List<Throwable> exceptions = new ArrayList<>();

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (FileChecker checker : checkers) {
                futures.add(executorService.submit(() -> {
                    try (checker) {  // try-with-resources ensures closing
                        checker.checkFiles(files);
                    } catch (Exception e) {
                        synchronized (exceptions) {
                            exceptions.add(e);
                        }
                    }
                }));
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            }
        } finally {
            executorService.shutdown();
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        }

        if (!exceptions.isEmpty()) {
            throw new Exception("One or more HTML checkers failed: " + exceptions);
        }
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
