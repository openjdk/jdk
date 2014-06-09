/*
 * Copyright (c) 1998, 1999, 2014 Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4136352
 * @library /lib/testlibrary
 * @summary Test Native2ASCII error messages
 *
 */

import java.io.File;
import java.util.ResourceBundle;
import java.util.MissingResourceException;
import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.JDKToolLauncher;
import jdk.testlibrary.ProcessTools;

public class NativeErrors {

    private static ResourceBundle rsrc;

    static {
        try {
            rsrc = ResourceBundle.getBundle(
                     "sun.tools.native2ascii.resources.MsgNative2ascii");
        } catch (MissingResourceException e) {
            throw new Error("Missing message file.");
        }
    }

    public static void main(String args[]) throws Throwable {
        // Execute command in another vm. Verify stdout for expected err msg.

        // Test with no input file given.
        checkResult(executeCmd("-encoding"), "err.bad.arg");

        File f0 = new File(System.getProperty("test.src", "."), "test123");
        String path0 = f0.getPath();
        if ( f0.exists() ) {
            throw new Error("Input file should not exist: " + path0);
        }
        checkResult(executeCmd(path0), "err.cannot.read");

        File f1 = new File(System.getProperty("test.src", "."), "test1");
        File f2 = File.createTempFile("test2", ".tmp");
        String path1 = f1.getPath();
        String path2 = f2.getPath();
        if ( !f1.exists() ) {
            throw new Error("Missing input file: " + path1);
        }
        if ( !f2.setWritable(false) ) {
            throw new Error("Output file cannot be made read only: " + path2);
        }
        f2.deleteOnExit();
        if ( f2.canWrite() ) {
            String msg = "Output file is still writable. " +
                    "Probably because test is run as root. Read-only test skipped.";
            System.out.println(msg);
        } else {
            // Test write to a read-only file.
            checkResult(executeCmd(path1, path2), "err.cannot.write");
        }
    }

    private static String executeCmd(String... toolArgs) throws Throwable {
        JDKToolLauncher cmd = JDKToolLauncher.createUsingTestJDK("native2ascii");
        for (String s : toolArgs) {
            cmd.addToolArg(s);
        }
        OutputAnalyzer output = ProcessTools.executeProcess(cmd.getCommand());
        if (output == null || output.getStdout() == null) {
            throw new Exception("Output was null. Process did not finish correctly.");
        }
        if (output.getExitValue() == 0) {
            throw new Exception("Process exit code was 0, but error was expected.");
        }
        return output.getStdout();
    }

    private static void checkResult(
            String errorReceived, String errorKey) throws Exception {
        String errorExpected = rsrc.getString(errorKey);
        if (errorExpected == null) {
            throw new Exception("No error message for key: " + errorKey);
        }
        // Remove template tag from error message.
        errorExpected = errorExpected.replaceAll("\\{0\\}", "");

        System.out.println("received: " + errorReceived);
        System.out.println("expected: " + errorExpected);
        if (errorReceived.indexOf(errorExpected) < 0) {
            throw new RuntimeException("Native2ascii bad arg error broken.");
        }
    }

}
