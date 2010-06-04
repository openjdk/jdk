/*
 * Copyright (c) 1998, 1999, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test Native2ASCII error messages
 *
 */

import java.io.*;
import sun.tools.native2ascii.*;
import java.util.*;

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

    public static void main(String args[]) throws Exception {
        String[] command;
        Process p = null;
        BufferedReader in = null;

        // Construct a command that runs the test in other vm
        // Exec another vm to run test in
        // Read the result to determine if test failed

        command = getComString("-encoding");
        p = Runtime.getRuntime().exec(command);
        in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        checkResult(in, "err.bad.arg");

        File f0 = new File(System.getProperty("test.src", "."), "test123");
        String path0 = f0.getPath();
        if ( f0.exists() ) {
            throw new Error("Input file should not exist: " + path0);
        }

        command = getComString(path0);
        p = Runtime.getRuntime().exec(command);
        in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        checkResult(in, "err.cannot.read");

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

        command = getComString(path1, path2);
        p = Runtime.getRuntime().exec(command);
        in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        checkResult(in, "err.cannot.write");
    }


    private static void checkResult(BufferedReader in, String errorExpected)
                                                           throws Exception {
        String errorReceived;
        errorReceived = in.readLine();
        assert errorReceived != null : "First readline cannot be null";
        errorExpected = rsrc.getString(errorExpected);
        assert errorExpected != null : "Expected message cannot be null";
        StringBuffer error = new StringBuffer(errorExpected);
        int start = errorExpected.indexOf("{0}");
        if (start >= 0) {
            error.delete(start, start+3);
            errorExpected = error.toString();
        }
        //System.out.println("received: " + errorReceived);
        //System.out.println("expected: " + errorExpected);
        if (!errorReceived.endsWith(errorExpected))
            throw new RuntimeException("Native2ascii bad arg error broken.");
    }

    private static String[] getComString(String arg2) {
        String[] coms = new String[2];
        coms[0] = getPathString();
        coms[1] = arg2;
        return coms;
    }

    private static String[] getComString(String arg2, String arg3) {
        String[] coms = new String[3];
        coms[0] = getPathString();
        coms[1] = arg2;
        coms[2] = arg3;
        return coms;
    }

    /*
     * Search for path to native2ascii
     */
    private static String getPathString() {
        String path = System.getProperty("java.home") + File.separator +
            "bin" + File.separator + "native2ascii";
        if (File.separatorChar == '\\') {
            path = path + ".exe";
        }
        File f = new File(path);
        if (!f.exists()) {
            System.out.println("Cannot find native2ascii at "+path);
            path = System.getProperty("java.home") + File.separator + ".." +
                   File.separator + "bin" + File.separator + "native2ascii";
            if (File.separatorChar == '\\') {
                path = path + ".exe";
            }
            f = new File(path);
            if (!f.exists())
                throw new RuntimeException("Cannot find native2ascii at "+path);
            System.out.println("Using native2ascii at "+path);
        }
        return path;
    }

}
