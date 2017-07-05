/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4966112
 * @summary Some Composite components does not show the Context Popup.
 * @library ../../regtesthelpers
 * @build Util
 * @author Alexander Zuev
 * @run main bug4966112
 */
import javax.swing.*;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.PopupMenuEvent;
import java.awt.*;
import java.awt.event.*;
import sun.awt.SunToolkit;

public class bug4966112 {

    private static final int NO_MOUSE_BUTTON = -1;
    private static volatile boolean shown = false;
    private static volatile int popupButton = NO_MOUSE_BUTTON;
    private static volatile JButton testButton;
    private static volatile JSplitPane jsp;
    private static volatile JSpinner spin;
    private static volatile JFileChooser filec;
    private static int buttonMask;
    private static Robot robot;

    public static void main(String[] args) throws Exception {
        SunToolkit toolkit = (SunToolkit) Toolkit.getDefaultToolkit();
        robot = new Robot();
        robot.setAutoDelay(100);

        createAndShowButton();
        toolkit.realSync();

        setClickPoint(testButton);
        clickMouse(InputEvent.BUTTON1_MASK);
        clickMouse(InputEvent.BUTTON2_MASK);
        clickMouse(InputEvent.BUTTON3_MASK);

        toolkit.realSync();
        closeFrame();

        if (popupButton == NO_MOUSE_BUTTON) {
            System.out.println("Test can't identify the popup trigger button. Test skipped");
            return;
        }

        setButtonMask();

        // Test Split Pane
        createAndShowSplitPane();
        toolkit.realSync();

        clickMouse(jsp);
        toolkit.realSync();
        closeFrame();

        if (!shown) {
            throw new RuntimeException("Popup was not shown on splitpane");
        }

        // Test Spinner
        createAndShowSpinner();
        toolkit.realSync();

        clickMouse(spin);
        toolkit.realSync();
        closeFrame();

        if (!shown) {
            throw new RuntimeException("Popup was not shown on spinner");
        }

        // Test File Chooser
        createAndShowFileChooser();
        toolkit.realSync();

        clickMouse(filec);
        toolkit.realSync();

        Util.hitKeys(robot, KeyEvent.VK_ESCAPE);
        toolkit.realSync();

        Util.hitKeys(robot, KeyEvent.VK_ESCAPE);
        toolkit.realSync();
        closeFrame();

        if (!shown) {
            throw new RuntimeException("Popup was not shown on filechooser");
        }
    }

    private static void clickMouse(JComponent c) throws Exception {
        setClickPoint(c);
        clickMouse(buttonMask);
    }

    private static void clickMouse(int buttons) {
        robot.mousePress(buttons);
        robot.mouseRelease(buttons);
    }

    private static void setButtonMask() {
        switch (popupButton) {
            case MouseEvent.BUTTON1:
                buttonMask = InputEvent.BUTTON1_MASK;
                break;
            case MouseEvent.BUTTON2:
                buttonMask = InputEvent.BUTTON2_MASK;
                break;
            case MouseEvent.BUTTON3:
                buttonMask = InputEvent.BUTTON3_MASK;
                break;
        }
    }

    private static void setClickPoint(final JComponent c) throws Exception {
        final Point[] result = new Point[1];
        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                Point p = c.getLocationOnScreen();
                Dimension size = c.getSize();
                result[0] = new Point(p.x + size.width / 2, p.y + size.height / 2);
            }
        });

        robot.mouseMove(result[0].x, result[0].y);
    }

    private static JPopupMenu createJPopupMenu() {
        JPopupMenu jpm = new JPopupMenu();
        jpm.add("One");
        jpm.add("Two");
        jpm.add("Three");
        jpm.addPopupMenuListener(new PopupMenuListener() {

            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                shown = true;
            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });

        AutoClosable.INSTANCE.setPopup(jpm);
        return jpm;
    }

    private static void createAndShowButton() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                JFrame frame = new JFrame("Button Frame");
                frame.setLayout(new BorderLayout());
                testButton = new JButton("Popup Tester");

                testButton.addMouseListener(new MouseAdapter() {

                    void setPopupTrigger(MouseEvent e) {
                        if (e.isPopupTrigger()) {
                            popupButton = e.getButton();
                        }
                    }

                    public void mouseClicked(MouseEvent e) {
                        setPopupTrigger(e);
                    }

                    public void mousePressed(MouseEvent e) {
                        setPopupTrigger(e);
                    }

                    public void mouseReleased(MouseEvent e) {
                        setPopupTrigger(e);
                    }
                });

                frame.add(testButton, BorderLayout.CENTER);
                frame.pack();
                frame.setVisible(true);
                AutoClosable.INSTANCE.setFrame(frame);
            }
        });
    }

    private static void createAndShowSplitPane() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                JFrame frame = new JFrame("Test SplitPane");
                frame.setSize(250, 200);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setLayout(new BorderLayout());

                shown = false;
                jsp = new JSplitPane();
                jsp.setRightComponent(new JPanel());
                jsp.setLeftComponent(new JPanel());
                jsp.setComponentPopupMenu(createJPopupMenu());

                frame.add(jsp, BorderLayout.CENTER);

                jsp.setDividerLocation(150);

                frame.setLocation(400, 300);
                frame.setVisible(true);
                AutoClosable.INSTANCE.setFrame(frame);
            }
        });
    }

    private static void createAndShowSpinner() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                JFrame frame = new JFrame("JSpinner Test");
                frame.setLayout(new BorderLayout());
                frame.setSize(200, 100);
                shown = false;
                spin = new JSpinner();
                spin.setComponentPopupMenu(createJPopupMenu());
                frame.add(spin, BorderLayout.CENTER);
                frame.setVisible(true);
                AutoClosable.INSTANCE.setFrame(frame);
            }
        });
    }

    private static void createAndShowFileChooser() throws Exception {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                JFrame frame = new JFrame("FileChooser test dialog");
                frame.setSize(100, 100);

                shown = false;
                filec = new JFileChooser();
                filec.setComponentPopupMenu(createJPopupMenu());
                filec.showOpenDialog(frame);

                frame.setVisible(true);
                AutoClosable.INSTANCE.setFrame(frame);
            }
        });
    }

    private static void closeFrame() {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                AutoClosable.INSTANCE.close();
            }
        });
    }

    private static class AutoClosable {

        static final AutoClosable INSTANCE = new AutoClosable();
        private JFrame frame;
        private JPopupMenu popup;

        public void setFrame(JFrame frame) {
            this.frame = frame;
        }

        public void setPopup(JPopupMenu popup) {
            this.popup = popup;
        }

        public void close() {
            frame.dispose();
            if (popup != null) {
                popup.setVisible(false);
            }
        }
    }
}
