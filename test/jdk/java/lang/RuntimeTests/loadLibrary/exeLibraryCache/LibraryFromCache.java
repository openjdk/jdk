/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/**
 * @test
 * @bug 8275703
 * @library /test/lib
 * @requires os.family == "mac"
 * @run main/native/othervm -Djava.library.path=/usr/lib LibraryFromCache blas
 * @run main/native/othervm -Djava.library.path=/usr/lib LibraryFromCache BLAS
 * @summary Test System::loadLibrary to be able to load a library even
 *          if it's not present on the filesystem on macOS which supports
 *          dynamic library cache
 */

import jdk.test.lib.process.OutputAnalyzer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LibraryFromCache {
    public static void main(String[] args) throws IOException {
        String libname = args[0];
        if (!systemHasLibrary(libname)) {
            System.out.println("Test skipped. Library " + libname + " not found");
            return;
        }

        System.loadLibrary(libname);
    }

    /*
     * Returns true if dlopen successfully loads the specified library
     */
    private static boolean systemHasLibrary(String libname) throws IOException {
        Path launcher = Paths.get(System.getProperty("test.nativepath"), "LibraryCache");
        ProcessBuilder pb = new ProcessBuilder(launcher.toString(), "lib" + libname + ".dylib");
        OutputAnalyzer outputAnalyzer = new OutputAnalyzer(pb.start());
        System.out.println(outputAnalyzer.getOutput());
        return outputAnalyzer.getExitValue() == 0;
    }
}
