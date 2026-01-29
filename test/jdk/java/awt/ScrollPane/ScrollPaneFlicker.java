/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4073822
 * @summary ScrollPane repaints entire window when scrolling fast
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ScrollPaneFlicker
 */

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.ScrollPane;
import java.awt.Scrollbar;
import java.awt.TextArea;
import java.awt.TextField;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class ScrollPaneFlicker {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                When scrolling a ScrollPane fast(i.e. holding the down/up arrow
                down for a while), the ScrollPane would inexplicably refresh
                the entire window.

                1. Select a type of ScrollPane content from the content menu.
                2. Scroll the content using the up/down/left/right arrows on
                   the scroll bar. Try scrolling the entire content area using
                   the scroll arrows-- from top to bottom and left to right.
                3. Verify that the entire pane does not refresh when scrolling
                   - only the newly exposed areas should be repainting.
                4. Repeat for all content types.
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(ScrollPaneFlicker::initialize)
                .build()
                .awaitAndCheck();
    }

    static Frame initialize() {
        return new FlickerFrame();
    }
}

class FlickerFrame extends Frame {
    ScrollPane pane;

    public FlickerFrame() {
        super("ScrollPane Flicker Test");
        TextPanel textPanel = new TextPanel();
        GradientPanel gradientPanel = new GradientPanel();
        ComponentPanel componentPanel = new ComponentPanel();
        SwingPanel swingPanel = new SwingPanel();
        MenuBar menubar = new MenuBar();
        Menu testMenu = new Menu("Test Options");

        pane = new ScrollPane();
        pane.getHAdjustable().setUnitIncrement(8);
        pane.getVAdjustable().setUnitIncrement(16);
        pane.add(textPanel);
        add(pane);

        testMenu.add(makeContentItem("Text Lines", textPanel));
        testMenu.add(makeContentItem("Gradient Fill", gradientPanel));
        testMenu.add(makeContentItem("AWT Components", componentPanel));
        testMenu.add(makeContentItem("Swing Components", swingPanel));
        menubar.add(testMenu);

        setMenuBar(menubar);
        setSize(400, 300);
    }

    public MenuItem makeContentItem(String title, final Component content) {
        MenuItem menuItem = new MenuItem(title);
        menuItem.addActionListener(
                ev -> {
                    pane.add(content);
                    pane.validate();
                }
        );
        return menuItem;
    }
}

class GradientPanel extends Canvas {
    public void paint(Graphics g) {
        // just paint something that'll take a while
        int x, y;
        int width = getSize().width;
        int height = getSize().height;
        int step = 8;

        for (x = 0; x < width; x += step) {
            for (y = 0; y < height; y += step) {
                int red = (255 * y) / height;
                int green = (255 * x * y) / (width * height);
                int blue = (255 * x) / width;
                Rectangle bounds = g.getClipBounds();
                Rectangle fbounds = new Rectangle(x, y, x + step, y + step);
                if (bounds.intersects(fbounds)) {
                    Color color = new Color(red, green, blue);
                    g.setColor(color);
                    g.fillRect(x, y, x + step, y + step);
                }
            }
        }
    }

    public Dimension getPreferredSize() {
        return new Dimension(200, 1000);
    }
}

class TextPanel extends Canvas {
    public void paint(Graphics g) {
        Font font = new Font("SanSerif", Font.ITALIC, 12);

        g.setFont(font);
        // just paint something that'll take a while
        int x, y;
        int width = getWidth();
        int height = getHeight();
        int step = 16;

        for (x = y = 0; y < height; y += step) {
            Rectangle bounds = g.getClipBounds();
            Rectangle tbounds = new Rectangle(x, y - 16, x + width, y);
            if (bounds.intersects(tbounds)) {
                g.drawString(y + " : The quick brown fox jumps over the lazy dog. " +
                        "The rain in Spain falls mainly on the plain.", x, y);
            }
        }
    }

    public Dimension getPreferredSize() {
        return new Dimension(640, 1000);
    }
}

class ComponentPanel extends Panel {
    ComponentPanel() {
        add(new Label("Label"));
        add(new Button("Button"));
        add(new Checkbox("Checkbox"));
        Choice c = new Choice();
        c.add("choice");
        java.awt.List l = new java.awt.List();
        l.add("list");
        add(new Scrollbar());
        add(new TextField("TextField"));
        add(new TextArea("TextArea"));
        add(new Panel());
        add(new Canvas());
    }
}

class SwingPanel extends JPanel {
    SwingPanel() {
        add(new JLabel("JLabel"));
        add(new JButton("JButton"));
        add(new JCheckBox("JCheckBox"));
        JComboBox c = new JComboBox();
        JList l = new JList();
        add(new JScrollBar());
        add(new JTextField("This is a JTextField with some text in it to make it longer."));
        add(new JTextArea("This is a JTextArea with some text in it to make it longer."));
    }
}
