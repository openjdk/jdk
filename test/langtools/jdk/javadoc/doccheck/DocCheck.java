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
import tools.checkers.LinkChecker;
import tools.checkers.TidyChecker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Takes a module name or directory name under the generated documentation directory as input
 * and runs different {@link FileChecker file checkers} on it
 */

public class DocCheck {

//    private static final String ROOT_PATH = System.getProperty("test.src") + "/docs/" ;
    private static final String ROOT_PATH = "/Nizar/jdk/docs/";
    private List<String> files;

    @Before
    public void setUp() throws IOException {
        String module = System.getProperty("doccheck.dir");
        if (module == null || module.isEmpty()) {
            throw new IllegalArgumentException("Directory not specified");
        }
        String root = ROOT_PATH + module;
        var fileTester = new FileProcessor();
        fileTester.processFiles(Path.of(root));
        files = fileTester.getFiles();
    }

    @Test
    public void testTidy() throws IOException {
        try (TidyChecker tidy = new TidyChecker()) {
            tidy.checkFiles(files);
        }
    }

    @Test
    public void testBadCharacters() throws IOException {
        try (BadCharacterChecker badChars = new BadCharacterChecker()) {
            badChars.checkFiles(files);
        }
    }

    @Test
    public void testInternalLinks() throws IOException {
        try (LinkChecker linkChecker = new LinkChecker()) {
            var htmlChecker = new HtmlFileChecker(linkChecker);
            htmlChecker.checkFiles(files);
        }
    }
}
