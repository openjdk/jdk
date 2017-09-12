/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8164130
 * @summary test IOException handling
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester
 * @run main TestIOException
 */

import java.io.File;
import java.io.FileWriter;

public class TestIOException extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestIOException tester = new TestIOException();
        tester.runTests();
    }

    @Test
    void testReadOnlyDirectory() {
        File outDir = new File("out1");
        if (!outDir.mkdir()) {
            throw new Error("Cannot create directory");
        }
        if (!outDir.setReadOnly()) {
            throw new Error("could not set directory read-only");
        }
        if (outDir.canWrite()) {
            throw new Error("directory is writable");
        }

        try {
            javadoc("-d", outDir.toString(),
                    new File(testSrc, "TestIOException.java").getPath());
            checkExit(Exit.ERROR);
            checkOutput(Output.OUT, true,
                "Destination directory not writable: " + outDir);
        } finally {
            outDir.setWritable(true);
        }
    }

    @Test
    void testReadOnlyFile() throws Exception {
        File outDir = new File("out2");
        if (!outDir.mkdir()) {
            throw new Error("Cannot create directory");
        }
        File index = new File(outDir, "index.html");
        try (FileWriter fw = new FileWriter(index)) { }
        if (!index.setReadOnly()) {
            throw new Error("could not set index read-only");
        }
        if (index.canWrite()) {
            throw new Error("index is writable");
        }

        try {
            setOutputDirectoryCheck(DirectoryCheck.NONE);
            javadoc("-d", outDir.toString(),
                    new File(testSrc, "TestIOException.java").getPath());

            checkExit(Exit.ERROR);
            checkOutput(Output.OUT, true,
                "Error writing file: " + index);
        } finally {
            setOutputDirectoryCheck(DirectoryCheck.EMPTY);
            index.setWritable(true);
        }
    }

    @Test
    void testReadOnlySubdirectory() throws Exception {
        // init source file
        File srcDir = new File("src4");
        File src_p = new File(srcDir, "p");
        src_p.mkdirs();
        File src_p_C = new File(src_p, "C.java");
        try (FileWriter fw = new FileWriter(src_p_C)) {
            fw.write("package p; public class C { }");
        }

        // create an unwritable package output directory
        File outDir = new File("out3");
        File pkgOutDir = new File(outDir, "p");
        if (!pkgOutDir.mkdirs()) {
            throw new Error("Cannot create directory");
        }
        if (!pkgOutDir.setReadOnly()) {
            throw new Error("could not set directory read-only");
        }
        if (pkgOutDir.canWrite()) {
            throw new Error("directory is writable");
        }

        // run javadoc and check results
        try {
            setOutputDirectoryCheck(DirectoryCheck.NONE);
            javadoc("-d", outDir.toString(),
                    src_p_C.getPath());
            checkExit(Exit.ERROR);
            checkOutput(Output.OUT, true,
                "Error writing file: " + new File(pkgOutDir, "C.html"));
        } finally {
            setOutputDirectoryCheck(DirectoryCheck.EMPTY);
            pkgOutDir.setWritable(true);
        }
    }

    @Test
    void testReadOnlyDocFilesDir() throws Exception {
        // init source files
        File srcDir = new File("src4");
        File src_p = new File(srcDir, "p");
        src_p.mkdirs();
        File src_p_C = new File(src_p, "C.java");
        try (FileWriter fw = new FileWriter(src_p_C)) {
            fw.write("package p; public class C { }");
        }
        File src_p_docfiles = new File(src_p, "doc-files");
        src_p_docfiles.mkdir();
        try (FileWriter fw = new FileWriter(new File(src_p_docfiles, "info.txt"))) {
            fw.write("info");
        }

        // create an unwritable doc-files output directory
        File outDir = new File("out4");
        File pkgOutDir = new File(outDir, "p");
        File docFilesOutDir = new File(pkgOutDir, "doc-files");
        if (!docFilesOutDir.mkdirs()) {
            throw new Error("Cannot create directory");
        }
        if (!docFilesOutDir.setReadOnly()) {
            throw new Error("could not set directory read-only");
        }
        if (docFilesOutDir.canWrite()) {
            throw new Error("directory is writable");
        }

        try {
            setOutputDirectoryCheck(DirectoryCheck.NONE);
            javadoc("-d", outDir.toString(),
                    "-sourcepath", srcDir.getPath(),
                    "p");
            checkExit(Exit.ERROR);
            checkOutput(Output.OUT, true,
                "Error writing file: " + new File(docFilesOutDir, "info.txt"));
        } finally {
            setOutputDirectoryCheck(DirectoryCheck.EMPTY);
            docFilesOutDir.setWritable(true);
        }
    }
}

