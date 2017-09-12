/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4236543 8129833
 * @summary rmic w/o -d should put class files in package directory
 * @author Dana Burns
 * @library ../../../../java/rmi/testlibrary
 *
 * @build StreamPipe
 * @run main/othervm RmicDefault
 */

/*
 * Ensure that, in the absence of a -d argument, rmic will deposit
 * the generated class files in the package directory as opposed
 * to the old behaviour of depositing them in the local directory.
 * JavaTest/jtreg does not support setting the classpath yet, so do
 * the javac directly.
 */

import java.io.File;

public class RmicDefault {
    private static final String PKG_DIR = "packagedir";
    private static final String[] remoteClasses = new String[] {
        "RmicMeImpl", "AppletServer"
    };

    public static void main(String args[]) throws Exception {
        String javahome = System.getProperty("java.home");
        String testclasses = System.getProperty("test.classes");
        String userDir = System.getProperty("user.dir");
        String cmd = javahome + File.separator + "bin" + File.separator +
            "javac -d " + testclasses + " " + System.getProperty("test.src") +
            File.separator + PKG_DIR + File.separator;

        for (String clz : remoteClasses) {
            System.out.println("Working on class " + clz);
            Process javacProcess = Runtime.getRuntime().exec(cmd + clz + ".java");

            StreamPipe.plugTogether(javacProcess.getInputStream(), System.out);
            StreamPipe.plugTogether(javacProcess.getErrorStream(), System.out);

            javacProcess.waitFor();

            Process rmicProcess = Runtime.getRuntime().exec(
                javahome + File.separator + "bin" + File.separator +
                "rmic -classpath " + testclasses + " " + PKG_DIR + "." + clz);

            StreamPipe.plugTogether(rmicProcess.getInputStream(), System.out);
            StreamPipe.plugTogether(rmicProcess.getErrorStream(), System.err);

            rmicProcess.waitFor();
            int exitCode = rmicProcess.exitValue();
            if (rmicProcess.exitValue() != 0) {
                throw new RuntimeException("Rmic failed. The exit code is " + exitCode);
            }

            File stub = new File(userDir + File.separator + PKG_DIR + File.separator + clz + "_Stub.class");
            if (!stub.exists()) {
                throw new RuntimeException("TEST FAILED: could not find stub");
            }
        }

        System.err.println("TEST PASSED");
    }
}
