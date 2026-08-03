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
 *
 */

/*
 * @test
 * @summary Use special characters in the name of the cache file specified by -XX:AOTCacheOutput
 *          Make sure these characters are passed to the child JVM process that assembles the cache.
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @build SpecialCacheNames
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar MyTestApp
 * @run driver SpecialCacheNames AOT --one-step-training
 */

import java.io.File;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;

public class SpecialCacheNames {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "MyTestApp";

    public static void main(String[] args) throws Exception {
        test("with spaces", args);
        test("single'quote", args);
        if (!Platform.isWindows()) {
            // This seems to be a limitation of ProcessBuilder on Windows that has problem passing
            // double quote or unicode characters to a child process. As a result, we can't
            // even pass these parameters to the training run JVM.
            test("double\"quote", args);
            test("unicode\u202fspace", args); // Narrow No-Break Space
            test("unicode\u6587", args); // CJK unifed ideographs "wen" = "script"
        }
    }

    static void test(String name, String[] args) throws Exception {
        String archiveName = name + (args[0].equals("LEYDEN") ? ".cds" : ".aot");

        System.out.println("============================= Testing with AOT cache name: {{" + archiveName + "}}");
        new Tester(name, archiveName).run(args);
    }

    static class Tester extends CDSAppTester {
        String archiveName;
        public Tester(String name, String archiveName) {
            super(name);
            this.archiveName = archiveName;
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            // A space character in a training run vmarg should not break this vmarg into two.
            return new String[] { "-Dmy.test.prop=space -XX:FooBar" };
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) {
            if (runMode.isProductionRun()) {
                File f = new File(archiveName);
                if (f.exists()) {
                    System.out.println("Found Archive {{" + archiveName + "}}");
                } else {
                    throw new RuntimeException("Archive {{" + archiveName + "}} does not exist");
                }
            }

            if (runMode.isApplicationExecuted()) {
                out.shouldContain("Hello World");
            }
        }
    }
}

class MyTestApp {
    public static void main(String args[]) {
        String s = System.getProperty("my.test.prop");
        if (!"space -XX:FooBar".equals(s)) {
            throw new RuntimeException("Expected \"space -XX:FooBar\" but got \"" + s + "\"");
        }

        System.out.println("Hello World");
    }
}
