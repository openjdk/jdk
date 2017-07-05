/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package compiler.aot;

import jdk.test.lib.process.OutputAnalyzer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Utils;

/**
 * A simple class calling AOT compiler over requested items
 */
public class AotCompiler {

    private final static String METHODS_LIST_FILENAME = "methodsList.txt";

    public static void main(String args[]) {
        String className = null;
        List<String> compileList = new ArrayList<>();
        String libName = null;
        List<String> extraopts = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-class":
                    className = args[++i];
                    break;
                case "-compile":
                    compileList.add("compileOnly " + args[++i]);
                    break;
                case "-libname":
                    libName = args[++i];
                    break;
                case "-extraopt":
                    extraopts.add(args[++i]);
                    break;
                default:
                    throw new Error("Unknown option: " + args[i]);
            }
        }
        extraopts.add("-classpath");
        extraopts.add(Utils.TEST_CLASS_PATH + File.pathSeparator + Utils.TEST_SRC);
        if (className != null && libName != null) {
            OutputAnalyzer oa = launchCompiler(libName, className, extraopts, compileList);
            oa.shouldHaveExitValue(0);
        } else {
            printUsage();
            throw new Error("Mandatory arguments aren't passed");
        }
    }

    public static OutputAnalyzer launchCompilerSimple(String... args) {
        return launchJaotc(Arrays.asList(args), null);
    }

    public static OutputAnalyzer launchCompiler(String libName, String item, List<String> extraopts,
            List<String> compList) {
        Path file = null;
        if (compList != null && !compList.isEmpty()) {
            file = Paths.get(METHODS_LIST_FILENAME);
            try {
                Files.write(file, compList, StandardOpenOption.CREATE);
            } catch (IOException e) {
                throw new Error("Couldn't write " + METHODS_LIST_FILENAME + " " + e, e);
            }
        }
        List<String> args = new ArrayList<>();
        args.add("--compile-with-assertions");
        args.add("--output");
        args.add(libName);
        if (file != null) {
            args.add("--compile-commands");
            args.add(file.toString());
        }
        args.add("--class-name");
        args.add(item);
        return launchJaotc(args, extraopts);
    }

    private static OutputAnalyzer launchJaotc(List<String> args, List<String> extraVmOpts) {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jaotc");
        for (String vmOpt : Utils.getTestJavaOpts()) {
            launcher.addVMArg(vmOpt);
        }
        if (extraVmOpts != null) {
            for (String vmOpt : extraVmOpts) {
                launcher.addVMArg(vmOpt);
            }
        }
        for (String arg : args) {
            launcher.addToolArg(arg);
        }
        try {
            return new OutputAnalyzer(new ProcessBuilder(launcher.getCommand()).inheritIO().start());
        } catch (IOException e) {
            throw new Error("Can't start test process: " + e, e);
        }
    }

    public static void printUsage() {
        System.err.println("Usage: " + AotCompiler.class.getName()
                + " -class <class> -libname <.so name>"
                + " [-compile <compileItems>]* [-extraopt <java option>]*");
    }
}
