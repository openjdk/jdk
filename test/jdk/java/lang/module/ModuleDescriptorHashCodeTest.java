/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.util.Optional;

/**
 * @test
 * @bug 8275509
 * @library /test/lib
 * @run driver ModuleDescriptorHashCodeTest
 * @modules java.sql
 * @summary Tests that the ModuleDescriptor.hashCode() method returns the same hash code
 * across multiple JVM runs, for the same module descriptor.
 */
public class ModuleDescriptorHashCodeTest {

    /**
     * Launches multiple instances of a Java program which verifies the hashCode() of a
     * ModuleDescriptor, which is loaded from the boot layer as well as constructed using
     * the ModuleDescriptor.Builder. It is expected that every single run of this program
     * will generate the exact same hash code for the module descriptor of the same module.
     */
    public static void main(String[] args) throws Exception {
        ModuleDescriptor md = javaSQLModuleFromBootLayer().getDescriptor();
        assertRequiresHasModifier(md);
        int expectedHashCode = md.hashCode();
        int numProcesses = 50;
        for (int i = 0; i < numProcesses; i++) {
            // run some with CDS enabled and some with CDS disabled
            boolean disableCDS = (i % 2 == 0);
            String[] processArgs;
            if (disableCDS) {
                processArgs = new String[]{"-Xshare:off",
                        HashCodeChecker.class.getName(),
                        String.valueOf(expectedHashCode)};
            } else {
                processArgs = new String[]{HashCodeChecker.class.getName(),
                        String.valueOf(expectedHashCode)};
            }
            ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder(processArgs);
            long start = System.currentTimeMillis();
            OutputAnalyzer outputAnalyzer = ProcessTools.executeProcess(processBuilder);
            System.out.println("Process " + outputAnalyzer.pid() + " completed in "
                    + (System.currentTimeMillis() - start) + " milli seconds");
            outputAnalyzer.shouldHaveExitValue(0);
        }
    }

    private static class HashCodeChecker {

        /**
         * Loads the java.sql module from boot layer and compares the hashCode of that module's
         * descriptor with the expected hash code (which is passed as an argument to the program).
         * Then uses the {@link ModuleDescriptor.Builder} to construct a module descriptor for the
         * same module and verifies that it too has the same hash code.
         */
        public static void main(String[] args) throws Exception {
            int expectedHashCode = Integer.parseInt(args[0]);
            Module bootModule = javaSQLModuleFromBootLayer();
            ModuleDescriptor bootMD = bootModule.getDescriptor();
            int actualHashCode = bootMD.hashCode();
            if (actualHashCode != expectedHashCode) {
                throw new RuntimeException("Expected hashCode " + expectedHashCode + " but got " + actualHashCode
                        + " from boot module descriptor " + bootMD);
            }
            System.out.println("Got expected hashCode of " + expectedHashCode + " for boot module descriptor " + bootMD);
            ModuleDescriptor mdFromBuilder = fromModuleInfoClass(bootModule);
            // verify that this object is indeed a different object instance than the boot module descriptor
            // to prevent any artificial passing of the test
            if (bootMD == mdFromBuilder) {
                throw new RuntimeException("ModuleDescriptor loaded from boot layer and " +
                        "one created from module-info.class unexpectedly returned the same instance: " + bootMD);
            }
            int hashCode = mdFromBuilder.hashCode();
            if (expectedHashCode != hashCode) {
                throw new RuntimeException("Expected hashCode " + expectedHashCode + " but got " + hashCode
                        + " from module descriptor " + mdFromBuilder);
            }
            // invoke a few times to make sure the hashCode doesn't change within the same JVM run
            for (int i = 0; i < 5; i++) {
                int h = mdFromBuilder.hashCode();
                if (expectedHashCode != h) {
                    throw new RuntimeException("Expected hashCode " + expectedHashCode + " but got " + h
                            + " from module descriptor " + mdFromBuilder);
                }
            }
        }
    }

    private static void assertRequiresHasModifier(ModuleDescriptor moduleDescriptor) {
        // The test case needs a module whose descriptor has at least one "requires" with a "modifier"
        // set. This is to ensure that the hashCode tests that we run in this test case,
        // do indeed trigger the hashCode() calls against the "modifier" enums.
        // At the time of writing this test, the test does indeed use such a boot module which satisfies
        // that criteria. However, we want to be sure that any future
        // changes to that module definition don't create artificial success of the test case.
        for (ModuleDescriptor.Requires requires : moduleDescriptor.requires()) {
            if (!requires.modifiers().isEmpty()) {
                return;
            }
        }
        throw new RuntimeException(moduleDescriptor + " doesn't have a \"requires\" with a \"modifier\"");
    }

    // Finds and returns the java.sql module from the boot layer
    private static Module javaSQLModuleFromBootLayer() {
        // we use "java.sql" as the module of choice because its module definition has
        // at least one "requires" with a "modifier":
        //  requires transitive java.logging;
        //  requires transitive java.transaction.xa;
        //  requires transitive java.xml;
        String moduleName = "java.sql";
        Optional<Module> bootModule = ModuleLayer.boot().findModule(moduleName);
        if (bootModule.isEmpty()) {
            throw new RuntimeException(moduleName + " module is missing in boot layer");
        }
        return bootModule.get();
    }

    // Returns a ModuleDescriptor parsed out of the module-info.class of the passed Module
    private static ModuleDescriptor fromModuleInfoClass(Module module) throws IOException {
        try (InputStream moduleInfo = module.getResourceAsStream("module-info.class")) {
            if (moduleInfo == null) {
                throw new RuntimeException("Could not locate module-info.class in " + module);
            }
            return ModuleDescriptor.read(moduleInfo);
        }
    }
}
