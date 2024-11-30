/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 4800187
 * @summary REGRESSION:show the wrong selection when there are \r characters in the text
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TextAreaCRLFAutoDetectManualTest
 */

public class TextAreaCRLFAutoDetectManualTest {
    static int flag = 1;

    private static final String INSTRUCTIONS = """
                Please click the button several times.
                If you see the text '679' selected on the left TextArea
                and the same text on the right TextArea
                each time you press the button,
                the test passed, else failed.
                """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("TextAreaCRLFAutoDetectManualTest")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(TextAreaCRLFAutoDetectManualTest::createGUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createGUI() {
        Frame f = new Frame("TextAreaCRLFAutoDetectManualTest");

        TextArea ta1 = new TextArea(5, 20);
        TextArea ta2 = new TextArea(5, 20);

        TextField tf1 = new TextField("123", 20);
        TextField tf2 = new TextField("567", 20);
        TextField tf3 = new TextField("90", 20);

        Button b = new Button("Click Me Several Times");

        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                ta1.setText("");
                ta2.setText("");
                flag++;
                String eoln = ((flag % 2) != 0) ? "\r\n" : "\n";
                ta1.setText(eoln + tf1.getText() + eoln + tf2.getText() + eoln + tf3.getText() + eoln);
                ta1.select(6, 10);
                ta2.setText(ta1.getSelectedText());
                ta1.requestFocus();
            }
        });

        f.setLayout(new FlowLayout());

        Panel tfpanel = new Panel();
        tfpanel.setLayout(new GridLayout(3, 1));
        tfpanel.add(tf1);
        tfpanel.add(tf2);
        tfpanel.add(tf3);
        f.add(tfpanel);

        f.add(ta1);
        f.add(ta2);
        f.add(b);

        f.pack();
        return f;
    }
}
