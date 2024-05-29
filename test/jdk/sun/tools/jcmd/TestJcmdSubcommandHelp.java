/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat Inc.
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
 * @bug 8332124
 * @summary Test to verify jcmd accepts the "help" suboption as a command argument
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run main/othervm TestJcmdSubcommandHelp
 *
 */

import jdk.test.lib.process.OutputAnalyzer;


public class TestJcmdSubcommandHelp {

    private static final String HELP = "help";
    private static final String CMD = "VM.metaspace";

    public static void main(String[] args) throws Exception {

        // Sanity check with empty input
        OutputAnalyzer output = JcmdBase.jcmd();
        output.shouldContain("The following commands are available:");

        // Sanity check with existing usage for "help <cmd>"
        output = JcmdBase.jcmd(HELP, CMD);
        String expectedOutput = output.getOutput();
        output.shouldNotContain("Unknown diagnostic command");

        // Check help as a suboption to the command is accepted i.e. "<cmd> help"
        output = JcmdBase.jcmd(CMD, HELP);
        String issuedOutput = output.getOutput();
        output.shouldNotContain("Unknown diagnostic command");

        if (!expectedOutput.equals(output.getOutput())) {
            System.out.println("Expected output: ");
            System.out.println(expectedOutput);
            System.out.println("Issued output: ");
            System.out.println(issuedOutput);
            throw new Exception("Expected jcmd to accept 'help' suboption as a command argument" +
                                " and issue the same help output.");
        }

        // Issue incorrect suboption to command argument containing 'help'
        String incorrectOpt = "helpln;n";
        output = JcmdBase.jcmd(CMD, incorrectOpt);
        output.shouldContain("Unknown argument \'" + incorrectOpt + "\' in diagnostic command.");

        // Issue multiple suboptions along with 'help'
        output = JcmdBase.jcmd(CMD, HELP, "basic");
        output.shouldContain("Unknown argument \'" + HELP + "\' in diagnostic command.");
    }
}
