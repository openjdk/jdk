/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary The boot classpath appended by the Boot-Class-Path attribute of a -javaagent
 *          JAR in the training run must be passed to the child JVM process that
 *          assembles the AOT cache.
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.jvmti
 * @library /test/lib
 * @build AgentBootClassPath
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AgentBootClassPathApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar boot1.jar AgentBootClassPathBoot1
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar boot2.jar AgentBootClassPathBoot2
 * @run driver AgentBootClassPath AOT --one-step-training
 */

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class AgentBootClassPath {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String boot1Jar = ClassFileInstaller.getJarPath("boot1.jar");
    static final String agentJar = "agent.jar";
    static final String mainClass = "AgentBootClassPathApp";

    public static void main(String[] args) throws Exception {
        // The agent appends boot2.jar to the boot classpath in its OnLoad phase.
        // The Boot-Class-Path attribute is relative to the location of the agent JAR.
        String manifest = "Premain-Class: AgentBootClassPathAgent\n" +
                          "Boot-Class-Path: boot2.jar\n";
        ClassFileInstaller.writeJar(agentJar,
                                    ClassFileInstaller.Manifest.fromString(manifest),
                                    "AgentBootClassPathAgent");

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
            // The training JVM's boot classpath append is boot1.jar:boot2.jar.
            // The child JVM must see the same boot classpath append, in the same order.
            return new String[] {
                "-Xbootclasspath/a:" + boot1Jar,
                "-javaagent:" + agentJar,
                "-Xlog:class+load,class+path,aot",
            };
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) {
            if (runMode == RunMode.TRAINING) {
                out.shouldContain("Skipping AgentBootClassPathAgent: Unsupported location");
                out.shouldContain("Skipping AgentBootClassPathBoot2: Unsupported location");
            }
            if (runMode.isApplicationExecuted()) {
                out.shouldContain("AgentBootClassPathBoot1 loader = null");
                out.shouldContain("AgentBootClassPathBoot2 loader = null");
            }
            if (runMode.isProductionRun()) {
                out.shouldMatch("AgentBootClassPathBoot1 source: (shared objects file|.*boot1.jar)");
                out.shouldMatch("AgentBootClassPathBoot2 source: (shared objects file|.*boot2.jar)");
                out.shouldContain("AgentBootClassPathApp source: shared objects file");
            }
        }
    }
}

class AgentBootClassPathAgent {
    public static void premain(String args, java.lang.instrument.Instrumentation inst) {
        System.out.println("AgentBootClassPathAgent.premain");
    }
}

class AgentBootClassPathApp {
    public static void main(String args[]) throws Exception {
        for (String name : new String[] {"AgentBootClassPathBoot1", "AgentBootClassPathBoot2"}) {
            Class<?> c = Class.forName(name);
            System.out.println(name + " loader = " + c.getClassLoader());
            if (c.getClassLoader() != null) {
                throw new RuntimeException(name + " should be loaded by the boot loader");
            }
        }
    }
}

class AgentBootClassPathBoot1 {}

class AgentBootClassPathBoot2 {}
