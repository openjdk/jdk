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

package jdk.test.lib.cds;

import java.io.File;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import jdk.test.lib.cds.CDSAppTester.RunMode;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.StringArrayUtils;

/*
 * A simpler way to use CDSAppTester. Example:
 *
 * SimpleCDSAppTester.of("moduleNeedsJdkAddExport")
 *    .classpath(dummyJar)
 *    .modulepath(modulePath)
 *    .addVmArgs("--add-modules", "com.needsjdkaddexport",
 *               "--add-exports", "java.base/jdk.internal.misc=com.needsjdkaddexport", "-Xlog:cds")
 *    .appCommandLine("-m", "com.needsjdkaddexport/com.needsjdkaddexport.Main")
 *    .setAssemblyChecker((OutputAnalyzer out) -> {
 *           out.shouldContain("Full module graph = enabled");
 *        })
 *    .setProductionChecker((OutputAnalyzer out) -> {
 *            out.shouldContain("use_full_module_graph = true; java.base");
 *        })
 *    .runStaticWorkflow()
 *    .runAOTWorkflow();
 */
public class SimpleCDSAppTester {
    private String name;
    private BiConsumer<OutputAnalyzer, RunMode> trainingChecker;
    private BiConsumer<OutputAnalyzer, RunMode> assemblyChecker;
    private BiConsumer<OutputAnalyzer, RunMode> productionChecker;
    private String classpath;
    private String modulepath;
    private String[] appCommandLine;
    private String[] vmArgs = new String[] {};

    private SimpleCDSAppTester(String name) {
        this.name = name;
    }

    public static SimpleCDSAppTester of(String name) {
        return new SimpleCDSAppTester(name);
    }

    public SimpleCDSAppTester classpath(String... paths) {
        this.classpath = null;
        for (String p : paths) {
            if (this.classpath == null) {
                this.classpath = p;
            } else {
                this.classpath += File.pathSeparator + p;
            }
        }
        return this;
    }

    public SimpleCDSAppTester modulepath(String... paths) {
        this.modulepath = null;
        for (String p : paths) {
            if (this.modulepath == null) {
                this.modulepath = p;
            } else {
                this.modulepath += File.pathSeparator + p;
            }
        }
        return this;
    }

    public SimpleCDSAppTester addVmArgs(String... args) {
        vmArgs = StringArrayUtils.concat(vmArgs, args);
        return this;
    }

    public SimpleCDSAppTester appCommandLine(String... args) {
        this.appCommandLine = args;
        return this;
    }

    public SimpleCDSAppTester setTrainingChecker(BiConsumer<OutputAnalyzer, RunMode> checker) {
        this.trainingChecker = checker;
        return this;
    }

    public SimpleCDSAppTester setAssemblyChecker(BiConsumer<OutputAnalyzer, RunMode> checker) {
        this.assemblyChecker = checker;
        return this;
    }

    public SimpleCDSAppTester setProductionChecker(BiConsumer<OutputAnalyzer, RunMode> checker) {
        this.productionChecker = checker;
        return this;
    }

    public SimpleCDSAppTester setTrainingChecker(Consumer<OutputAnalyzer> checker) {
        this.trainingChecker = (OutputAnalyzer out, RunMode runMode) -> {
            checker.accept(out);
        };
        return this;
    }

    public SimpleCDSAppTester setAssemblyChecker(Consumer<OutputAnalyzer> checker) {
        this.assemblyChecker = (OutputAnalyzer out, RunMode runMode) -> {
            checker.accept(out);
        };
        return this;
    }

    public SimpleCDSAppTester setProductionChecker(Consumer<OutputAnalyzer> checker) {
        this.productionChecker = (OutputAnalyzer out, RunMode runMode) -> {
            checker.accept(out);
        };
        return this;
    }

    class Tester extends CDSAppTester {
        public Tester(String name) {
            super(name);
        }

        @Override
        public String classpath(RunMode runMode) {
            return SimpleCDSAppTester.this.classpath;
        }

        @Override
        public String modulepath(RunMode runMode) {
            return SimpleCDSAppTester.this.modulepath;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            return SimpleCDSAppTester.this.vmArgs;
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return SimpleCDSAppTester.this.appCommandLine;
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.TRAINING) {
                if (trainingChecker != null) {
                    trainingChecker.accept(out, runMode);
                }
            } else if (isDumping(runMode)) {
                if (assemblyChecker != null) {
                    assemblyChecker.accept(out, runMode);
                }
            } else if (runMode.isProductionRun()) {
                if (productionChecker != null) {
                    productionChecker.accept(out, runMode);
                }
            }
        }
    }

    public SimpleCDSAppTester runStaticWorkflow() throws Exception {
        (new Tester(name)).runStaticWorkflow();
        return this;
    }

    public SimpleCDSAppTester runAOTWorkflow() throws Exception {
        (new Tester(name)).runAOTWorkflow();
        return this;
    }

    public SimpleCDSAppTester run(String args[])  throws Exception {
        (new Tester(name)).run(args);
        return this;
    }
}
