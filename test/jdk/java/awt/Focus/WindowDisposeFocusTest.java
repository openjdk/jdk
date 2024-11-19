/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4257071 4228379
 * @summary Ensures that focus lost is delivered to a lightweight component
            in a disposed window
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual WindowDisposeFocusTest
*/

import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class WindowDisposeFocusTest {

    private static final String INSTRUCTIONS = """
         Click on "Second"
         Click on close box
         When dialog pops up, "Second" should no longer have focus.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("WindowDisposeFocusTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(WindowDisposeFocusTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static Window createTestUI() {
        return JFCFocusBug2.test(new String[]{});
    }
}

class JFCFocusBug2 extends JPanel {

    static public Window test(String[] args) {
        final JFrame frame = new JFrame("WindowDisposeFrame");
        frame.setSize(100, 100);
        frame.setVisible(true);

        final JFCFocusBug2 bug = new JFCFocusBug2();
        final JDialog dialog = new JDialog(frame, false);
        dialog.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                dialog.dispose();
                JDialog dialog2 = new JDialog(frame, false);
                dialog2.setContentPane(bug);
                dialog2.pack();
                dialog2.setVisible(true);
            }
        });
        dialog.setContentPane(bug);
        dialog.pack();
        dialog.setVisible(true);
        return frame;
    }

    public JFCFocusBug2() {
        _first = new JButton("First");
        _second = new JButton("Second");
        add(_first);
        add(_second);
    }

    private JButton _first;
    private JButton _second;
}
