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
import java.io.IOException;
import java.net.URL;
import javax.accessibility.AccessibleContext;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.html.HTMLEditorKit;

public class TestEditorPaneAccessibleChildCount {
    private static JEditorPane jep;
    private static AccessibleContext ac;
    private static JFrame frame;
    private static int childCount1 = 0;
    private static int childCount2 = 0;


    public TestEditorPaneAccessibleChildCount() {
        createAndShowUI();
    }

    public void createAndShowUI() {
        frame = new JFrame("JEditorPane A11Y Child Count Test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        jep = new JEditorPane();
        jep.setEditable(false);
        jep.setEditorKit(new HTMLEditorKit());
        URL url = TestEditorPaneAccessibleChildCount.class.
                getResource("test1.html");
        loadHtmlPage(url);
        JScrollPane jScrollPane = new JScrollPane(jep);
        jScrollPane.setPreferredSize(new Dimension(540,400));
        panel.add(jScrollPane);
        frame.getContentPane().add(panel, BorderLayout.CENTER);
        frame.setSize(560, 450);
        frame.setLocationRelativeTo(null);
        frame.pack();
        frame.setVisible(true);
    }

    public static void loadHtmlPage(URL url) {
        try {
            jep.setPage(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addDelay(int mSec) {
        try {
            Thread.sleep(mSec);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                new TestEditorPaneAccessibleChildCount();
            });

            addDelay(500);
            SwingUtilities.invokeAndWait(() -> {
                ac = jep.getAccessibleContext();
                childCount1 = ac.getAccessibleChildrenCount();
            });

            URL url = TestEditorPaneAccessibleChildCount.class.
                        getResource("test2.html");
            SwingUtilities.invokeAndWait(() -> {
                loadHtmlPage(url);
            });
            addDelay(500);

            SwingUtilities.invokeAndWait(() -> {
                childCount2 = ac.getAccessibleChildrenCount();
            });

            if ((childCount1 != childCount2) &&
                    (childCount1 != 0 && childCount2 != 0)) {
                System.out.println("passed");
            } else {
                System.out.println("Test1 html page accessible children" +
                        " count is: "+ childCount1);
                System.out.println("Test2 html page accessible children" +
                        " count is: "+ childCount2);
                throw new RuntimeException("getAccessibleChildrenCount" +
                        " returned wrong child count");
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
