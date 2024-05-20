/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=static
 * @summary Run JavacBenchApp with the classic static archive workflow
 * @requires vm.cds
 * @library /test/lib
 * @run driver JavacBench STATIC
 */

/*
 * @test id=dynamic
 * @summary Run JavacBenchApp with the classic dynamic archive workflow
 * @requires vm.cds
 * @library /test/lib
 * @run driver JavacBench DYNAMIC
 */

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;

public class JavacBench {
    static String mainClass = JavacBenchApp.class.getName();
    static String appJar;

    public static void main(String args[]) throws Exception {
        appJar = ClassFileInstaller.writeJar("JavacBenchApp.jar",
                                             "JavacBenchApp",
                                             "JavacBenchApp$ClassFile",
                                             "JavacBenchApp$FileManager",
                                             "JavacBenchApp$SourceFile");
        JavacBenchTester tester = new JavacBenchTester();
        tester.run(args);
    }

    static class JavacBenchTester extends CDSAppTester {
        public JavacBenchTester() {
            super("JavacBench");
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
                "90",
            };
        }
    }
}
