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


import org.junit.Before;
import org.junit.Test;
import tools.FileChecker;
import tools.FileProcessor;
import tools.HtmlFileChecker;
import tools.checkers.BadCharacterChecker;
import tools.checkers.DocTypeChecker;
import tools.checkers.LinkChecker;
import tools.checkers.TidyChecker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


/**
 * Takes a directory path under the generated documentation directory as input
 * and runs different {@link FileChecker file checkers} on it
 */
public class DocCheck {
    private static final Path ROOT_PATH = Path.of(System.getProperty("test.jdk"));
    private List<Path> files;

    @Before
    public void setUp() {
        Path root = Path.of(ROOT_PATH.getParent() + File.separator + "docs" + File.separator + System.getProperty("doccheck.dir"));
        var fileTester = new FileProcessor();
        fileTester.processFiles(root);
        files = fileTester.getFiles();
    }

    @Test
    public void test() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        List<Throwable> exceptions = new ArrayList<>();

        try (
                TidyChecker tidy = new TidyChecker();
                BadCharacterChecker badChars = new BadCharacterChecker();
                HtmlFileChecker docChecker = new HtmlFileChecker(new DocTypeChecker());
                HtmlFileChecker htmlChecker = new HtmlFileChecker(new LinkChecker());
        ) {
            List<Future<?>> futures = new ArrayList<>();
            futures.add(executorService.submit(() -> {
                try {
                    tidy.checkFiles(files);
                } catch (RuntimeException e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            }));

            futures.add(executorService.submit(() -> {
                try {
                    docChecker.checkFiles(files);
                } catch (RuntimeException e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            }));

            futures.add(executorService.submit(() -> {
                try {
                    badChars.checkFiles(files);
                } catch (RuntimeException e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            }));

            futures.add(executorService.submit(() -> {
                try {
                    htmlChecker.checkFiles(files);
                } catch (RuntimeException e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            }));

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            }
        } catch (IOException e) {
            throw new Exception("Interrupted: " + e);
        } finally {
            executorService.shutdown();
        }

        if (!exceptions.isEmpty()) {
            throw new Exception("One or more HTML checkers failed: " + exceptions);
        }
    }
}
