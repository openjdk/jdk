/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @library classes
 * @build test.Empty
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver ClassLoadUnloadTest
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.test.lib.classloader.ClassUnloadCommon;

public class ClassLoadUnloadTest {
    private static class ClassUnloadTestMain {
        public static void main(String... args) throws Exception {
            String className = "test.Empty";
            ClassLoader cl = ClassUnloadCommon.newClassLoader();
            Class<?> c = cl.loadClass(className);
            cl = null; c = null;
            ClassUnloadCommon.triggerUnloading();
        }
    }

    static void checkFor(OutputAnalyzer output, String... outputStrings) throws Exception {
        for (String s: outputStrings) {
            output.shouldContain(s);
        }
    }

    static void checkAbsent(OutputAnalyzer output, String... outputStrings) throws Exception {
        for (String s: outputStrings) {
            output.shouldNotContain(s);
        }
    }

    // Use the same command-line heap size setting as ../ClassUnload/UnloadTest.java
    static OutputAnalyzer exec(String... args) throws Exception {
        List<String> argsList = new ArrayList<>();
        Collections.addAll(argsList, args);
        Collections.addAll(argsList, "-Xmn8m", "-Xbootclasspath/a:.", "-XX:+UnlockDiagnosticVMOptions",
                           "-XX:+WhiteBoxAPI", "-XX:+ClassUnloading", ClassUnloadTestMain.class.getName());
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(argsList);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        return output;
    }

    public static void main(String... args) throws Exception {

        OutputAnalyzer output;

        //  -Xlog:class+unload=info
        output = exec("-Xlog:class+unload=info");
        checkFor(output, "[class,unload]", "unloading class");

        //  -Xlog:class+unload=off
        output = exec("-Xlog:class+unload=off");
        checkAbsent(output,"[class,unload]");

        //  -Xlog:class+load=info
        output = exec("-Xlog:class+load=info");
        checkFor(output,"[class,load]", "java.lang.Object", "source:");

        //  -Xlog:class+load=debug
        output = exec("-Xlog:class+load=debug");
        checkFor(output,"[class,load]", "java.lang.Object", "source:", "klass:", "super:", "loader:", "bytes:");

        //  -Xlog:class+load=off
        output = exec("-Xlog:class+load=off");
        checkAbsent(output,"[class,load]");

        //  -verbose:class
        output = exec("-verbose:class");
        checkFor(output,"[class,load]", "java.lang.Object", "source:");
        checkFor(output,"[class,unload]", "unloading class");

        //  -Xlog:class+loader+data=trace
        output = exec("-Xlog:class+loader+data=trace");
        checkFor(output, "[class,loader,data]", "create loader data");

        //  -Xlog:class+load+cause
        output = exec("-Xlog:class+load+cause");
        checkAbsent(output,"[class,load,cause]");
        checkFor(output,"class load cause logging will not produce output without LogClassLoadingCauseFor");

        String x = ClassUnloadTestMain.class.getName();

        output = exec("-Xlog:class+load+cause", "-XX:LogClassLoadingCauseFor=" + x);
        checkFor(output,"[class,load,cause]", "Java stack when loading " + x + ":");

        output = exec("-Xlog:class+load+cause+native", "-XX:LogClassLoadingCauseFor=" + x);
        checkFor(output,"[class,load,cause,native]", "Native stack when loading " + x + ":");

        output = exec("-Xlog:class+load+cause*", "-XX:LogClassLoadingCauseFor=" + x);
        checkFor(output,"[class,load,cause] Java stack when loading " + x + ":");
        checkFor(output,"[class,load,cause,native] Native stack when loading " + x + ":");
    }
}
