/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4045781
 * @summary Exposed/damaged canvases don't always update correctly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PaintGlitchTest
 */

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.Scrollbar;
import java.awt.TextArea;
import java.awt.TextField;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class PaintGlitchTest extends Frame {
    static final String INSTRUCTIONS = """
               1. Click on the 'Painting Glitch Test' window  and select from
                  its menu a content type (text, gradient, fill,
                  AWT components, Swing components etc.).
               2. Select 'Modal Dialog...' to create a dialog.
               3. Drag the dialog over the content very fast
                  for 10 seconds or so - make sure you
                  keep dragging while the content is painting.
               4. Verify that the area exposed by the drag (the damaged regions)
                  always update properly no white areas or bits of the dialog
                  should be left after the drag operation is
                  completed (i.e. after you let go of the mouse).
               5. Repeat for all other content types.
               6. If for any content type the damaged dialog is not properly
                  repainted press Fail. Otherwise press Pass.
            """;

    public PaintGlitchTest() {
        super("Painting Glitch Test");

        TextPanel textPanel = new TextPanel();
        GradientPanel gradientPanel = new GradientPanel();
        ComponentPanel componentPanel = new ComponentPanel();
        SwingPanel swingPanel = new SwingPanel();

        add(textPanel);

        MenuBar menubar = new MenuBar();
        Menu testMenu = new Menu("Test");
        testMenu.add(makeContentItem("Text Lines", textPanel) );
        testMenu.add(makeContentItem("Gradient Fill", gradientPanel) );
        testMenu.add(makeContentItem("AWT Components", componentPanel) );
        testMenu.add(makeContentItem("Swing Components", swingPanel) );
        testMenu.addSeparator();
        MenuItem dialogItem = new MenuItem("Modal Dialog...");
        dialogItem.addActionListener(ev -> new ObscuringDialog(PaintGlitchTest.this).show());
        testMenu.add(dialogItem);
        testMenu.addSeparator();
        menubar.add(testMenu);

        setMenuBar(menubar);
        setSize(400,300);
    }

    public static void main(String args[]) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Repaint Glitch")
                .testUI(PaintGlitchTest::new)
                .instructions(INSTRUCTIONS)
                .columns(40)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    public MenuItem makeContentItem(String title, final Component content) {
        MenuItem menuItem = new MenuItem(title);
        menuItem.addActionListener(
                ev -> {
                    remove(0);
                    add(content);
                    validate();
                }
        );

        return menuItem;
    }
}

class GradientPanel extends Canvas {
    public void paint(Graphics g) {
        long ms = System.currentTimeMillis();
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

                Color   color = new Color(red, green, blue);
                g.setColor(color);
                g.fillRect(x, y, step, step);
            }
        }
        long time = System.currentTimeMillis() - ms;
        PassFailJFrame.log("GradientPanel paint took " + time + " ms");
    }

    public Dimension getPreferredSize() {
        return new Dimension(200,1000);
    }
}

class TextPanel extends Canvas {
    public void paint(Graphics g) {
        long ms = System.currentTimeMillis();
        Font font = new Font("SanSerif", Font.ITALIC, 12);

        g.setFont(font);
        // just paint something that'll take a while
        int x, y;
        int height = getHeight();
        int step = 16;

        for (x = y = 0; y < height; y += step) {
            g.drawString(y + " : The quick brown fox jumps over the lazy dog. " +
                    "The rain in Spain falls mainly on the plain.", x, y);
        }
        long time = System.currentTimeMillis() - ms;
        PassFailJFrame.log("TextPanel paint took " + time + " ms");
    }

    public Dimension getPreferredSize() {
        return new Dimension(640,1000);
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

class ObscuringDialog extends Dialog {
    ObscuringDialog(Frame f) {
        super(f, "Obscuring Dialog");
        Button ok = new Button("OK, go away");
        ok.addActionListener(ev -> dispose());
        add(ok);
        pack();
    }
}
