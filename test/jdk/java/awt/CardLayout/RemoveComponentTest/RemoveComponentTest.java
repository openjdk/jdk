/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4546123
 * @summary CardLayout becomes unusable after deleting an element
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RemoveComponentTest
 */

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JLabel;
import javax.swing.JPanel;

public class RemoveComponentTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                You should see a frame titled "Test Frame For
                RemoveComponentTest". Try to select a few different panels from
                the second menu. Make sure your last choice is the red panel.
                Then click close (in first menu). After that you should be able
                to select any panels except red one.
                If that is the case, the test passes. Otherwise, the test failed.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(RemoveComponentTest::createUI)
                .logArea(5)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        TestFrame frame = new TestFrame();
        frame.setSize(200, 200);
        return frame;
    }
}

class TestFrame extends Frame implements ActionListener {
    public Panel aPanel;
    public TestPanel pageRed;
    public TestPanel pageGreen;
    public TestPanel pageBlue;
    public String currentSelection = "";

    public MenuItem mi;
    public CardLayout theCardLayout;


    public TestFrame() {
        super("Test Frame For RemoveComponentTest");

        setBackground(Color.black);
        setLayout(new BorderLayout(5, 5));

        MenuBar mb = new MenuBar();

        Menu fileMenu = new Menu("File");
        Menu pageMenu = new Menu("Pages");

        mi = new MenuItem("Close");
        mi.addActionListener(this);
        fileMenu.add(mi);

        mi = new MenuItem("Red");
        mi.addActionListener(this);
        pageMenu.add(mi);

        mi = new MenuItem("Green");
        mi.addActionListener(this);
        pageMenu.add(mi);

        mi = new MenuItem("Blue");
        mi.addActionListener(this);
        pageMenu.add(mi);

        mb.add(fileMenu);
        mb.add(pageMenu);

        setMenuBar(mb);

        aPanel = new Panel();
        theCardLayout = new CardLayout();

        aPanel.setLayout(theCardLayout);

        pageRed = new TestPanel("PageRed", Color.red);
        pageGreen = new TestPanel("PageGreen", Color.green);
        pageBlue = new TestPanel("PageBlue", Color.blue);

        aPanel.add("PageRed", pageRed);
        aPanel.add("PageGreen", pageGreen);
        aPanel.add("PageBlue", pageBlue);

        add("Center", aPanel);
        setSize(getPreferredSize());
    }

    public Insets getInsets() {
        return new Insets(47, 9, 9, 9);
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals("Red")) {
            theCardLayout.show(aPanel, "PageRed");
            currentSelection = "PageRed";
        } else if (e.getActionCommand().equals("Green")) {
            theCardLayout.show(aPanel, "PageGreen");
        } else if (e.getActionCommand().equals("Blue")) {
            theCardLayout.show(aPanel, "PageBlue");
        } else if (e.getActionCommand().equals("Close")) {
            PassFailJFrame.log("Closing");

            if (currentSelection.equals("PageRed")) {
                PassFailJFrame.log("Remove page red");
                theCardLayout.removeLayoutComponent(pageRed);
            }
        }
    }
}

class TestPanel extends JPanel {
    private String pageName;

    TestPanel(String pageName, Color color) {
        setBackground(color);
        add(new JLabel(pageName));
    }
}
