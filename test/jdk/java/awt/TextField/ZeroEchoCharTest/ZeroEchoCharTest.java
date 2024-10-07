/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @bug 4307281
 * @summary verify that after setting the echo char to 0 disguises are
 *          removed and user input is echoed to the screen unchanged.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ZeroEchoCharTest
 */

public class ZeroEchoCharTest extends Frame implements ActionListener {
    private final TextField textfield = new TextField();
    private final Button button1 = new Button("Set echo char to *");
    private final Button button2 = new Button("Set echo char to 0");
    static final String INSTRUCTIONS = """
            1.Type in the text field. The user input must be echoed unchanged.
            2.Set echo char to '*' by pressing the corresponding button.
              If all characters in the text field aren't immediately replaced
              with '*', the test fails.
            3.Set echo char to 0 by pressing the corresponding button.
              If disguises in the text field don't immediately revert to
              the original characters, the test fails.
            4.Type in the text field. If the input is echoed unchanged,
              the test passes. Otherwise, the test fails.
            """;

    public ZeroEchoCharTest() {
        button1.addActionListener(this);
        button2.addActionListener(this);

        setLayout(new GridLayout(3, 1));

        add(textfield);
        add(button1);
        add(button2);
        pack();
    }

    public void actionPerformed(ActionEvent event) {
        if (event.getSource() == button1) {
            textfield.setEchoChar('*');
        } else if (event.getSource() == button2) {
            textfield.setEchoChar((char)0);
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Zero Echo Char Test")
                .testUI(ZeroEchoCharTest::new)
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(40)
                .build()
                .awaitAndCheck();
    }
}
