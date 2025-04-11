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
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/* @test
   @bug 8303904
   @requires (os.family == "mac")
   @summary Images appear pixelated at resolutions higher than 100%
            when volatile image buffering is disabled.
   @library /java/awt/regtesthelpers
   @build PassFailJFrame
   @run main PixelatedImageTest
 */
public class PixelatedImageTest {

    private static final String INSTRUCTIONS = """
                Verify that the rendered image is not pixelated.
                1) Run the test at a resolution higher than 100%.
                2) Check that the displayed text is not pixelated.
                If the text is not pixelated, press PASS; otherwise, press FAIL.
                """;

    public static void main(String[] args) throws Exception {
        System.setProperty("swing.volatileImageBufferEnabled", "false");
        PassFailJFrame.builder()
                .title("Pixelated image Test")
                .instructions(INSTRUCTIONS)
                .rows((5))
                .columns(35)
                .testUI(PixelatedImageTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JWindow createTestUI() {
        String text = """
                This text should appear clear, without any pixelation.
                The backgroud has to be transparent with no black borders
                """;
        JTextPane textPane = new JTextPane();
        textPane.setText(text);
        textPane.setEditable(false);
        textPane.setOpaque(false);
        textPane.setBorder(new EmptyBorder(10,10,10,10));

        JWindow window = new JWindow();
        window.setLocationRelativeTo(null);

        JPanel panel = new JPanel() {
            @Override
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(220, 180, 0, 200));
                g2.fillRect(5, 5, getWidth(), getHeight());
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(10,10,10,10));
        panel.setLayout(new BorderLayout());
        panel.add(textPane, BorderLayout.NORTH);

        window.getContentPane().add(panel);
        window.setBackground(new Color(0,0,0,0));
        window.setSize(200, 200);
        return window;
    }
}

