/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8240975 8281335
 * @library /test/lib
 * @modules java.base/jdk.internal.loader
 * @build java.base/* p.Test Main
 * @run main/othervm/native -Xcheck:jni Main
 * @summary Test loading and unloading of native libraries
 */

import jdk.internal.loader.NativeLibrariesTest;
import jdk.test.lib.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String... args) throws Exception {
        setup();

        // Verify a native library from test.nativepath
        NativeLibrariesTest test = new NativeLibrariesTest();
        test.runTest();

        // System::loadLibrary succeeds even the library is loaded as raw library
        System.loadLibrary(NativeLibrariesTest.LIB_NAME);

        // expect NativeLibraries to succeed even the library has been loaded by System::loadLibrary
        test.loadTestLibrary();

        // unload all NativeLibrary instances
        test.unload();

        // load zip library from JDK
        if (!Platform.isStatic()) {
            test.load(System.mapLibraryName("zip"), true /* succeed */);
        }

        // load non-existent library
        test.load(System.mapLibraryName("NotExist"), false /* fail to load */);
    }
    /*
     * move p/Test.class out from classpath to the scratch directory
     */
    static void setup() throws IOException {
        String dir = System.getProperty("test.classes", ".");
        Path p = Files.createDirectories(Paths.get("classes").resolve("p"));
        Files.move(Paths.get(dir, "p", "Test.class"), p.resolve("Test.class"));
    }

}
