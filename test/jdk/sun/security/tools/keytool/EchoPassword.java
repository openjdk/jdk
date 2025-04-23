/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8354469
 * @summary keytool password prompt shows warning when cannot suppress echo
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual/othervm EchoPassword
 */

import javax.swing.*;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;

public class EchoPassword {

    static int counter = 0;

    public static void main(String[] args) throws Exception {
        final String keytool = String.format("\"%s/bin/keytool\" -keystore 8354469.ks",
                System.getProperty("java.home").replace("\\", File.separator));

        final String[] titles = {
                "Copy first command",
                "Copy second command",
                "Copy third command"
        };

        final String[] commands = {
                keytool + " -genkeypair",
                keytool + " -genkeypair | type",
                "(echo changeit & echo changeit ) | " + keytool + " -genkeypair"
        };

        final String message = """
                Perform the following steps and record the final result:

                1. Open a terminal or Windows Command Prompt window.

                2. Press "Copy First Command" to copy the first command into the system clipboard.
                   Paste it into the terminal window and execute the command.

                   When prompted, enter some characters and press Enter. Verify that the input is
                   hidden, and no warning about password echoing appears. Press Ctrl-C to exit.

                3. Press "Copy Second Command" to copy the second command into the system clipboard.
                   Paste it into the terminal window and execute the command.

                   When prompted, enter some characters and press Enter. Verify that the input is
                   echoed, and a warning about password echoing is shown. Press Ctrl-C to exit.

                4. Press "Copy Third Command" to copy the third command into the system clipboard.
                   Paste it into the terminal window and execute the command.

                   Verify that the password "changeit" is not shown in the command output, and
                   no warning about password echoing appears. It's OK to see an exception.

                Press "pass" if the behavior matches expectations; otherwise, press "fail".
                """;

        var copyButton = new JButton("Copy First Command");
        copyButton.addActionListener(_ -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(commands[counter]), null);
            counter = (counter + 1) % titles.length;
            copyButton.setText(titles[counter]);
        });

        PassFailJFrame.builder()
                .instructions(message)
                .columns(60)
                .addButton(copyButton)
                .build()
                .awaitAndCheck();
    }
}
