/*
 * Copyright 2014 Google Inc.  All Rights Reserved.
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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;

import org.testng.annotations.Test;

/**
 * @test
 * @bug 8058520
 * @summary jar tf and jar xf should work on zip files with leading garbage
 * @library /lib/testlibrary
 * @run testng LeadingGarbage
 */
@Test
public class LeadingGarbage {
    final String jar =
        Paths.get(System.getProperty("java.home"), "bin", "jar").toString();
    final File[] files = { new File("a"), new File("b") };
    final File normalZip = new File("normal.zip");
    final File leadingGarbageZip = new File("leadingGarbage.zip");

    void createFile(File f) throws IOException {
        try (OutputStream fos = new FileOutputStream(f)) {
            fos.write(f.getName().getBytes("UTF-8"));
        }
    }

    void createFiles() throws IOException {
        for (File file : files)
            createFile(file);
    }

    void deleteFiles() throws IOException {
        for (File file : files)
            assertTrue(file.delete());
    }

    void assertFilesExist() throws IOException {
        for (File file : files)
            assertTrue(file.exists());
    }

    void createNormalZip() throws Throwable {
        createFiles();
        String[] cmd = { jar, "c0Mf", "normal.zip", "a", "b" };
        ProcessBuilder pb = new ProcessBuilder(cmd);
        OutputAnalyzer a = ProcessTools.executeProcess(pb);
        a.shouldHaveExitValue(0);
        a.stdoutShouldMatch("\\A\\Z");
        a.stderrShouldMatch("\\A\\Z");
        deleteFiles();
    }

    void createZipWithLeadingGarbage() throws Throwable {
        createNormalZip();
        createFile(leadingGarbageZip);
        try (OutputStream fos = new FileOutputStream(leadingGarbageZip, true)) {
            Files.copy(normalZip.toPath(), fos);
        }
        assertTrue(normalZip.length() < leadingGarbageZip.length());
        assertTrue(normalZip.delete());
    }

    public void test_canList() throws Throwable {
        createNormalZip();
        assertCanList("normal.zip");
    }

    public void test_canListWithLeadingGarbage() throws Throwable {
        createZipWithLeadingGarbage();
        assertCanList("leadingGarbage.zip");
    }

    void assertCanList(String zipFileName) throws Throwable {
        String[] cmd = { jar, "tf", zipFileName };
        ProcessBuilder pb = new ProcessBuilder(cmd);
        OutputAnalyzer a = ProcessTools.executeProcess(pb);
        a.shouldHaveExitValue(0);
        StringBuilder expected = new StringBuilder();
        for (File file : files)
            expected.append(file.getName()).append(System.lineSeparator());
        a.stdoutShouldMatch(expected.toString());
        a.stderrShouldMatch("\\A\\Z");
    }

    public void test_canExtract() throws Throwable {
        createNormalZip();
        assertCanExtract("normal.zip");
    }

    public void test_canExtractWithLeadingGarbage() throws Throwable {
        createZipWithLeadingGarbage();
        assertCanExtract("leadingGarbage.zip");
    }

    void assertCanExtract(String zipFileName) throws Throwable {
        String[] cmd = { jar, "xf", zipFileName };
        ProcessBuilder pb = new ProcessBuilder(cmd);
        OutputAnalyzer a = ProcessTools.executeProcess(pb);
        a.shouldHaveExitValue(0);
        a.stdoutShouldMatch("\\A\\Z");
        a.stderrShouldMatch("\\A\\Z");
        assertFilesExist();
    }

}
