/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4226191
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary  Verify that Lightweight text components (like swing JTextField)
 *           work correctly with IM when there is an uneditable peered
 *           TextField/TextArea in the same parent Frame
 * @run main/manual JTextFieldTest
 */

import java.awt.FlowLayout;
import java.awt.TextField;

import javax.swing.JFrame;
import javax.swing.JTextField;

public class JTextFieldTest {
    private static final String INSTRUCTIONS =
            """
             Please run this test in a CJK (Chinese/Japanese/Korean) locale
             with input method support. If you could add input in the swing
             JTextField, then the test has passed!
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame
            .builder()
            .title("JTextFieldTest")
            .instructions(INSTRUCTIONS)
            .rows(5)
            .columns(40)
            .testUI(JTextFieldTest::createAndShowGUI)
            .build()
            .awaitAndCheck();
    }

    public static JFrame createAndShowGUI() {
        JFrame frame = new JFrame("Test Frame");
        frame.setLayout(new FlowLayout());
        TextField tf1 = new TextField("ABCDEFGH", 10);
        tf1.setEditable(false);
        JTextField tf2 = new JTextField("12345678", 10);
        frame.getContentPane().add(tf1);
        frame.getContentPane().add(tf2);
        frame.pack();
        return frame;
    }
}
