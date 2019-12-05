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

import java.nio.file.Files;
import java.nio.file.Path;

public abstract class Base {
    private static final String appOutput = JPackagePath.getAppOutputFile();

    private static void validateResult(String[] result) throws Exception {
        if (result.length != 2) {
            throw new AssertionError(
                   "Unexpected number of lines: " + result.length);
        }

        if (!result[0].trim().endsWith("jpackage test application")) {
            throw new AssertionError("Unexpected result[0]: " + result[0]);
        }

        if (!result[1].trim().equals("args.length: 0")) {
            throw new AssertionError("Unexpected result[1]: " + result[1]);
        }
    }

    public static void validate(String app) throws Exception {
        Path outPath = Path.of(appOutput);
        int retVal = JPackageHelper.execute(null, app);

        if (outPath.toFile().exists()) {
             System.out.println("output contents: ");
             System.out.println(Files.readString(outPath) + "\n");
        } else {
             System.out.println("no output file: " + outPath
                   + " from command: " + app);
        }

        if (retVal != 0) {
            throw new AssertionError(
                "Test application (" + app + ") exited with error: " + retVal);
        }

        if (!outPath.toFile().exists()) {
            throw new AssertionError(appOutput + " was not created");
        }

        String output = Files.readString(outPath);
        String[] result = JPackageHelper.splitAndFilter(output);
        validateResult(result);
    }

    public static void testCreateAppImage(String [] cmd) throws Exception {
        JPackageHelper.executeCLI(true, cmd);
        validate(JPackagePath.getApp());
    }

    public static void testCreateAppImageToolProvider(String [] cmd) throws Exception {
        JPackageHelper.executeToolProvider(true, cmd);
        validate(JPackagePath.getApp());
    }
}
