/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.testlibrary;

import java.util.ArrayList;

public class JcmdBase {

    private static ProcessBuilder processBuilder = new ProcessBuilder();

    /**
     * Attach jcmd to the current process
     *
     * @param commandArgs
     *            jcmd command line parameters, e.g. JFR.start
     * @return jcmd output
     * @throws Exception
     */
    public final static OutputAnalyzer jcmd(String... commandArgs)
            throws Exception {
        ArrayList<String> cmd = new ArrayList<String>();
        String cmdString = "";

        // jcmd from the jdk to be tested
        String jcmdPath = JdkFinder.getTool("jcmd", false);
        cmd.add(jcmdPath);
        cmdString += jcmdPath;

        String pid = Integer.toString(ProcessTools.getProcessId());
        cmd.add(pid);
        cmdString += " " + pid;

        for (int i = 0; i < commandArgs.length; i++) {
            cmd.add(commandArgs[i]);
            cmdString += " " + commandArgs[i];
        }

        // Log command line for debugging purpose
        System.out.println("Command line:");
        System.out.println(cmdString);

        processBuilder.command(cmd);
        OutputAnalyzer output = new OutputAnalyzer(processBuilder.start());

        // Log output for debugging purpose
        System.out.println("Command output:");
        System.out.println(output.getOutput());

        if (output.getExitValue() != 0) {
            throw new Exception(processBuilder.command()
                    + " resulted in exit value " + output.getExitValue()
                    + " , expected to get 0");
        }

        return output;
    }

}
