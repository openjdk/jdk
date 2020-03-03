/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8187305
 * @summary Tests logging of shared library loads and unloads.
 * @library /runtime/testlibrary /test/lib
 * @build sun.hotspot.WhiteBox
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main LoadLibraryTest
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jtreg.SkippedException;

import sun.hotspot.WhiteBox;

public class LoadLibraryTest {

    public static final String CLASS_NAME = "LoadLibraryTest$LoadLibraryClass";

    private static class LoadLibrary {

        static String testClasses;

        public static void runTest() throws Exception {
            // create a classloader and load a class that loads a library.
            MyClassLoader myLoader = new MyClassLoader();
            Class<?> c = Class.forName(CLASS_NAME, true, myLoader);
        }

        public static void main(String[] args) throws Exception {
            testClasses = args[0];
            runTest();
            ClassUnloadCommon.triggerUnloading();
            WhiteBox wb = WhiteBox.getWhiteBox();
            if (!wb.isClassAlive(CLASS_NAME)) {
                System.out.println("Class LoadLibraryClass was unloaded");
            }
        }


        public static class MyClassLoader extends ClassLoader {

            static ByteBuffer readClassFile(String name) {
                File f = new File(testClasses, name);
                try (FileInputStream fin = new FileInputStream(f);
                     FileChannel fc = fin.getChannel())
                {
                    return fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
                } catch (IOException e) {
                    throw new RuntimeException("Can't open file: " + name +
                                               ", exception: " + e.toString());
                }
            }

            protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
                Class<?> c;
                if (!CLASS_NAME.equals(name)) {
                    c = super.loadClass(name, resolve);
                } else {
                    // should not delegate to the system class loader
                    c = findClass(name);
                    if (resolve) {
                        resolveClass(c);
                    }
                }
                return c;
            }

            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (!CLASS_NAME.equals(name)) {
                    throw new ClassNotFoundException("Unexpected class: " + name);
                }
                return defineClass(name, readClassFile(name + ".class"), null);
            }
        } // MyClassLoader
    }


    static class LoadLibraryClass {
        static {
            System.loadLibrary("LoadLibraryClass");
            nTest();
        }
        native static void nTest();
    }


    public static void main(String... args) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xbootclasspath/a:.", "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+WhiteBoxAPI", "-Xmn8m", "-Xlog:library=info",
            "-Djava.library.path=" + System.getProperty("java.library.path"),
            "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI",
            "LoadLibraryTest$LoadLibrary", System.getProperty("test.classes"));

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        output.shouldContain("Loaded library");
        output.shouldContain("Found Java_LoadLibraryTest_00024LoadLibraryClass_nTest__ in library");
        if (output.getOutput().contains("Class LoadLibraryClass was unloaded")) {
            output.shouldContain("Unloaded library with handle");
        } else {
            throw new SkippedException(
                "Skipping check for library unloading logging because no unloading occurred");
        }
    }
}
