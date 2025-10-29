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
 * @summary Try to AOT-initialize SharedSecrets accessors that are not allowed.
 * @bug 8368199
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.debug
 * @library /test/lib
 * @build SharedSecretsTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar MyTestApp
 * @run driver SharedSecretsTest AOT
 */

import java.io.ObjectInputFilter;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class SharedSecretsTest {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "MyTestApp";

    public static void main(String[] args) throws Exception {
        Tester t = new Tester(mainClass);
        t.setCheckExitValue(false);
        t.runAOTAssemblyWorkflow();
    }

    static class Tester extends CDSAppTester {
        public Tester(String name) {
            super(name);
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            return new String[] { "-XX:AOTInitTestClass=" + mainClass};
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) {
            if (runMode == RunMode.ASSEMBLY) {
                out.shouldMatch("jdk.internal.access.SharedSecrets::javaObjectInputFilterAccess .* must be stateless");
                out.shouldNotHaveExitValue(0);
            } else {
                out.shouldHaveExitValue(0);
            }
        }
    }
}

// We use -XX:AOTInitTestClass to force this class to be AOT-initialized in the assembly phase. It will
// cause the SharedSecrets::javaObjectInputFilterAccess to be initialized, which is not allowed.
class MyTestApp {
    static Object foo = ObjectInputFilter.Config.createFilter("");
    public static void main(String args[]) {
    }
}
