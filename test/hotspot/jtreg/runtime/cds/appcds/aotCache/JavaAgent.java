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
 * @summary -javaagent should be allowed in AOT workflow. However, classes transformed/redefined by agents will not
 *          be cached.
 * @requires vm.cds.supports.aot.class.linking
 * @comment work around JDK-8345635
 * @requires !vm.jvmci.enabled
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build JavaAgent JavaAgentTransformer Util
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar JavaAgentApp JavaAgentApp$ShouldBeTransformed
 * @run driver JavaAgent AOT
 */

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class JavaAgent {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "JavaAgentApp";

    public static String agentClasses[] = {
        "JavaAgentTransformer",
        "Util",
    };
    static String agentJar;

    public static void main(String... args) throws Exception {
        agentJar = ClassFileInstaller.writeJar("agent.jar",
                                        ClassFileInstaller.Manifest.fromSourceFile("JavaAgentTransformer.mf"),
                                        agentClasses);

        new Tester().run(args);
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
            return new String[] { "-javaagent:" + agentJar, "-Xlog:aot,cds"};
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            String agentLoadedMsg = "JavaAgentTransformer.premain() is called";
            if (runMode.isApplicationExecuted()) {
                out.shouldContain(agentLoadedMsg);
                out.shouldContain("Transforming: JavaAgentApp$ShouldBeTransformed; Class<?> = null");
                out.shouldContain("Result: YYYY"); // "XXXX" has been changed to "YYYY" by the agent
            } else {
                out.shouldNotContain(agentLoadedMsg);
            }

            switch (runMode) {
            case RunMode.TRAINING:
                out.shouldContain("Skipping JavaAgentApp$ShouldBeTransformed: From ClassFileLoadHook");
                out.shouldContain("Skipping JavaAgentTransformer: Unsupported location");
                break;
            case RunMode.ASSEMBLY:
                out.shouldContain("Disabled all JVMTI agents during -XX:AOTMode=create");
                break;
            }
        }
    }
}

class JavaAgentApp {
    public static void main(String[] args) {
        System.out.println("Result: " + (new ShouldBeTransformed()));
    }

    static class ShouldBeTransformed {
        public String toString() {
            return "XXXX"; // Will be changed to YYYY by the agent
        }
    }
}
