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


import jtreg.SkippedException;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test framework for performing tests on the generated documentation.
 */
public class DocTester {
    private final static String DIR = System.getenv("DOCS_JDK_IMAGE_DIR");
    private static final Path firstCandidate = Path.of(System.getProperty("test.jdk"))
            .getParent().resolve("docs");

    public static Path resolveDocs() {
        if (DIR != null && !DIR.isBlank() && Files.exists(Path.of(DIR))) {
            return Path.of(DIR);
        } else if (Files.exists(firstCandidate)) {
            return firstCandidate;
        }else {
            throw new SkippedException("docs folder not found in either location");
        }
    }
}
