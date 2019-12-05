/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class JavaOptionsBase {

    private static final String app = JPackagePath.getApp();
    private static final String appOutput = JPackagePath.getAppOutputFile();

    private static final String ARGUMENT1 = "-Dparam1=Some Param 1";
    private static final String ARGUMENT2 = "-Dparam2=Some \"Param\" 2";
    private static final String ARGUMENT3 =
            "-Dparam3=Some \"Param\" with \" 3";

    private static final List<String> arguments = new ArrayList<>();

    private static void initArguments(boolean toolProvider, String [] cmd) {
        if (arguments.isEmpty()) {
            arguments.add(ARGUMENT1);
            arguments.add(ARGUMENT2);
            arguments.add(ARGUMENT3);
        }

        String argumentsMap = JPackageHelper.listToArgumentsMap(arguments,
                toolProvider);
        cmd[cmd.length - 1] = argumentsMap;
    }

    private static void initArguments2(boolean toolProvider, String [] cmd) {
        int index = cmd.length - 6;

        cmd[index++] = "--java-options";
        arguments.clear();
        arguments.add(ARGUMENT1);
        cmd[index++] = JPackageHelper.listToArgumentsMap(arguments,
                toolProvider);

        cmd[index++] = "--java-options";
        arguments.clear();
        arguments.add(ARGUMENT2);
        cmd[index++] = JPackageHelper.listToArgumentsMap(arguments,
                toolProvider);

        cmd[index++] = "--java-options";
        arguments.clear();
        arguments.add(ARGUMENT3);
        cmd[index++] = JPackageHelper.listToArgumentsMap(arguments,
                toolProvider);

        arguments.clear();
        arguments.add(ARGUMENT1);
        arguments.add(ARGUMENT2);
        arguments.add(ARGUMENT3);
    }

    private static void validateResult(String[] result, List<String> args)
            throws Exception {
        if (result.length != (args.size() + 2)) {
            for (String r : result) {
                System.err.println(r.trim());
            }
            throw new AssertionError("Unexpected number of lines: "
                    + result.length);
        }

        if (!result[0].trim().equals("jpackage test application")) {
            throw new AssertionError("Unexpected result[0]: " + result[0]);
        }

        if (!result[1].trim().equals("args.length: 0")) {
            throw new AssertionError("Unexpected result[1]: " + result[1]);
        }

        int index = 2;
        for (String arg : args) {
            if (!result[index].trim().equals(arg)) {
                throw new AssertionError("Unexpected result[" + index + "]: "
                    + result[index]);
            }
            index++;
        }
    }

    private static void validate(List<String> expectedArgs) throws Exception {
        int retVal = JPackageHelper.execute(null, app);
        if (retVal != 0) {
            throw new AssertionError("Test application exited with error: "
                    + retVal);
        }

        File outfile = new File(appOutput);
        if (!outfile.exists()) {
            throw new AssertionError(appOutput + " was not created");
        }

        String output = Files.readString(outfile.toPath());
        String[] result = JPackageHelper.splitAndFilter(output);
        validateResult(result, expectedArgs);
    }

    public static void testCreateAppImageJavaOptions(String [] cmd) throws Exception {
        initArguments(false, cmd);
        JPackageHelper.executeCLI(true, cmd);
        validate(arguments);
    }

    public static void testCreateAppImageJavaOptionsToolProvider(String [] cmd) throws Exception {
        initArguments(true, cmd);
        JPackageHelper.executeToolProvider(true, cmd);
        validate(arguments);
    }

    public static void testCreateAppImageJavaOptions2(String [] cmd) throws Exception {
        initArguments2(false, cmd);
        JPackageHelper.executeCLI(true, cmd);
        validate(arguments);
    }

    public static void testCreateAppImageJavaOptions2ToolProvider(String [] cmd) throws Exception {
        initArguments2(true, cmd);
        JPackageHelper.executeToolProvider(true, cmd);
        validate(arguments);
    }
}
