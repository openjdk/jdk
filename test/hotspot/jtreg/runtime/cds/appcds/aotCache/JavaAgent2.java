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


/**
 * @test id=aot
 * @summary -javaagent should be allowed in AOT workflow. However,
 * classes transformed/redefined by agents will not be cached.
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build JavaAgent2 JavaAgentRetransformer Util
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar JavaAgentApp2 JavaAgentApp2$ShouldNotBeTransformed JavaAgentApp2$ShouldBeTransformed
 * @run driver JavaAgent2 AOT
 */

import java.util.random.RandomGenerator;
import java.util.LinkedList;
import java.util.Arrays;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class JavaAgent2 {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "JavaAgentApp2";

    public static String agentClasses[] = {
        "JavaAgentRetransformer",
        "Util",
    };
    static String agentJar;

    public static void main(String... args) throws Exception {
        agentJar = ClassFileInstaller.writeJar("agent.jar",
                                               ClassFileInstaller.Manifest.fromSourceFile("JavaAgentRetransformer.mf"),
                                               agentClasses);

        Tester t = new Tester();
        t.run(args);
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
            switch (runMode) {
            case RunMode.TRAINING:
                return new String[] {
                    "-javaagent:" + agentJar,
                    "-Xlog:aot,cds",
                    "-XX:+AOTClassLinking",
                    // "-XX:CompileCommand=dontinline ShouldNotBeTransformed::doWork",
                    "-XX:-TieredCompilation",
                    "-XX:+PrintCompilation",
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+PrintInlining",
                };
            case RunMode.ASSEMBLY:
                return new String[] {
                    "-javaagent:" + agentJar,
                    "-Xlog:aot,cds",
                    "-XX:+AOTClassLinking",
                    "-XX:+AOTPrintTrainingInfo",
                };
            default:
                return new String[] {
                    "-javaagent:" + agentJar,
                    "-Xlog:aot,cds",
                    "-XX:+AOTClassLinking",
                };
            }
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            checkExecutionForAOTWorkflow(out, runMode);
        }

        static String agentLoadedMsg = "JavaAgentRetransformer.premain() is called";
        static String agentPremainFinished = "JavaAgentRetransformer::premain() is finished";

        public void checkExecutionForAOTWorkflow(OutputAnalyzer out, RunMode runMode) throws Exception {

            if (runMode.isApplicationExecuted()) {
                out.shouldContain(agentLoadedMsg);
                out.shouldContain("Transforming: JavaAgentApp2$ShouldBeTransformed");
                out.shouldContain(agentPremainFinished);
            } else {
                out.shouldNotContain(agentLoadedMsg);
                out.shouldNotContain(agentPremainFinished);
           }

            switch (runMode) {
            case RunMode.TRAINING:
                out.shouldContain("Skipping JavaAgentApp2$ShouldBeTransformed: From ClassFileLoadHook");
                out.shouldContain("Skipping JavaAgentApp2$ShouldBeTransformed: Has been redefined");
                out.shouldContain("Skipping JavaAgentRetransformer: Unsupported location");
                // should see compilation of $ShouldNotBeTransformed::doWork
                out.shouldMatch("^[0-9]* *[0-9]* *[0-9] *JavaAgentApp2\\$ShouldNotBeTransformed::doWork");
                // should see inlining of $ShouldBeTransformed::doWork
                out.shouldMatch("JavaAgentApp2\\$ShouldBeTransformed::doWork \\([0-9]* bytes\\) *inline");
                break;
            case RunMode.ASSEMBLY:
                out.shouldContain("Disabled all JVMTI agents during -XX:AOTMode=create");
                // we should not be saving compiled training data for
                // $ShouldBeTransformed.doWork as it depends on a
                // method that was retransfromed
                out.shouldNotMatch("^  C JavaAgentApp2\\$ShouldNotBeTransformed\\[.\\].doWork\\(\\)");
                break;
            }

        }
    }
}

class JavaAgentApp2 {
    public static void main(String[] args) {
        ShouldNotBeTransformed s = new ShouldNotBeTransformed();
        // Force profile/compile of ShouldNotBeTransformed::dowork.
        // The compiler should inline ShouldBeTransformed::doWork
        for (int i = 0; i < 10_000; i++) {
            s.doWork();
        }
        // transform ShouldBeTransformed::doWork to a simple return
        JavaAgentRetransformer.doRetransform(ShouldBeTransformed.class);
        // Profile and compile again
        for (int i = 0; i < 10_000; i++) {
            s.doWork();
        }
    }

    static class ShouldNotBeTransformed {
        private ShouldBeTransformed r = new ShouldBeTransformed();
        private int value = 0;
        public void doWork() {
            value = r.doWork(value);
        }
        public int getValue() { return value; }
    }

    static class ShouldBeTransformed {
        private RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        public int doWork(int value) {
            return (value * (random.nextInt(5) + 1) + random.nextInt(7));
        }
    }
}
