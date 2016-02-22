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
 * @test ClassLoadUnloadTest
 * @bug 8142506
 * @library /testlibrary /runtime/testlibrary
 * @library classes
 * @build ClassUnloadCommon test.Empty jdk.test.lib.* jdk.test.lib.OutputAnalyzer jdk.test.lib.ProcessTools
 * @run driver ClassLoadUnloadTest
 */

import jdk.test.lib.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClassLoadUnloadTest {
    private static OutputAnalyzer out;
    private static ProcessBuilder pb;
    private static class ClassUnloadTestMain {
        public static void main(String... args) throws Exception {
            String className = "test.Empty";
            ClassLoader cl = ClassUnloadCommon.newClassLoader();
            Class<?> c = cl.loadClass(className);
            cl = null; c = null;
            ClassUnloadCommon.triggerUnloading();
        }
    }

    static void checkFor(String... outputStrings) throws Exception {
        out = new OutputAnalyzer(pb.start());
        for (String s: outputStrings) {
            out.shouldContain(s);
        }
        out.shouldHaveExitValue(0);
    }

    static void checkAbsent(String... outputStrings) throws Exception {
        out = new OutputAnalyzer(pb.start());
        for (String s: outputStrings) {
            out.shouldNotContain(s);
        }
        out.shouldHaveExitValue(0);
    }

    // Use the same command-line heap size setting as ../ClassUnload/UnloadTest.java
    static ProcessBuilder exec(String... args) throws Exception {
        List<String> argsList = new ArrayList<>();
        Collections.addAll(argsList, args);
        Collections.addAll(argsList, "-Xmn8m");
        Collections.addAll(argsList, "-Dtest.classes=" + System.getProperty("test.classes","."));
        Collections.addAll(argsList, ClassUnloadTestMain.class.getName());
        return ProcessTools.createJavaProcessBuilder(argsList.toArray(new String[argsList.size()]));
    }

    public static void main(String... args) throws Exception {

        //  -Xlog:classunload=info
        pb = exec("-Xlog:classunload=info");
        checkFor("[classunload]", "unloading class");

        //  -Xlog:classunload=off
        pb = exec("-Xlog:classunload=off");
        checkAbsent("[classunload]");

        //  -XX:+TraceClassUnloading
        pb = exec("-XX:+TraceClassUnloading");
        checkFor("[classunload]", "unloading class");

        //  -XX:-TraceClassUnloading
        pb = exec("-XX:-TraceClassUnloading");
        checkAbsent("[classunload]");

        //  -Xlog:classload=info
        pb = exec("-Xlog:classload=info");
        checkFor("[classload]", "java.lang.Object", "source:");

        //  -Xlog:classload=debug
        pb = exec("-Xlog:classload=debug");
        checkFor("[classload]", "java.lang.Object", "source:", "klass:", "super:", "loader:", "bytes:");

        //  -Xlog:classload=off
        pb = exec("-Xlog:classload=off");
        checkAbsent("[classload]");

        //  -XX:+TraceClassLoading
        pb = exec("-XX:+TraceClassLoading");
        checkFor("[classload]", "java.lang.Object", "source:");

        //  -XX:-TraceClassLoading
        pb = exec("-XX:-TraceClassLoading");
        checkAbsent("[classload]");

        //  -verbose:class
        pb = exec("-verbose:class");
        checkFor("[classload]", "java.lang.Object", "source:");
        checkFor("[classunload]", "unloading class");

        //  -Xlog:classloaderdata=trace
        pb = exec("-Xlog:classloaderdata=trace");
        checkFor("[classloaderdata]", "create class loader data");

    }
}
