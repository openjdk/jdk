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
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class JPackageInstallerHelper {
    private static final String JPACKAGE_TEST_OUTPUT = "jpackage.test.output";
    private static final String JPACKAGE_VERIFY_INSTALL = "jpackage.verify.install";
    private static final String JPACKAGE_VERIFY_UNINSTALL = "jpackage.verify.uninstall";
    private static String testOutput;
    private static final boolean isTestOutputSet;
    private static final boolean isVerifyInstall;
    private static final boolean isVerifyUnInstall;

    static {
        String out = System.getProperty(JPACKAGE_TEST_OUTPUT);
        isTestOutputSet = (out != null);
        if (isTestOutputSet) {
            File file = new File(out);
            if (!file.exists()) {
                throw new AssertionError(file.getAbsolutePath() + " does not exist");
            }

            if (!file.isDirectory()) {
                throw new AssertionError(file.getAbsolutePath() + " is not a directory");
            }

            if (!file.canWrite()) {
                throw new AssertionError(file.getAbsolutePath() + " is not writable");
            }

            if (out.endsWith(File.separator)) {
                out = out.substring(0, out.length() - 2);
            }

            testOutput = out;
        }

        isVerifyInstall = (System.getProperty(JPACKAGE_VERIFY_INSTALL) != null);
        isVerifyUnInstall = (System.getProperty(JPACKAGE_VERIFY_UNINSTALL) != null);
    }

    public static boolean isTestOutputSet() {
        return isTestOutputSet;
    }

    public static boolean isVerifyInstall() {
        return isVerifyInstall;
    }

    public static boolean isVerifyUnInstall() {
        return isVerifyUnInstall;
    }

    public static void copyTestResults(List<String> files) throws Exception {
        if (!isTestOutputSet()) {
            return;
        }

        File dest = new File(testOutput);
        if (!dest.exists()) {
            dest.mkdirs();
        }

        if (JPackageHelper.isWindows()) {
            files.add(JPackagePath.getTestSrc() + File.separator + "install.bat");
            files.add(JPackagePath.getTestSrc() + File.separator + "uninstall.bat");
        } else {
            files.add(JPackagePath.getTestSrc() + File.separator + "install.sh");
            files.add(JPackagePath.getTestSrc() + File.separator + "uninstall.sh");
        }

        for (String file : files) {
            Path source = Path.of(file);
            Path target = Path.of(dest.toPath() + File.separator + source.getFileName());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void validateApp(String app) throws Exception {
        File outFile = new File("appOutput.txt");
        if (outFile.exists()) {
            outFile.delete();
        }

        int retVal = JPackageHelper.execute(outFile, app);
        if (retVal != 0) {
            throw new AssertionError(
                   "Test application exited with error: " + retVal);
        }

        if (!outFile.exists()) {
            throw new AssertionError(outFile.getAbsolutePath() + " was not created");
        }

        String output = Files.readString(outFile.toPath());
        String[] result = output.split("\n");
        if (result.length != 2) {
            System.err.println(output);
            throw new AssertionError(
                   "Unexpected number of lines: " + result.length);
        }

        if (!result[0].trim().equals("jpackage test application")) {
            throw new AssertionError("Unexpected result[0]: " + result[0]);
        }

        if (!result[1].trim().equals("args.length: 0")) {
            throw new AssertionError("Unexpected result[1]: " + result[1]);
        }
    }

    public static void validateOutput(String output) throws Exception {
        File file = new File(output);
        if (!file.exists()) {
            // Try lower case in case of OS is case sensitive
            file = new File(output.toLowerCase());
            if (!file.exists()) {
                throw new AssertionError("Cannot find " + file.getAbsolutePath());
            }
        }
    }
}
