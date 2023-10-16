/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

/* @test
 * @bug 8276849
 * @summary Update the window icon on DPI scale factor changes
 * @requires os.family == "windows"
 * @run main/manual WindowIconUpdateOnDPIChangingTest
 */
public class WindowIconUpdateOnDPIChangingTest {

    private static volatile boolean testResult = false;
    private static final CountDownLatch countDownLatch = new CountDownLatch(1);
    private static JFrame frame;

    private static final String INSTRUCTIONS = "<html><body style=\"font-family: sans-serif\">\n"
            + "<b>INSTRUCTIONS:</b>\n"
            + "<p>Verify that the window icon is properly updated after changing the display DPI.</p>\n"
            + "\n"
            + "<p>The test is applicable for OSes that allows to change the display DPI\n"
            + "without rebooting the system. Press <b>PASS</b> for other systems.</p>\n"
            + "\n"
            + "<ol>"
            + "  <li>Set the display DPI scale factor to 100%</li>"
            + "  <li>Check that the string \"16\" is painted in the window icon.</li>"
            + "  <li>Set the display DPI scale factor to a different value,"
            + "   or move the frame to another screen with a different DPI scale factor.</li>"
            + "  <li>Check that the window icon is updated, it should display \"24\" on a screen"
            + "      with a 150% DPI scaling, or \"32\" on a screen with a 200% DPI scaling."
            + "      <br>If so, press <b>PASS</b>, otherwise press <b>FAIL</b>.</li>"
            + "<ol>\n"
            + "</body>\n"
            + "</html>\n";

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(WindowIconUpdateOnDPIChangingTest::createUI);
        if (!countDownLatch.await(15, TimeUnit.MINUTES)) {
            SwingUtilities.invokeAndWait(() -> frame.dispose());
            throw new RuntimeException("Timed out!");
        } else if (!testResult) {
            throw new RuntimeException("Test fails!");
        }
    }

    private static void createUI() {
        frame = new JFrame("Window Icon Update Test");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(640, 480);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                countDownLatch.countDown();
            }
        });
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(createInstrumentsPane(), BorderLayout.CENTER);
        frame.getContentPane().add(createControlPanel(), BorderLayout.SOUTH);
        frame.setIconImages(IntStream.rangeClosed(16, 32).mapToObj(size -> createIcon(size)).toList());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JTextPane createInstrumentsPane() {
        JTextPane instructionsPane = new JTextPane();
        instructionsPane.setContentType("text/html");
        instructionsPane.setText(INSTRUCTIONS);
        instructionsPane.setEditable(false);
        instructionsPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        return instructionsPane;
    }

    private static JPanel createControlPanel() {
        JButton passButton = new JButton("Pass");
        passButton.addActionListener(e -> testResult = true);
        passButton.addActionListener(e -> frame.dispose());

        JButton failButton = new JButton("Fail");
        failButton.addActionListener(e -> frame.dispose());

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        controlPanel.add(passButton);
        controlPanel.add(failButton);

        return controlPanel;
    }

    /**
     * Creates an icon of the size specified. The size is drawn at the center of the icon.
     *
     * @param size the size of the icon
     */
    private static Image createIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, size, size);
        g.setFont(new Font("Dialog", Font.BOLD, 12));
        g.setColor(Color.BLACK);

        TextLayout layout = new TextLayout(String.valueOf(size), g.getFont(), g.getFontRenderContext());
        int height = (int) layout.getBounds().getHeight();
        int width = (int) layout.getBounds().getWidth();
        layout.draw(g, (size - width) / 2f - 1, (size + height) / 2f);
        return image;
    }
}
