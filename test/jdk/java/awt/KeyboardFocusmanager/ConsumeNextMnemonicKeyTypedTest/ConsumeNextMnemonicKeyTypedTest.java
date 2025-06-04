/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @key headful
  @bug        6346690
  @summary    Tests that key_typed is consumed after mnemonic key_pressed is handled for a menu item.
  @library    /test/lib
  @build      jdk.test.lib.Platform
  @run        main ConsumeNextMnemonicKeyTypedTest
*/

import jdk.test.lib.Platform;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public class ConsumeNextMnemonicKeyTypedTest {
    static Robot robot;
    static JFrame frame;
    static JTextField text;
    static JMenuBar bar;
    static JMenu menu;
    static JMenuItem item;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(50);
        try {
            SwingUtilities.invokeAndWait(ConsumeNextMnemonicKeyTypedTest::init);

            robot.waitForIdle();
            robot.delay(500);

            test();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static void init() {
        frame = new JFrame("Test Frame");
        text = new JTextField();
        bar = new JMenuBar();
        menu = new JMenu("Menu");
        item = new JMenuItem("item");

        menu.setMnemonic('f');
        item.setMnemonic('i');
        menu.add(item);
        bar.add(menu);

        frame.add(text);
        frame.setJMenuBar(bar);
        frame.pack();

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    static void test() {

        robot.waitForIdle();

        if (!text.isFocusOwner()) {
            robot.mouseMove(text.getLocationOnScreen().x + 5, text.getLocationOnScreen().y + 5);
            robot.delay(100);
            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.delay(100);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);

            int iter = 10;
            while (!text.isFocusOwner() && iter-- > 0) {
                robot.delay(200);
            }
            if (iter <= 0) {
                System.out.println("Test: text field couldn't be focused!");
                return;
            }
        }

        robot.keyPress(KeyEvent.VK_A);
        robot.delay(100);
        robot.keyRelease(KeyEvent.VK_A);

        robot.waitForIdle();

        String charA = text.getText();
        System.err.println("Test: character typed with VK_A: " + charA);

        robot.keyPress(KeyEvent.VK_BACK_SPACE);
        robot.delay(100);
        robot.keyRelease(KeyEvent.VK_BACK_SPACE);

        robot.waitForIdle();

        if (Platform.isOSX()) {
            robot.keyPress(KeyEvent.VK_CONTROL);
        }
        robot.keyPress(KeyEvent.VK_ALT);
        robot.keyPress(KeyEvent.VK_F);
        robot.delay(100);
        robot.keyRelease(KeyEvent.VK_F);
        robot.keyRelease(KeyEvent.VK_ALT);
        if (Platform.isOSX()) {
            robot.keyRelease(KeyEvent.VK_CONTROL);
        }

        robot.waitForIdle();

        String string = text.getText();

        robot.keyPress(KeyEvent.VK_I);
        robot.delay(100);
        robot.keyRelease(KeyEvent.VK_I);

        robot.waitForIdle();

        System.out.println("Test: character typed after mnemonic key press: " + text.getText());

        if (!text.getText().equals(string)) {
            throw new RuntimeException("Test failed!");
        }

        robot.keyPress(KeyEvent.VK_A);
        robot.delay(100);
        robot.keyRelease(KeyEvent.VK_A);

        robot.waitForIdle();

        System.err.println("Test: character typed with VK_A: " + text.getText());

        if (!charA.equals(text.getText())) {
            throw new RuntimeException("Test failed!");
        }

        System.out.println("Test passed.");
    }
}
