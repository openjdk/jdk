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
 * @summary keytool password does not echo in multiple cases
 * @library /java/awt/regtesthelpers
 * @modules java.base/jdk.internal.util
 * @build PassFailJFrame
 * @run main/manual/othervm EchoPassword
 */

import jdk.internal.util.OperatingSystem;

import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.event.HyperlinkEvent;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Path;

public class EchoPassword {

    static JLabel label;

    public static void main(String[] args) throws Exception {

        var ks1 = "\"" + Path.of("8354469.ks1").toAbsolutePath() + "\"";
        var ks2 = "\"" + Path.of("8354469.ks2").toAbsolutePath() + "\"";
        var ks3 = "\"" + Path.of("8354469.ks3").toAbsolutePath() + "\"";

        final String keytool = "\"" + System.getProperty("java.home")
                + File.separator + "bin" + File.separator + "keytool\"";
        final String nonASCII = "äöäöäöäö";

        final String[][] commands = {
                // Input password from real Console
                {"First command", keytool + " -keystore " + ks1
                        + " -genkeypair -keyalg ec -dname cn=a -alias first"},
                // Input password from limited Console (when stdout is redirected)
                {"Second command", keytool + " -keystore " + ks2
                        + " -genkeypair -keyalg ec -dname cn=b -alias second | sort"},
                // Input password from System.in stream
                {"Third command", "echo changeit| " + keytool + " -keystore " + ks1
                        + " -genkeypair -keyalg ec -dname cn=c -alias third"},
                // Ensure limited Console does not write a newline to System.out
                {"Fourth command", keytool + " -keystore " + ks1
                        + " -exportcert -alias first | "
                        + keytool + " -printcert -rfc"},
                // Non-ASCII password from System.in
                {"Fifth command", "("
                        // Solution 2 of https://stackoverflow.com/a/29747723
                        + (OperatingSystem.isWindows()
                        ? ("echo " + nonASCII + "^&echo " + nonASCII + "^&rem.")
                        : ("echo " + nonASCII + "; echo " + nonASCII))
                        + ") | " + keytool + " -keystore " + ks3
                                + " -genkeypair -alias a -keyalg ec -dname cn=a"},
                // Non-ASCII password from Console
                {"Sixth command", keytool + " -keystore " + ks3
                        + " -exportcert -alias a -rfc"},
                {"The password", nonASCII}
        };

        final String message = String.format("""
                <html>Open a terminal or Windows Command Prompt window, perform
                the following steps, and record the final result. Each time you
                click a link to copy something, make sure the status line at the
                bottom shows the link has been successfully clicked.
                <h3>Part I: Password Echoing Tests</h3>
                <ol>
                <li>Click <a href='c0'>Copy First Command</a> to copy the
                following command into the system clipboard. Paste it into the
                terminal window and execute the command.
                <p><code>
                %s
                </code><p>
                When prompted, enter "changeit" and press Enter. When prompted
                again, enter "changeit" again and press Enter. Verify that the
                two password prompts show up on different lines, both
                passwords are hidden, and a key pair is generated successfully.

                <li>Click <a href='c1'>Copy Second Command</a> to copy the
                following command into the system clipboard. Paste it into the
                terminal window and execute the command.
                <p><code>
                %s
                </code><p>
                When prompted, enter "changeit" and press Enter. When prompted
                again, enter "changeit" again and press Enter. Verify that the
                two password prompts show up on different lines, both
                passwords are hidden, and a key pair is generated successfully.

                <li>Click <a href='c2'>Copy Third Command</a> to copy the
                following command into the system clipboard. Paste it into the
                terminal window and execute the command.
                <p><code>
                %s
                </code><p>
                You will see a prompt but you don't need to enter anything.
                Verify that the password "changeit" is not shown in the command
                output and a key pair is generated successfully.

                <li>Click <a href='c3'>Copy Fourth Command</a> to copy the
                following command into the system clipboard. Paste it into the
                terminal window and execute the command.
                <p><code>
                %s
                </code><p>
                When prompted, enter "changeit" and press Enter. Verify that the
                password is hidden and a PEM certificate is correctly shown.
                </ol>
                <h3>Part II: Interoperability on Non-ASCII Passwords</h3>
                <ol>
                <li>Click <a href='c4'>Copy Fifth Command</a> to copy the
                following command into the system clipboard. Paste it into the
                terminal window and execute the command.
                <p><code>
                %s
                </code><p>
                Verify that a key pair is generated successfully.

                <li>Click <a href='c5'>Copy Sixth Command</a> to copy the
                following command into the system clipboard. Paste it into the
                terminal window and execute the command.
                <p><code>
                %s
                </code><p>
                When prompted, click <a href='c6'>Copy Password</a> to copy the
                password. Paste it into the terminal window and press Enter.
                Verify that the password is hidden and a PEM certificate is
                correctly shown.
                </ol>
                Press "pass" if the behavior matches expectations;
                otherwise, press "fail".
                """, commands[0][1], commands[1][1], commands[2][1], commands[3][1],
                commands[4][1], commands[5][1], commands[6][1]);

        PassFailJFrame.builder()
                .instructions(message)
                .rows(40).columns(100)
                .hyperlinkListener(e -> {
                    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                        int pos = Integer.parseInt(e.getDescription().substring(1));
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                                new StringSelection(commands[pos][1]), null);
                        label.setText(commands[pos][0] + " copied");
                        if (e.getSource() instanceof JEditorPane ep) {
                            ep.getCaret().setVisible(false);
                        }
                    }
                })
                .splitUIBottom(() -> {
                    label = new JLabel("Status");
                    return label;
                })
                .build()
                .awaitAndCheck();
    }
}
