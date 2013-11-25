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

import java.util.Arrays;

/**
 * Super class for tests which need to attach jcmd to the current process.
 */
public class JcmdBase {

    private static ProcessBuilder processBuilder = new ProcessBuilder();

    /**
     * Attach jcmd to the current process
     *
     * @param toolArgs
     *            jcmd command line parameters, e.g. VM.flags
     * @return jcmd output
     * @throws Exception
     */
    public final static OutputAnalyzer jcmd(String... toolArgs)
            throws Exception {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jcmd");
        launcher.addToolArg(Integer.toString(ProcessTools.getProcessId()));
        for (String toolArg : toolArgs) {
            launcher.addToolArg(toolArg);
        }
        processBuilder.command(launcher.getCommand());
        System.out.println(Arrays.toString(processBuilder.command().toArray()).replace(",", ""));
        OutputAnalyzer output = new OutputAnalyzer(processBuilder.start());
        System.out.println(output.getOutput());

        output.shouldHaveExitValue(0);

        return output;
    }

}
