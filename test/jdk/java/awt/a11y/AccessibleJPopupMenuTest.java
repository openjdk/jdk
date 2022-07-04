/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, JetBrains s.r.o.. All rights reserved.
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
 * @summary Test implementation of NSAccessibilityMenu and NSAccessibilityMenuItem roles peer
 * @author Artem.Semenov@jetbrains.com
 * @run main/manual AccessibleJPopupMenuTest
 * @requires (os.family == "mac")
 */

import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.CountDownLatch;

public class AccessibleJPopupMenuTest extends AccessibleComponentTest {

    @Override
    public CountDownLatch createCountDownLatch() {
        return new CountDownLatch(1);
    }


    private static JPopupMenu createPopup() {
        JPopupMenu popup = new JPopupMenu("MENU");
        popup.add("One");
        popup.add("Two");
        popup.add("Three");
        popup.addSeparator();
        JMenu menu = new JMenu("For submenu");
        menu.add("subOne");
        menu.add("subTwo");
        menu.add("subThree");
        popup.add(menu);
        return popup;
    }

    public void createTest() {
        INSTRUCTIONS = "INSTRUCTIONS:\n"
                + "Check a11y of JPopupMenu.\n\n"
                + "Turn screen reader on, and Tab to the show button and press space.\n"
                + "Press the up and down arrow buttons to move through the menu, and open submenu.\n\n"
                + "If you can hear popup menu items tab further and press PASS, otherwise press FAIL.\n";

        JPanel frame = new JPanel();

        JButton button = new JButton("show");
        button.setPreferredSize(new Dimension(100, 35));

        button.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                createPopup().show(button, button.getX(), button.getY());
            }
        });

        frame.setLayout(new FlowLayout());
        frame.add(button);
        exceptionString = "Accessible JPopupMenu test failed!";
        super.createUI(frame, "Accessible JPopupMenu test");
    }

    public static void main(String[] args) throws Exception {
        AccessibleJPopupMenuTest a11yTest = new AccessibleJPopupMenuTest();

        CountDownLatch countDownLatch = a11yTest.createCountDownLatch();
        SwingUtilities.invokeLater(a11yTest::createTest);
        countDownLatch.await();

        if (!testResult) {
            throw new RuntimeException(a11yTest.exceptionString);
        }
    }
}
