/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, BELLSOFT. All rights reserved.
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
 * LoadLibraryUnloadTest ensures all objects (NativeLibrary) are deallocated
 * when loaded in concurrent mode.
 */
/*
 * @test
 * @bug 8266310 8289919 8293282
 * @summary Checks that JNI_OnLoad is invoked only once when multiple threads
 *          call System.loadLibrary concurrently, and JNI_OnUnload is invoked
 *          when the native library is loaded from a custom class loader.
 * @library /test/lib
 * @build LoadLibraryUnload p.Class1
 * @run main/othervm/native LoadLibraryUnloadTest
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;

import static jdk.test.lib.process.ProcessTools.*;

public class LoadLibraryUnloadTest {

    private static String testClassPath = System.getProperty("test.classes");
    private static String testLibraryPath = System.getProperty("test.nativepath");

    private final static long countLines(OutputAnalyzer output, String string) {
        return output.asLines()
                     .stream()
                     .filter(s -> s.contains(string))
                     .count();
    }

    private final static void dump(OutputAnalyzer output) {
        output.asLines()
              .stream()
              .forEach(s -> System.out.println(s));
    }

    public static void main(String[] args) throws Throwable {

        OutputAnalyzer outputAnalyzer = executeCommand(createTestJavaProcessBuilder(
                "-Dtest.classes=" + testClassPath,
                "-Djava.library.path=" + testLibraryPath,
                "LoadLibraryUnload"));
        dump(outputAnalyzer);

        Asserts.assertTrue(
                countLines(outputAnalyzer, "Native library loaded from Class1.") == 2,
                "Native library expected to be loaded in 2 threads.");

        long refCount = countLines(outputAnalyzer, "Native library loaded.");

        Asserts.assertTrue(refCount > 0, "Failed to load native library.");

        System.out.println("Native library loaded in " + refCount + " threads");

        Asserts.assertTrue(refCount == 1, "Native library is loaded more than once.");

        Asserts.assertTrue(
                countLines(outputAnalyzer, "Native library unloaded.") == refCount,
                "Failed to unload native library");

        Asserts.assertEquals(0, outputAnalyzer.getExitValue(),
                "LoadLibraryUnload exit value not zero");
    }
}
