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
 * @bug 6734168
 * @requires (os.family == "windows")
 * @summary Verifies if BasicButtonUI uses wrong FontMetrics to Layout JButtons text
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestButtonFontMetrics
 */

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

public class TestButtonFontMetrics extends JButton {

    private static final String INSTRUCTIONS = """
        A frame will be shown with a button with text "Test";
        Click on the button.
        If the text on the button truncates,
        then test fails else test passes.""";

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        PassFailJFrame.builder()
                .title("TestButtonFontMetrics Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(TestButtonFontMetrics::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public TestButtonFontMetrics(String text) {
        super(text);
    }

    @Override
    public void paintComponent(Graphics g) {
        if (isEnabled()) {
            super.paintComponent(g);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        BufferedImage image = new BufferedImage(getWidth(), getHeight(),
                                BufferedImage.TYPE_INT_RGB);
        Graphics imgGraphics = image.getGraphics();

        super.paintComponent(imgGraphics);
        float[] kernel = { 0.1f, 0.1f, 0.1f, 0.1f, 0.2f, 0.1f, 0.1f, 0.1f, 0.1f };
        ConvolveOp op = new ConvolveOp(new Kernel(3, 3, kernel),
                                       ConvolveOp.EDGE_NO_OP, null);
        image = op.filter(image, null);
        g2.drawImage(image, 0, 0, null);
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("TestButtonFontMetrics");
        frame.setLayout(new GridBagLayout());
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        TestButtonFontMetrics button = new TestButtonFontMetrics("Test");
        button.addActionListener(new DisableListener());
        frame.add(button, new GridBagConstraints(0, 0, 1, 1, 0, 0,
             GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(
                  10, 10, 0, 10), 0, 0));
        frame.pack();
        return frame;
    }

    static class DisableListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            ((JComponent) e.getSource()).setEnabled(false);
        }
    }
}
