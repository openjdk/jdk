/*
 * Copyright (c) 2024, IBM Corporation. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @bug 8319516
 * @summary verify that System.loadLibrary on AIX is able to load libraries from ".a" (archive) file
 * @requires os.family == "aix"
 * @library /test/lib/
 * @build jdk.test.lib.process.ProcessTools
 * @run  main/othervm   LoadAIXLibraryFromArchiveObject
 */
public class LoadAIXLibraryFromArchiveObject {

    private static final String TEST_LIBRARY_NAME = "foobar";
    // creates a ".a" archive file in a test specific directory and then
    // launches a java application passing this directory through "-Djava.library.path".
    // the java application then attempts to load the library using System.loadLibrary()
    public static void main(String[] args) throws Exception {
        String javaHome = System.getProperty("java.home");
        Path libj2pcscSo = Path.of(javaHome).resolve("lib", "libj2pcsc.so");
        if (!Files.exists(libj2pcscSo)) {
            throw new AssertionError(libj2pcscSo + " is missing");
        }
        String archiveFileName = "lib" + TEST_LIBRARY_NAME + ".a";
        // copy over libj2pcsc.so as an archive file to test specific scratch dir
        Path testNativeLibDir = Path.of("native").toAbsolutePath();
        Files.createDirectories(testNativeLibDir);
        Path libFooBarArchive = testNativeLibDir.resolve(archiveFileName);
        Files.copy(libj2pcscSo, libFooBarArchive);
        // launch a java application which calls System.loadLibrary and is passed
        // the directory containing the native library archive file, through
        // -Djava.library.path
        ProcessBuilder processBuilder = ProcessTools.createTestJavaProcessBuilder(
                "-Djava.library.path=" + testNativeLibDir,
                LoadAIXLibraryFromArchiveObject.LoadLibraryApp.class.getName());
        ProcessTools.executeCommand(processBuilder).shouldHaveExitValue(0);
    }

    static class LoadLibraryApp {
        public static void main(final String[] args) throws Exception {
            System.out.println("attempting to load library " + TEST_LIBRARY_NAME);
            System.loadLibrary(TEST_LIBRARY_NAME);
            System.out.println(TEST_LIBRARY_NAME + " successfully loaded");
        }
    }
}

