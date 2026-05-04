/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @bug 4084454
 * @summary Make sure that you can set the text in a "password mode"
 *          text field and that it echoes as the current echo char.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SetPasswordTest
 */

public class SetPasswordTest extends Frame implements ActionListener {
    private String setText = "Set text";
    private String getText = "Get text";
    private TextField tfPassword;

    static final String INSTRUCTIONS = """
            The purpose of this test is to ensure that when using a textField for
            password entry that text set programmatically is not shown in the clear.

            We also test "pasting" text into the text field.

            1.  Press the "Set Text" button
                Text should appear as '*' chars
              - if the string "secret" appears then the test is failed.
            2.  Use the mouse to select (highlight) all the text and press the DELETE key
            3.  Use the system's copy/paste functionality to copy a text string from the
                desktop or this window, and paste it into the text field.
            4.  Text should appear in the text field as '*' chars
              - if the string you pasted appears then the test is failed.
            5.  press the "Get Text" button and the string you pasted
                should be printed in the log area
              - if it prints echo symbols instead the test is failed.
            """;

    public SetPasswordTest() {
        setLayout(new FlowLayout());
        tfPassword = new TextField("Initial text", 30);
        tfPassword.setEchoChar('*');
        add(tfPassword);

        Button b1 = new Button(setText);
        b1.addActionListener(this);
        add(b1);

        Button b2 = new Button(getText);
        b2.addActionListener(this);
        add(b2);
        pack();
    }

    public void actionPerformed(ActionEvent evt) {
        String ac = evt.getActionCommand();
        if (setText.equals(ac)) {
            tfPassword.setText("secret");
        }

        if (getText.equals(ac)) {
            PassFailJFrame.log("Text: \"" + tfPassword.getText() + "\"");
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Set Password Test")
                .testUI(SetPasswordTest::new)
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(40)
                .logArea()
                .build()
                .awaitAndCheck();
    }
}
