/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import static javax.swing.UIManager.getInstalledLookAndFeels;

/*
 * @test
 * @key headful
 * @bug 4666101
 * @summary Verifies that in a JEditorPane, the down arrow is honoured after you
            add text on a line preceding a blank line.
 * @run main JEditorPaneNavigationTest
 */
public class JEditorPaneNavigationTest {

    private static volatile int caretPos;
    private static JEditorPane jep;
    private static JFrame frame;
    private static Robot robot;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(100);

        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
                                  .map(LookAndFeelInfo::getClassName)
                                  .collect(Collectors.toList());
        for (final String laf : lafs) {
            try {
                AtomicBoolean lafSetSuccess = new AtomicBoolean(false);
                SwingUtilities.invokeAndWait(() -> {
                    lafSetSuccess.set(setLookAndFeel(laf));
                    if (lafSetSuccess.get()) {
                        createUI();
                    }
                });
                if (!lafSetSuccess.get()) {
                    continue;
                }
                robot.waitForIdle();

                AtomicReference<Point> pt = new AtomicReference<>();
                SwingUtilities.invokeAndWait(() -> pt.set(jep.getLocationOnScreen()));
                caretPos = 0;
                final Point jEditorLoc = pt.get();

                // Click on JEditorPane
                robot.mouseMove(jEditorLoc.x + 50, jEditorLoc.y + 50);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                keyType(KeyEvent.VK_ENTER);
                keyType(KeyEvent.VK_ENTER);

                typeSomeText();

                keyType(KeyEvent.VK_UP);
                keyType(KeyEvent.VK_UP);

                typeSomeText();

                keyType(KeyEvent.VK_DOWN);

                System.out.println(" test1 caret pos = " + caretPos);

                // Check whether the caret position is at the expected value 5
                if (caretPos != 5) {
                    captureScreen();
                    throw new RuntimeException("Test Failed in " + laf
                            + " expected initial caret position is 5, but actual is " + caretPos);
                }

                keyType(KeyEvent.VK_DOWN);

                System.out.println(" test2 caret pos = " + caretPos);

                // Check whether the caret position is at the expected value 10
                if (caretPos != 10) {
                    captureScreen();
                    throw new RuntimeException("Test Failed in " + laf
                            + " expected final caret position is 10, but actual is " + caretPos);
                }

                System.out.println("Test Passed in " + laf);

            } finally {
                SwingUtilities.invokeAndWait(JEditorPaneNavigationTest::disposeFrame);
            }
        }
    }

    private static void captureScreen() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        try {
            ImageIO.write(
                    robot.createScreenCapture(new Rectangle(0, 0, screenSize.width, screenSize.height)),
                    "png",
                    new File("JEditorPaneNavigationTest.png")
            );
        } catch (IOException ignore) {
        }
    }

    private static void typeSomeText() {
        keyType(KeyEvent.VK_T);
        keyType(KeyEvent.VK_E);
        keyType(KeyEvent.VK_X);
        keyType(KeyEvent.VK_T);
    }

    private static void keyType(int keyCode) {
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
    }


    private static void createUI() {
        frame = new JFrame();
        jep = new JEditorPane();
        jep.setPreferredSize(new Dimension(100, 100));
        jep.addCaretListener(e -> caretPos = jep.getCaretPosition());
        jep.setEditable(true);
        JPanel panel = new JPanel();
        panel.add(jep);
        frame.add(panel);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    private static boolean setLookAndFeel(String lafName) {
        try {
            UIManager.setLookAndFeel(lafName);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Ignoring Unsupported laf : " + lafName);
            return false;
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

}
