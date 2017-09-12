/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import jdk.test.lib.Asserts;

/*
 * @test
 * @key cte_test
 * @bug 4345157
 * @summary JDK 1.3.0 alters thread signal mask
 * @requires (vm.simpleArch == "sparcv9")
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @compile Prog.java
 * @run main/native ThreadSignalMask
 */
public class ThreadSignalMask {

    public static void main(String args[]) throws Exception {

        String testClasses = getSystemProperty("test.classes");

        String testNativePath = getSystemProperty("test.nativepath");

        String testJdk = getSystemProperty("test.jdk");

        Path currentDirPath = Paths.get(".");

        Path classFilePath = Paths.get(testClasses,
                Prog.class.getSimpleName() + ".class");

        // copy Prog.class file to be invoked from native
        Files.copy(classFilePath,
                currentDirPath.resolve(Prog.class.getSimpleName() + ".class"),
                StandardCopyOption.REPLACE_EXISTING);

        Path executableFilePath = Paths.get(testNativePath,
                ThreadSignalMask.class.getSimpleName());

        Path executableFileLocalPath = currentDirPath.resolve(
                ThreadSignalMask.class.getSimpleName());

        // copy compiled native executable ThreadSignalMask
        Files.copy(executableFilePath,
                executableFileLocalPath,
                StandardCopyOption.REPLACE_EXISTING);

        executableFileLocalPath.toFile().setExecutable(true);

        long[] intervalsArray = {2000, 5000, 10000, 20000};

        List<String> processArgs = Arrays.asList(
                executableFileLocalPath.toString(),
                testJdk);
        ProcessBuilder pb = new ProcessBuilder(processArgs);
        pb.redirectOutput(Redirect.INHERIT);
        pb.redirectError(Redirect.INHERIT);
        int result = 0;
        for (long interval : intervalsArray) {
            Process p = pb.start();

            // sleep for a specified period of time to let native run
            sleep(interval);
            p.destroy();

            // wait for process to finish, get exit value and validate it
            result = p.waitFor();
            System.out.println("Result = " + result);
            if (result == 0) {
                break;
            }
        }

        Asserts.assertEquals(result, 0);
    }

    // Utility method to handle Thread.sleep
    private static void sleep(long millis) throws InterruptedException {
        System.out.println("Sleep for " + millis);
        Thread.sleep(millis);
    }

    // Utility method to retrieve and validate system properties
    private static String getSystemProperty(String propertyName) throws Error {
        String systemProperty = System.getProperty(propertyName, "").trim();
        System.out.println(propertyName + " = " + systemProperty);
        if (systemProperty.isEmpty()) {
            throw new Error("TESTBUG: property " + propertyName + " is empty");
        }
        return systemProperty;
    }
}
