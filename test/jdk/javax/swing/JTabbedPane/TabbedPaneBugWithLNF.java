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

/*
 * @test
 * @bug 4459231
 * @summary Verifies if JTabbedPane(with Scrollable tablayout) changes focus
 *          on change in LookAndFeel
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TabbedPaneBugWithLNF
 */

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TabbedPaneBugWithLNF {

    private static String LNF = "com.sun.java.swing.plaf.motif.MotifLookAndFeel";
    private static JTabbedPane tabPane;
    private static JButton testBtn;

    static final String INSTRUCTIONS = """
        A JTabbedPane with 10 tabs will be shown.
        Scroll the tabs till the end, i.e., to "Testing Tab9".
        Select that tab.
        You will see the main tab JButton's text changed to 'Test Button9'.
        Click on it, which will change the lookandfeel.
        Verify if child tabs have scrolled back to starting child tab
        i.e., 'Testing Tab0', where as the selected tab is still 'Testing Tab9'.
        If it does, press Fail
        else if focus of the child Tab is still at "Testing Tab9" press Pass.""";

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        PassFailJFrame.builder()
                .title("TabbedPaneBugWithLNF Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(TabbedPaneBugWithLNF::createUI)
                .position(PassFailJFrame.Position.TOP_LEFT_CORNER)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame frame = new JFrame("TabbedPaneBugWithLNF");
        frame.setSize(640, 180);

        tabPane = new JTabbedPane(JTabbedPane.BOTTOM,JTabbedPane.SCROLL_TAB_LAYOUT);

        tabPane.addTab("Testing Tab0", testBtn = new JButton("Test Button0"));

        tabPane.addTab("Testing Tab1", testBtn = new JButton("Test Button1"));

        tabPane.addTab("Testing Tab2", testBtn = new JButton("Test Button2"));

        tabPane.addTab("Testing Tab3", testBtn = new JButton("Test Button3"));

        tabPane.addTab("Testing Tab4", testBtn = new JButton("Test Button4"));

        tabPane.addTab("Testing Tab5", testBtn = new JButton("Test Button5"));

        tabPane.addTab("Testing Tab6", testBtn = new JButton("Test Button6"));

        tabPane.addTab("Testing Tab7", testBtn = new JButton("Test Button7"));
        tabPane.addTab("Testing Tab8", testBtn = new JButton("Test Button8"));
        JButton myBtn = null;
        tabPane.addTab("Testing Tab9", myBtn = new JButton("Test Button9"));
        myBtn.addActionListener( new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                try {
                    UIManager.setLookAndFeel(LNF);
                    SwingUtilities.updateComponentTreeUI(frame);
                    System.out.println("tabPane.selectedIndex " + tabPane.getSelectedIndex());
                } catch (Exception exc) {
                    System.out.println("Error changing L&F : " + LNF);
                }
            }
        });
        frame.add(tabPane);
        return frame;
    }
}

