/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4519537 4522866
 * @summary Tests that text and components in paragraph views line up at their baselines.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual BaselineTest
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

import javax.swing.text.ComponentView;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

public class BaselineTest {

    static final String INSTRUCTIONS = """
        Test that components displayed in a JTextPane properly respect their vertical alignment.
        There are two text panes, stacked vertically with similar content except the bottom components are taller.
        The content consists of a leading and trailing text string, with pink coloured components between.
        The text string content means the strings 'Default Size Text' and 'Large Size Text'.
        Text content baseline is at the bottom of CAPITAL letters in the text.
        Each pink component has a string displaying its alignment setting in the range 0.0 to 1.0
        NB: The position of the strings "align = 0.0" etc is not important, it is the component position that matters.
        0.0 means it should be aligned with its top at the text content baseline,
        1.0 means it should be aligned with its bottom at the text content baseline.
        A value in between will be a proportional alignment, eg 0.5 is centered on the text content baseline
        If the content displays as described, the test PASSES.
    """;

    public static void main(String[] args) throws Exception {
       PassFailJFrame.builder()
            .title("BaselineTest Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(60)
            .rows(12)
            .testUI(BaselineTest::createUI)
            .positionTestUIBottomRowCentered()
            .build()
            .awaitAndCheck();
    }

    public static JFrame createUI() {
        JFrame frame = new JFrame("BaselineTest");
        frame.setLayout(new GridLayout(2, 1));

        JTextPane prefPane = new JTextPane();
        initJTextPane(prefPane);
        frame.add(new JScrollPane(prefPane));

        JTextPane variablePane = new JTextPane();
        variablePane.setEditorKit(new CustomEditorKit());
        initJTextPane(variablePane);
        frame.add(new JScrollPane(variablePane));
        frame.setSize(800, 400);
        return frame;
    }

    static void initJTextPane(JTextPane tp) {

        try {
            Document doc = tp.getDocument();

            doc.insertString(0, " Default Size Text ", null);
            tp.setCaretPosition(doc.getLength());
            tp.insertComponent(new PaintLabel(0.0f));
            tp.insertComponent(new PaintLabel(0.2f));
            tp.insertComponent(new PaintLabel(0.5f));
            tp.insertComponent(new PaintLabel(0.7f));
            tp.insertComponent(new PaintLabel(1.0f));
            SimpleAttributeSet set = new SimpleAttributeSet();
            StyleConstants.setFontSize(set, 20);
            tp.setCaretPosition(doc.getLength());
            doc.insertString(doc.getLength(), " Large Size Text ", set);
        } catch (BadLocationException ble) {
            throw new RuntimeException(ble);
        }
    }

    static class PaintLabel extends JLabel {

        private int pref = 40;
        private int min = pref - 30;
        private int max = pref + 30;

        public PaintLabel(float align) {

            setAlignmentY(align);
            String alignStr = String.valueOf(align);

            setText("align = " + alignStr);
            setOpaque(true);
            setBackground(Color.PINK);
        }

        public Dimension getMinimumSize() {
            return new Dimension(super.getMinimumSize().width, min);
        }

        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, pref);
        }

        public Dimension getMaximumSize() {
            return new Dimension(super.getMaximumSize().width, max);
        }

        public void paintComponent(Graphics g) {
            g.setColor(Color.PINK);
            g.fillRect(0, 0, getWidth(), getHeight());
            int y = (int)(getAlignmentY() * getHeight());
            g.setColor(Color.BLACK);
            g.drawLine(0, y, getWidth(), y);
            g.drawString(getText(), 0, 10);
        }
    }

    static class CustomComponentView extends ComponentView {

        public CustomComponentView(Element elem) {
            super(elem);
        }

        public int getResizeWeight(int axis) {
            return 1;
        }
}

    static class CustomEditorKit extends StyledEditorKit implements ViewFactory {

        public View create(Element elem) {
            if (StyleConstants.ComponentElementName.equals(elem.getName())) {
                return new CustomComponentView(elem);
            } else {
                return super.getViewFactory().create(elem);
            }
        }

        public ViewFactory getViewFactory() {
            return this;
        }
}

}
