/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

package compiler.aot.cli.jaotc;

import compiler.aot.AotCompiler;

import java.io.File;
import java.io.IOException;

import jdk.test.lib.process.ExitCode;
import jdk.test.lib.Platform;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Utils;
import jdk.test.lib.cli.CommandLineOptionTest;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class JaotcTestHelper {
    public static final String DEFAULT_LIB_PATH = "./unnamed." + Platform.sharedLibraryExt();
    public static final String DEFAULT_LIBRARY_LOAD_MESSAGE = "loaded    " + DEFAULT_LIB_PATH
            + "  aot library";
    private static final String UNLOCK_EXPERIMENTAL_VM_OPTIONS = "-XX:+UnlockExperimentalVMOptions";
    private static final String ENABLE_AOT = "-XX:+UseAOT";
    private static final String AOT_LIBRARY = "-XX:AOTLibrary=" + DEFAULT_LIB_PATH;
    private static final String PRINT_AOT = "-XX:+PrintAOT";

    public static OutputAnalyzer compileLibrary(String... args) {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jaotc");
        for (String vmOpt : Utils.getTestJavaOpts()) {
            launcher.addVMArg(vmOpt);
        }
        launcher.addToolArg("--compile-with-assertions");
        for (String arg : args) {
            launcher.addToolArg(arg);
        }
        String linker = AotCompiler.resolveLinker();
        if (linker != null) {
            launcher.addToolArg("--linker-path");
            launcher.addToolArg(linker);
        }
        String[] cmd = launcher.getCommand();
        try {
            return ProcessTools.executeCommand(cmd);
        } catch (Throwable e) {
            throw new Error("Can't start test process: " + e, e);
        }
    }

    public static void checkLibraryUsage(String classToRun) {
        checkLibraryUsage(classToRun, new String[]{DEFAULT_LIBRARY_LOAD_MESSAGE}, null);
    }

    public static void checkLibraryUsage(String classToRun, String[] expectedOutput,
            String[] unexpectedOutput) {
        try {
            CommandLineOptionTest.verifyJVMStartup(expectedOutput, unexpectedOutput,
                    "Unexpected exit code", "Unexpected output", ExitCode.OK,
                    /* addTestVMOpts */ true, UNLOCK_EXPERIMENTAL_VM_OPTIONS,
                    ENABLE_AOT, AOT_LIBRARY, PRINT_AOT, classToRun);
        } catch (Throwable t) {
            throw new Error("Library usage verification failed: " + t, t);
        }
    }

    public static String getClassAotCompilationFilename(Class<?> classToCompile) {
        return classToCompile.getName().replaceAll("\\.","/") + ".class";
    }

    public static String getClassAotCompilationName(Class<?> classToCompile) {
        return classToCompile.getName();
    }
}
