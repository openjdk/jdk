/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @run main/timeout=600 TestNormal
 * @bug 8020802 8156807
 * @summary Need an ability to create jar files that are invariant to the pack200 packing/unpacking
 * @author Alexander Zuev
 */

import java.io.*;

public class TestNormal {
    private static String FS = File.separator;

    public static void main(String args[]) throws Exception {
        String testdir = Utils.TEST_CLS_DIR.getAbsolutePath();

        try {
            String jarCmd = Utils.getJarCmd();
            String packCmd = Utils.getPack200Cmd();

            // create the original jar
            Utils.runExec(jarCmd, "cf", "original.jar", "-C", testdir, ".");

            // create the reference jar
            Utils.runExec(packCmd, "-r", "repacked.jar", "original.jar");

            // create the normalized jar using jar(1)
            Utils.runExec(jarCmd, "cnf", "normalized.jar", "-C", testdir, ".");

            // compare archive contents bit wise, these should be identical!
            Utils.doCompareBitWise(new File("repacked.jar"),
                    new File("normalized.jar"));
        } finally {
           Utils.cleanup();
        }
    }
}
