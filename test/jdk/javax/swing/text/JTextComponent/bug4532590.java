/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4532590
 * @summary Tests that selection is not painted when highlighter is set to null
 * @run main bug4532590
 */

import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.SwingUtilities;

public class bug4532590 {

    static final int SELECTION_START = 5;
    static final int SELECTION_END   = 10;
    static final String TEXT         = "Typein the missing word.";

    static final Color TEXT_FG       = Color.BLACK;
    static final Color TEXT_BG       = Color.WHITE;
    static final Color SELECTION_FG  = Color.RED;
    static final Color SELECTION_BG  = Color.YELLOW;

    JTextComponent[] comps;
    JTextPane pane;
    JTextArea area, warea;

    int selFG = SELECTION_FG.getRGB();
    int selBG = SELECTION_BG.getRGB();

    public bug4532590() throws BadLocationException {
        // text pane
        pane = new JTextPane();
        pane.setContentType("text/plain");

        // populate the pane
        DefaultStyledDocument dsd = new DefaultStyledDocument();
        dsd.insertString(0, "\n" + TEXT + "\n\n", new SimpleAttributeSet());
        pane.setDocument(dsd);

        // text area
        area = new JTextArea();
        area.setText("\n" + TEXT);

        // wrapped text area
        warea = new JTextArea();
        warea.setText("\n" + TEXT);

        comps = new JTextComponent[3];
        comps[0] = pane;
        comps[1] = area;
        comps[2] = warea;
    }

    void initComp(JTextComponent comp) {
        comp.setEditable(false);
        comp.setForeground(TEXT_FG);
        comp.setBackground(TEXT_BG);
        comp.setSelectedTextColor(SELECTION_FG);
        comp.setSelectionColor(SELECTION_BG);
        comp.setHighlighter(null);
        comp.setSize(comp.getPreferredSize());

        comp.setSelectionStart(SELECTION_START);
        comp.setSelectionEnd(SELECTION_END);
        comp.getCaret().setSelectionVisible(true);
    }

    /**
     * Paint given component on an offscreen buffer
     */
    BufferedImage drawComp(JTextComponent comp) {
        int w = comp.getWidth();
        int h = comp.getHeight();

        BufferedImage img =
            new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        comp.paint(img.createGraphics());
        return img;
    }

    void testComp(JTextComponent comp) {
        initComp(comp);
        BufferedImage img = drawComp(comp);
        int w = img.getWidth(null);
        int h = img.getHeight(null);

        // scan the image
        // there should be no SELECTION_FG or SELECTION_BG pixels
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                int rgb = img.getRGB(i, j);
                if (rgb == selFG) {
                    throw new RuntimeException(
                                  "Failed: selection foreground painted");
                } else if (rgb == selBG) {
                    throw new RuntimeException(
                                  "Failed: selection background painted");
                }
            }
        }
    }

    void test() {
        for (int i = 0; i < comps.length; i++) {
            testComp(comps[i]);
        }
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                new bug4532590().test();
            } catch (BadLocationException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
