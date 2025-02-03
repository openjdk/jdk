/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.PanelUI;

/**
 * @test
 * @bug 8024926 8040279
 * @requires (os.family == "mac")
 * @summary [macosx] AquaIcon HiDPI support
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PixelatedTextTest
 */
public class PixelatedTextTest {

    private static final String INSTRUCTIONS = """
                Verify that the rendered image is not pixelated.
                1) Run the test at a resolution higher than 100%.
                2) Check that the displayed text is not pixelated.
                If the text is not pixelated, press PASS; otherwise, press FAIL.
                """;

    public static void main(String[] args) throws Exception {
        System.setProperty("swing.volatileImageBufferEnabled", "false");
        PassFailJFrame.builder()
                .title("Pixelated image test")
                .instructions(INSTRUCTIONS)
                .rows(10)
                .columns(35)
                .testUI(PixelatedTextTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JDialog createTestUI() {
        JTextPane textPane = new JTextPane();
        textPane.setText("This text should not appear pixelated");
        textPane.setEditable(false);
        textPane.setOpaque(false);
        textPane.setBorder(new EmptyBorder(10,10,10,10));

        JDialog dialog = new JDialog();
        dialog.setUndecorated(true);
        dialog.setLocationRelativeTo(null);

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(10,10,10,10));
        panel.setUI(new PanelUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(220, 180, 0, 200));
                g2.fill(new RoundRectangle2D.Double(5, 5,c.getWidth()-10,c.getHeight()-10,20,20));
            }
        });
        panel.setLayout(new BorderLayout());
        panel.add(textPane, BorderLayout.NORTH);
        dialog.getContentPane().add(panel);
        dialog.setBackground(new Color(0,0,0,0));
        dialog.pack();
        return dialog;
    }
}

