/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4271200
 * @summary This test should show that the default position of a Frame
 *          should be on the physical screen for the GraphicsConfiguration.
 *          The togglebutton shows and hides an empty frame on the second monitor.
 *          The frame should be positioned at 0, 0 and is shown and hidden by clicking the button.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual Position
 */

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class Position extends JPanel implements ActionListener {

    static final String INSTRUCTIONS = """
        This test should show that the default position of a Frame
        should be on the physical screen for the specified GraphicsConfiguration.
        There is a window "Show/Hide" button.
        The button alternatively shows and hides an empty frame on the second monitor.
        The frame should be positioned at 0, 0 and is shown and hidden by clicking the button.
        The test passes if it behaves as described and fails otherwise.
        """;

    static volatile GraphicsDevice gd[];
    static volatile JFrame secondFrame;
    static volatile boolean on = true;

    public Position() {
        JPanel p = new JPanel();
        JButton b = new JButton("Show/Hide Window on other screen");
        b.addActionListener(this);
        p.add(b);
        add(p);
    }

    public void actionPerformed(ActionEvent e) {
        if (secondFrame == null) {
            secondFrame = new JFrame("screen1", gd[1].getDefaultConfiguration());
            secondFrame.setSize(500, 500);
            PassFailJFrame.addTestWindow(secondFrame);
        }
        secondFrame.setVisible(on);
        on = !on;
    }

    public static void main(String[] args) throws Exception {

        gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        if (gd.length < 2) { /* test runs only on a multi-screen environment */
            return;
        }

        PassFailJFrame.builder()
                .title("Screen Device Position Test")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(10)
                .columns(50)
                .splitUIBottom(Position::new)
                .build()
                .awaitAndCheck();
    }
}
