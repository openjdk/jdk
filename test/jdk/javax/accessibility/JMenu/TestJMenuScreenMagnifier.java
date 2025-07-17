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
 * @bug 8283404
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "mac")
 * @summary Verifies if JMenu accessibility label magnifies using
 * screen magnifier a11y tool.
 * @run main/manual TestJMenuScreenMagnifier
 */

import java.lang.reflect.InvocationTargetException;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

public class TestJMenuScreenMagnifier {
    private static JFrame frame;
    private static final String INSTRUCTIONS =
            "1) Enable Screen magnifier on theMac \n\n" +
                "System Preference -> Accessibility -> Zoom -> " +
                "Select ( Enable Hover Text) \n\n" +
            "2) Move the mouse over the \"File\" or \"Edit\" menu by pressing  " +
                "\"cmd\" button.\n\n" +
            "3) If magnified label is visible, Press Pass else Fail.";

    public static void main(String[] args) throws InterruptedException,
             InvocationTargetException {
        PassFailJFrame passFailJFrame = new PassFailJFrame(
                "JMenu Screen Magnifier Test Instructions", INSTRUCTIONS, 5, 12, 40);
        try {
            SwingUtilities.invokeAndWait(
                    TestJMenuScreenMagnifier::createAndShowUI);
            passFailJFrame.awaitAndCheck();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
    private static void createAndShowUI() {
        frame = new JFrame("JMenu A11Y Screen Magnifier Test");

        JMenu file = new JMenu("File");
        file.getAccessibleContext().setAccessibleName("File Menu");

        JMenuItem open = new JMenuItem("Open");
        open.getAccessibleContext().setAccessibleName("Open MenuItem");

        JMenuItem quit = new JMenuItem("Quit");
        quit.getAccessibleContext().setAccessibleName("Quit MenuItem");

        file.add(open);
        file.add(quit);

        JMenu edit = new JMenu("Edit");
        edit.getAccessibleContext().setAccessibleName("Edit Menu");

        JMenuItem cut = new JMenuItem("Cut");
        cut.getAccessibleContext().setAccessibleName("Cut MenuItem");

        edit.add(cut);

        JMenuBar jMenuBar = new JMenuBar();

        jMenuBar.add(file);
        jMenuBar.add(edit);


        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame,
                PassFailJFrame.Position.HORIZONTAL);
        frame.setJMenuBar(jMenuBar);
        frame.setSize(300, 100);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
