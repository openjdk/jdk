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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 4048664 4065506 4122094 4171979
 * @summary Test if Dialog can be successfully hidden, see that no other app
 *          comes to front, see if hide + dispose causes assertion failure
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual HideDialogTest
 */

public class HideDialogTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. A Frame should appear with a "test" button in it
                2. Click on the "test" button. A Dialog will appear with a "dismiss" button
                   and a "dismiss-with-dispose" button
                3. First, click on the "dismiss-with-dispose" button. Verify that
                   no assertion failure appears.
                4. Now, click on the "dismiss" button. The Dialog should go away.
                5. Repeat from (2) 10-20 times.
                6. When the dialog goes away check that the frame window does not briefly
                   get obscured by another app or repaint it's entire area. There should be
                   no flicker at all in areas obscured by the dialog. (4065506 4122094)
                   If there is the test fails.
                7. If the Dialog is successfully hidden each time, the test passed.  If the
                   Dialog did not hide, the test failed (4048664).

                NOTE: When the dialog does not go away (meaning the bug has manifested itself),
                the "dismiss-with-dispose" button can be used to get rid of it.
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(new MyFrame())
                .build()
                .awaitAndCheck();
    }
}

class MyDialog extends Dialog {
    public MyDialog(Frame f) {
        super(f, "foobar", true);
        setSize(200, 200);
        setLayout(new BorderLayout());
        Panel p = new Panel();
        p.setLayout(new FlowLayout(FlowLayout.CENTER));
        Button okButton;
        okButton = new Button("dismiss");
        p.add(okButton);
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.out.println("Calling setVisible(false)");
                setVisible(false);
            }
        });
        Button newButton;
        p.add(newButton = new Button("dismiss-with-dispose"));
        newButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.out.println("Calling setVisible(false) + dispose()");
                setVisible(false);
                dispose();
            }
        });
        add("South", p);
        pack();
    }
}

class MyFrame extends Frame implements ActionListener {
    public MyFrame() {
        super();
        setSize(600, 400);
        setTitle("HideDialogTest");
        setLayout(new BorderLayout());
        Panel toolbar = new Panel();
        toolbar.setLayout(new FlowLayout(FlowLayout.LEFT));
        Button testButton = new Button("test");
        testButton.addActionListener(this);
        toolbar.add(testButton);
        add("North", toolbar);
    }

    public void actionPerformed(ActionEvent e) {
        String s = e.getActionCommand();
        if (s.equals("test")) {
            System.out.println("Begin test");
            MyDialog d = new MyDialog(this);
            d.setVisible(true);
            System.out.println("End test");
        }
    }

    public void paint(Graphics g) {
        for (int i = 0; i < 10; i++) {
            g.setColor(Color.red);
            g.fillRect(0, 0, 2000, 2000);
            g.setColor(Color.blue);
            g.fillRect(0, 0, 2000, 2000);
        }
    }
}
