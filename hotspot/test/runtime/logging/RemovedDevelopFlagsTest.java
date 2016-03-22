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
 * @test RemovedDevelopFlagsTest
 * @bug 8146632
 * @library /testlibrary
 * @build jdk.test.lib.OutputAnalyzer jdk.test.lib.ProcessTools
 * @run driver RemovedDevelopFlagsTest
 */
import jdk.test.lib.*;

public class RemovedDevelopFlagsTest {
    public static ProcessBuilder pb;

    public static class RemovedDevelopFlagsTestMain {
        public static void main(String... args) {
            System.out.print("Hello!");
        }
    }

    public static void exec(String flag, String value) throws Exception {
        pb = ProcessTools.createJavaProcessBuilder("-XX:+"+flag, RemovedDevelopFlagsTestMain.class.getName());
        OutputAnalyzer o = new OutputAnalyzer(pb.start());
        o.shouldContain(flag+" has been removed. Please use "+value+" instead.");
        o.shouldHaveExitValue(1);
    }

    public static void main(String... args) throws Exception {
        if (Platform.isDebugBuild()){
            exec("TraceClassInitialization", "-Xlog:classinit");
            exec("TraceClassLoaderData", "-Xlog:classloaderdata");
            exec("TraceDefaultMethods", "-Xlog:defaultmethods=debug");
            exec("TraceItables", "-Xlog:itables=debug");
            exec("TraceSafepoint", "-Xlog:safepoint=debug");
            exec("TraceStartupTime", "-Xlog:startuptime");
            exec("TraceVMOperation", "-Xlog:vmoperation=debug");
            exec("PrintVtables", "-Xlog:vtables=debug");
            exec("VerboseVerification", "-Xlog:verboseverification");
        }
    };
}
