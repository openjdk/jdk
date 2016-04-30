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
 * @test LoaderConstraintsTest
 * @bug 8149996
 * @library /testlibrary /runtime/testlibrary
 * @library classes
 * @build ClassUnloadCommon test.Empty jdk.test.lib.* jdk.test.lib.OutputAnalyzer jdk.test.lib.ProcessTools
 * @run driver LoaderConstraintsTest
 */

import jdk.test.lib.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LoaderConstraintsTest {
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

        // -XX:+TraceLoaderConstraints
        pb = exec("-XX:+TraceLoaderConstraints");
        out = new OutputAnalyzer(pb.start());
        out.getOutput();
        out.shouldContain("[class,loader,constraints] adding new constraint for name: java/lang/Class, loader[0]: jdk/internal/loader/ClassLoaders$AppClassLoader, loader[1]: <bootloader>");

        // -Xlog:class+loader+constraints=info
        pb = exec("-Xlog:class+loader+constraints=info");
        out = new OutputAnalyzer(pb.start());
        out.shouldContain("[class,loader,constraints] adding new constraint for name: java/lang/Class, loader[0]: jdk/internal/loader/ClassLoaders$AppClassLoader, loader[1]: <bootloader>");

        // -XX:-TraceLoaderConstraints
        pb = exec("-XX:-TraceLoaderConstraints");
        out = new OutputAnalyzer(pb.start());
        out.shouldNotContain("[class,loaderconstraints]");

        // -Xlog:class+loader+constraints=off
        pb = exec("-Xlog:class+loader+constraints=off");
        out = new OutputAnalyzer(pb.start());
        out.shouldNotContain("[class,loader,constraints]");

    }
}
