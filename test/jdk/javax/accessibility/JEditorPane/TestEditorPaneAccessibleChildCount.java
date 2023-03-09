/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8273986
 * @key headful
 * @requires (os.family == "windows")
 * @summary Verifies if accessible child count for JEditorPane HTML demo
 * returns correct child count.
 * @run main TestEditorPaneAccessibleChildCount
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.Point;
import java.awt.Robot;
import java.io.IOException;
import java.net.URL;
import javax.accessibility.AccessibleContext;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.html.HTMLEditorKit;

public class TestEditorPaneAccessibleChildCount implements ActionListener {
    private static JEditorPane jep;
    private static AccessibleContext ac;
    private static JButton ChangePageBtn;
    private static URL url;
    private static JFrame frame;

    public TestEditorPaneAccessibleChildCount() {
        createAndShowUI();
    }

    public void createAndShowUI() {
        frame = new JFrame("EditorPaneTester Tester");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        jep = new JEditorPane();
        jep.setEditable(false);
        jep.setEditorKit(new HTMLEditorKit());
        url= TestEditorPaneAccessibleChildCount.class.getResource("index.html");
        JScrollPane jScrollPane = new JScrollPane(jep);
        jScrollPane.setPreferredSize(new Dimension(540,400));
        panel.add(jScrollPane);
        frame.getContentPane().add(panel, BorderLayout.CENTER);

        ChangePageBtn = new JButton("Change Page");
        ChangePageBtn.addActionListener(this);
        frame.getContentPane().add(ChangePageBtn, BorderLayout.SOUTH);

        try {
            jep.setPage(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
        frame.setSize(560, 450);
        frame.setLocationRelativeTo(null);
        frame.pack();
        frame.setVisible(true);
    }

    public void actionPerformed(ActionEvent e) {
        url= TestEditorPaneAccessibleChildCount.class.getResource("title.html");
        try {
            jep.setPage(url);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(() -> {
                new TestEditorPaneAccessibleChildCount();
            });
            robot.waitForIdle();
            robot.delay(1000);
            ac = jep.getAccessibleContext();
            int childCount1 = ac.getAccessibleChildrenCount();

            Point ChangePageBtnLocation = ChangePageBtn.getLocationOnScreen();
            int width = ChangePageBtn.getWidth();
            int height = ChangePageBtn.getHeight();
            robot.mouseMove(ChangePageBtnLocation.x + width/2,
                            ChangePageBtnLocation.y + height/2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(100);

            int childCount2 = ac.getAccessibleChildrenCount();

            if ((childCount1 != childCount2) &&
                    (childCount1 != 0 && childCount2 != 0)) {
                System.out.println("passed");
            } else {
                System.out.println("Index page accessible children count is: "+
                        childCount1);
                System.out.println("Title page accessible children count is: "+
                        childCount2);
                throw new RuntimeException("getAccessibleChildrenCount returned"+
                        " wrong child count");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
