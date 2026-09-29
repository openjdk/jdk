/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary classes defined with JNI DefineClass should be excluded from the AOT config file and AOT cache.
 * @bug 8368182
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @build JNIDefineClass
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JNIDefineClassApp ExcludedDummy ExcludedDummy2
 * @run main/native JNIDefineClass
 */

import java.io.InputStream;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class JNIDefineClass {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "JNIDefineClassApp";

    public static void main(String[] args) throws Exception {
        Tester tester = new Tester();
        tester.run(new String[] {"AOT", "--two-step-training"} );
    }

    static class Tester extends CDSAppTester {
        public Tester() {
            super(mainClass);
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            return new String[] {
                "--enable-native-access=ALL-UNNAMED",
                "-Xlog:aot,aot+class=debug",
                "-Djava.library.path=" + System.getProperty("java.library.path"),
            };
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {mainClass};
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) {
            if (runMode.isApplicationExecuted()) {
                out.shouldContain("@@loader = null");
                out.shouldContain("@@name = ExcludedDummy");

                out.shouldMatch("@@loader2 = .*AppClassLoader");
                out.shouldContain("@@name2 = ExcludedDummy2");
            }
            if (runMode == RunMode.TRAINING) {
                out.shouldContain("Skipping ExcludedDummy: Unsupported location");
            }

            // Must not have a log like this
            /// [0.378s][debug  ][aot,class] klasses[   65] = 0x0000000800160490 boot  ExcludedDummy
            /// [0.378s][debug  ][aot,class] klasses[   66] = 0x0000000800160490 app   ExcludedDummy2
            out.shouldNotContain("aot,class.* klasses.*ExcludedDummy");
            out.shouldNotContain("aot,class.* klasses.*ExcludedDummy2");
        }
    }
}

class JNIDefineClassApp {

    static native Class<?> nativeDefineClass(String name, ClassLoader ldr, byte[] class_bytes);

    static {
        System.loadLibrary("JNIDefineClassApp");
    }

    public static void main(java.lang.String[] unused) throws Exception {
        ClassLoader appLoader = JNIDefineClassApp.class.getClassLoader();

        try (InputStream in = appLoader.getResourceAsStream("ExcludedDummy.class")) {
            byte[] b = in.readAllBytes();
            System.out.println(b.length);
            Class<?> c = nativeDefineClass("ExcludedDummy", null, b);
            System.out.println("@@loader = " + c.getClassLoader());
            System.out.println("@@name = " + c.getName());
        }

        try (InputStream in = appLoader.getResourceAsStream("ExcludedDummy2.class")) {
            byte[] b = in.readAllBytes();
            System.out.println(b.length);
            Class<?> c = nativeDefineClass("ExcludedDummy2", appLoader, b);
            System.out.println("@@loader2 = " + c.getClassLoader());
            System.out.println("@@name2 = " + c.getName());
        }

        System.out.println("TEST PASSED");
    }
}

// This class is loaded into the bootstrap loader using JNI DefineClass() with a null code source,
// so it should be excluded from the AOT configuration (and hence excluded from AOT cache)
class ExcludedDummy {

}

// This class is loaded into the app loader using JNI DefineClass() with a null code source,
// so it should be excluded from the AOT configuration (and hence excluded from AOT cache)
class ExcludedDummy2 {

}
