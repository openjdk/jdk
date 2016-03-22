/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4954921 8009259
 * @library /test/lib/share/classes
 * @modules java.base/jdk.internal.ref
 * @build jdk.test.lib.*
 * @build jdk.test.lib.process.*
 * @run main ExitOnThrow
 * @summary Ensure that if a cleaner throws an exception then the VM exits
 */
import java.util.Arrays;

import jdk.internal.ref.Cleaner;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ExitOnThrow {

    static final String cp = System.getProperty("test.classes", ".");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            String[] cmd = JDKToolLauncher.createUsingTestJDK("java")
                                          .addToolArg("-cp")
                                          .addToolArg(cp)
                                          .addToolArg("ExitOnThrow")
                                          .addToolArg("-executeCleaner")
                                          .getCommand();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            OutputAnalyzer out = ProcessTools.executeProcess(pb);
            System.out.println("======================");
            System.out.println(Arrays.toString(cmd));
            String msg = " stdout: [" + out.getStdout() + "]\n" +
                         " stderr: [" + out.getStderr() + "]\n" +
                         " exitValue = " + out.getExitValue() + "\n";
            System.out.println(msg);

            if (out.getExitValue() != 1)
                throw new RuntimeException("Unexpected exit code: " +
                                           out.getExitValue());

        } else {
            Cleaner.create(new Object(),
                           () -> { throw new RuntimeException("Foo!"); } );
            while (true) {
                System.gc();
                Thread.sleep(100);
            }
        }
    }

}
