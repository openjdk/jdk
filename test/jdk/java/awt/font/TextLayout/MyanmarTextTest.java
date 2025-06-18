/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8223558
 * @key headful
 * @summary Verifies that Myanmar script is rendered correctly:
 *          two characters combined into one glyph
 * @library /test/lib
 * @build jtreg.SkippedException
 * @run main MyanmarTextTest
 */

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.plaf.TextUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;

import jtreg.SkippedException;

public class MyanmarTextTest {
    private static final String TEXT = "\u1000\u103C";

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();

    private static final String[] FONTS_WINDOWS = {"Myanmar Text", "Noto Sans Myanmar"};
    private static final String[] FONTS_LINUX = {"Padauk", "Noto Sans Myanmar", "Myanmar Text"};
    private static final String[] FONTS_MACOS = {"Myanmar MN", "Noto Sans Myanmar", "Myanmar Text"};

    private static final String[] FONT_CANDIDATES = selectFontCandidates();
    private static final String FONT_NAME = selectAvailableFont();

    private final JFrame frame;
    private final JTextField myanmarTF;

    private static volatile MyanmarTextTest mtt;

    public static void main(String[] args) throws Exception {
        if (FONT_CANDIDATES == null) {
            System.err.println("Unsupported OS: exiting");
            throw new SkippedException("Unsupported OS: " + OS_NAME);
        }
        if (FONT_NAME == null) {
            String fontList = String.join(", ", FONT_CANDIDATES);
            System.err.println("Required font is not installed for OS: " + OS_NAME);
            System.err.println("Checked fonts: " + fontList);
            throw new SkippedException("Required fonts not installed for OS: "
                    + OS_NAME + ". Checked fonts: " + fontList);
        }

        try {
            SwingUtilities.invokeAndWait(MyanmarTextTest::createUI);
            SwingUtilities.invokeAndWait(mtt::checkPositions);
        } finally {
            SwingUtilities.invokeAndWait(mtt::dispose);
        }
    }

    private static void createUI() {
        mtt = new MyanmarTextTest();
        mtt.show();
    }

    private MyanmarTextTest() {
        frame = new JFrame("Myanmar Text");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        myanmarTF = new JTextField(TEXT);
        myanmarTF.setFont(new Font(FONT_NAME, Font.PLAIN, 40));

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.add(myanmarTF);
        main.setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));

        frame.getContentPane().add(main);
    }

    private void show() {
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private void dispose() {
        frame.dispose();
    }

    private void checkPositions() {
        final TextUI ui = myanmarTF.getUI();
        final Position.Bias[] biasRet = new Position.Bias[1];
        try {
            if (2 != ui.getNextVisualPositionFrom(myanmarTF, 0,
                    Position.Bias.Forward, SwingConstants.EAST, biasRet)) {
                throw new RuntimeException("For 0, next position should be 2");
            }
            if (2 != ui.getNextVisualPositionFrom(myanmarTF, 1,
                    Position.Bias.Forward, SwingConstants.EAST, biasRet)) {
                throw new RuntimeException("For 1, next position should be 2");
            }
            if (0 != ui.getNextVisualPositionFrom(myanmarTF, 2,
                    Position.Bias.Forward, SwingConstants.WEST, biasRet)) {
                throw new RuntimeException("For 2, prev position should be 0");
            }
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    private static String[] selectFontCandidates() {
        if (OS_NAME.contains("windows")) {
            return FONTS_WINDOWS;
        } else if (OS_NAME.contains("linux")) {
            return FONTS_LINUX;
        } else if (OS_NAME.contains("mac")) {
            return FONTS_MACOS;
        } else {
            return null;
        }
    }

    private static String selectAvailableFont() {
        if (FONT_CANDIDATES == null) {
            return null;
        }
        String[] installedFonts = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();

        for (String font : FONT_CANDIDATES) {
            if (Arrays.asList(installedFonts).contains(font)) {
                System.out.println("Using font: " + font);
                return font;
            }
        }
        return null;
    }
}
