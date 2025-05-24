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

import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.event.HyperlinkEvent;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;

public class EchoPassword {

    static JLabel label;

    public static void main(String[] args) throws Exception {

        final String keytool = String.format("\"%s/bin/keytool\" -keystore 8354469.ks",
                System.getProperty("java.home").replace("\\", File.separator));

        final String firstCommand = keytool + " -genkeypair";
        final String secondCommand = keytool + " -genkeypair | type";
        final String thirdCommand = "(echo changeit & echo changeit ) | " + keytool + " -genkeypair";

        final String message = String.format("""
                <html>Perform the following steps and record the final result:
                <ol>
                <li>Open a terminal or Windows Command Prompt window.

                <li>Click <a href='First'>Copy First Command</a> to copy the following command into
                the system clipboard. Paste it into the terminal window and execute the command.
                <pre>
                %s
                </pre>
                When prompted, enter some characters and press Enter. Verify that the input is
                hidden, and no warning about password echoing appears. Press Ctrl-C to exit.

                <li>Click <a href='Second'>Copy Second Command</a> to copy the following command into
                the system clipboard. Paste it into the terminal window and execute the command.
                <pre>
                %s
                </pre>
                When prompted, enter some characters and press Enter. Verify that the input is
                echoed, and a warning about password echoing is shown. Press Ctrl-C to exit.

                <li>Click <a href='Third'>Copy Third Command</a> to copy the following command into
                the system clipboard. Paste it into the terminal window and execute the command.
                <pre>
                %s
                </pre>
                Verify that the password "changeit" is not shown in the command output, and
                no warning about password echoing appears. It's OK to see an exception.
                </ol>
                Press "pass" if the behavior matches expectations; otherwise, press "fail".
                """, firstCommand, secondCommand, thirdCommand);

        PassFailJFrame.builder()
                .instructions(message)
                .addHyperlinkListener(e -> {
                    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                                new StringSelection(switch (e.getDescription()) {
                                    case "First" -> firstCommand;
                                    case "Second" -> secondCommand;
                                    default -> thirdCommand;
                                }), null);
                        label.setText(e.getDescription() + " command copied");
                        if (e.getSource() instanceof JEditorPane ep) {
                            ep.getCaret().setVisible(false);
                        }
                    }
                })
                .columns(100)
                .splitUIBottom(() -> {
                    label = new JLabel("Status");
                    return label;
                })
                .build()
                .awaitAndCheck();
    }
}
