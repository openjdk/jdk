/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4077709 4153989
 * @summary Lightweight component font settable test
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual LightweightFontTest
 */

import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;


public class LightweightFontTest {
    static Font desiredFont = null;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                [ There are 7 steps to this test ]
                1. The 2 bordered labels (Emacs vs. vi) should be in a LARGE font
                   (approximately 1/2 inch tall)
                2. The labels should not overlap.
                3. Each button should be large enough to contain the entire label.
                4. The labels should have red backgrounds
                5. The text in the left label should be blue and the right yellow
                6. Resize the window to make it much smaller and larger
                7. The buttons should never overlap, and they should be large
                   enough to contain the entire label.
                   (although the button may disappear if there is not enough
                   room in the window)"
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(LightweightFontTest::createUI)
                .logArea(5)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame f = new Frame("Lightweight Font Test");
        f.setLayout(new FlowLayout());

        desiredFont = new Font(Font.DIALOG, Font.PLAIN, 36);
        Component component;
        component = new BorderedLabel("Emacs or vi?");
        component.setFont(desiredFont);
        component.setBackground(Color.red);
        component.setForeground(Color.blue);
        f.add(component);
        component = new BorderedLabel("Vi or Emacs???");
        component.setFont(desiredFont);
        component.setBackground(Color.red);
        component.setForeground(Color.yellow);
        f.add(component);
        f.pack();
        return f;
    }
}

/**
 * Lightweight component
 */
class BorderedLabel extends Component {
    boolean superIsButton;
    String labelString;

    BorderedLabel(String labelString) {
        this.labelString = labelString;

        Component thisComponent = this;
        superIsButton = (thisComponent instanceof Button);
        if(superIsButton) {
            ((Button)thisComponent).setLabel(labelString);
        }
    }

    public Dimension getMinimumSize() {
        Dimension minSize = new Dimension();

        if (superIsButton) {
            minSize = super.getMinimumSize();
        } else {

            Graphics g = getGraphics();
            verifyFont(g);
            FontMetrics metrics = g.getFontMetrics();

            minSize.width = metrics.stringWidth(labelString) + 14;
            minSize.height = metrics.getMaxAscent() + metrics.getMaxDescent() + 9;

            g.dispose();
        }
        return minSize;
    }

    public Dimension getPreferredSize() {
        Dimension prefSize = new Dimension();
        if (superIsButton) {
            prefSize = super.getPreferredSize();
        } else {
            prefSize = getMinimumSize();
        }
        return prefSize;
    }

    public void paint(Graphics g) {
        verifyFont(g);
        super.paint(g);
        if (superIsButton) {
            return;
        }
        Dimension size = getSize();
        Color oldColor = g.getColor();

        // draw border
        g.setColor(getBackground());
        g.fill3DRect(0, 0, size.width, size.height, false);
        g.fill3DRect(3, 3, size.width - 6, size.height - 6, true);

        // draw text
        FontMetrics metrics = g.getFontMetrics();
        int centerX = size.width / 2;
        int centerY = size.height / 2;
        int textX = centerX - (metrics.stringWidth(labelString) / 2);
        int textY = centerY + ((metrics.getMaxAscent()
                + metrics.getMaxDescent()) / 2);
        g.setColor(getForeground());
        g.drawString(labelString, textX, textY);

        g.setColor(oldColor);
    }

    /**
     * Verifies that the font is correct and prints a warning
     * message and/or throws a RuntimeException if it is not.
     */
    private void verifyFont(Graphics g) {
        Font desiredFont = LightweightFontTest.desiredFont;
        Font actualFont = g.getFont();
        if (!actualFont.equals(desiredFont)) {
            PassFailJFrame.log("AWT BUG: FONT INFORMATION LOST!");
            PassFailJFrame.log("         Desired font: " + desiredFont);
            PassFailJFrame.log("          Actual font: " + actualFont);
            PassFailJFrame.forceFail();
        }
    }
}
