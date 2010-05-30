/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4156278
 * @summary Basic functional test for
 *          Runtime.exec(String[] command, String[] env, File path) and
 *          Runtime.exec(String command, String[] env, File path).
 *
 * @build SetCwd
 * @run shell setcwd.sh
 */
import java.io.*;

public class SetCwd {
    public static void testExec(String cmd, String[] cmdarray, boolean flag)
        throws Exception {
        File dir = new File(".");
        File[] files = dir.listFiles();
        String curDir = dir.getCanonicalPath();

        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory() && (new File(f, "SetCwd.class")).exists()) {
                String newDir = f.getCanonicalPath();
                // exec a new SetCwd in the sub directory
                Process p = null;
                if (flag) {
                    p = Runtime.getRuntime().exec(cmd, null, f);
                } else {
                    p = Runtime.getRuntime().exec(cmdarray, null, f);
                }

                BufferedReader in = new BufferedReader
                    (new InputStreamReader(p.getInputStream()));
                // Read back output from child
                String s = in.readLine();
                if (!s.startsWith(newDir)) {
                    throw new Exception("inconsistent directory after exec");
                }
                // Join on the child
                p.waitFor();
            }
        }
        System.out.println(curDir);
    }

    public static void main (String args[]) throws Exception {
        String cmdarray[] = new String[2];
        cmdarray[0] = System.getProperty("java.home") + File.separator +
            "bin" + File.separator + "java";
        cmdarray[1] = "SetCwd";
        String cmd = cmdarray[0] + " " + cmdarray[1];
        // test the two new methods
        testExec(cmd, null, true);
        testExec(null, cmdarray, false);
    }
}
