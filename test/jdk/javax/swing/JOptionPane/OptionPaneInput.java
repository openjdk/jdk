/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics2D;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 8235404
 * @summary Checks that JOptionPane doesn't block drawing strings on another component
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual OptionPaneInput
 */
public class OptionPaneInput {
    private static JFrame f;
    private static Canvas c;
    private static JTextField t;
    private static final String instructions = """
            1. Type "test" into the message dialog.
            2. Press enter key.
            3. Press OK button.
            4. If the OptionPaneInput frame has test drawn on it, pass. Otherwise fail.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame testFrame = new PassFailJFrame(instructions);

        SwingUtilities.invokeAndWait(() -> createGUI());
        testFrame.awaitAndCheck();
    }

    public static void createGUI() {
        f = new JFrame("OptionPaneInput");
        c = new Canvas();
        t = new JTextField();
        f.add(c);

        t.addActionListener(e -> {
            String text = t.getText();
            Graphics2D g2 = (Graphics2D)(c.getGraphics());
            g2.setColor(Color.BLACK);
            g2.drawString(text, 10, 10);
            System.out.println("drawing "+text);
            g2.dispose();
        });

        f.setSize(300, 100);
        PassFailJFrame.addTestWindow(f);
        PassFailJFrame.positionTestWindow(f, PassFailJFrame.Position.HORIZONTAL);
        f.setVisible(true);

        JOptionPane.showMessageDialog(f, t);
    }
}
