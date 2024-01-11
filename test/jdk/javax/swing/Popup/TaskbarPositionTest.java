/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import jtreg.SkippedException;

/*
 * @test
 * @bug 4245587 4474813 4425878 4767478 8015599
 * @key headful
 * @summary Tests the location of the heavy weight popup portion of JComboBox,
 * JMenu and JPopupMenu.
 * The test uses Ctrl+Down Arrow which is a system shortcut on macOS,
 * disable it in system settings, otherwise the test will fail
 * @library ../regtesthelpers
 * @library /test/lib
 * @build Util
 * @build jtreg.SkippedException
 * @run main TaskbarPositionTest
 */
public class TaskbarPositionTest implements ActionListener {

    private static JFrame frame;
    private static JPopupMenu popupMenu;
    private static JPanel panel;

    private static JComboBox<String> combo1;
    private static JComboBox<String> combo2;

    private static JMenu menu1;
    private static JMenu menu2;
    private static JMenu submenu;

    private static Rectangle fullScreenBounds;
    // The usable desktop space: screen size - screen insets.
    private static Rectangle screenBounds;

    private static final String[] numData = {
        "One", "Two", "Three", "Four", "Five", "Six", "Seven"
    };
    private static final String[] dayData = {
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    };
    private static final char[] mnDayData = {
        'M', 'T', 'W', 'R', 'F', 'S', 'U'
    };

    public TaskbarPositionTest() {
        frame = new JFrame("Use CTRL-down to show a JPopupMenu");
        frame.setContentPane(panel = createContentPane());
        frame.setJMenuBar(createMenuBar());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // CTRL-down will show the popup.
        panel.getInputMap().put(KeyStroke.getKeyStroke(
                KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK), "OPEN_POPUP");
        panel.getActionMap().put("OPEN_POPUP", new PopupHandler());

        frame.pack();

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        fullScreenBounds = new Rectangle(new Point(), toolkit.getScreenSize());
        screenBounds = new Rectangle(new Point(), toolkit.getScreenSize());

        // Reduce the screen bounds by the insets.
        GraphicsConfiguration gc = frame.getGraphicsConfiguration();
        if (gc != null) {
            Insets screenInsets = toolkit.getScreenInsets(gc);
            screenBounds = gc.getBounds();
            screenBounds.width -= (screenInsets.left + screenInsets.right);
            screenBounds.height -= (screenInsets.top + screenInsets.bottom);
            screenBounds.x += screenInsets.left;
            screenBounds.y += screenInsets.top;
        }

        // Place the frame near the bottom.
        frame.setLocation(screenBounds.x,
                screenBounds.y + screenBounds.height - frame.getHeight());
        frame.setVisible(true);
    }

    private static class ComboPopupCheckListener implements PopupMenuListener {

        @Override
        public void popupMenuCanceled(PopupMenuEvent ev) {
        }

        @Override
        public void popupMenuWillBecomeVisible(PopupMenuEvent ev) {
        }

        @Override
        public void popupMenuWillBecomeInvisible(PopupMenuEvent ev) {
            JComboBox<?> combo = (JComboBox<?>) ev.getSource();
            Point comboLoc = combo.getLocationOnScreen();

            JPopupMenu popupMenu = (JPopupMenu) combo.getUI().getAccessibleChild(combo, 0);

            Point popupMenuLoc = popupMenu.getLocationOnScreen();
            Dimension popupSize = popupMenu.getSize();

            isPopupOnScreen(popupMenu, fullScreenBounds);

            if (comboLoc.x > 0) {
                // The frame is located at the bottom of the screen,
                // the combo popups should open upwards
                if (popupMenuLoc.y + popupSize.height < comboLoc.y) {
                    System.err.println("popup " + popupMenuLoc
                            + " combo " + comboLoc);
                    throw new RuntimeException("ComboBox popup should open upwards");
                }
            } else {
                // The frame has been moved to negative position away from
                // the bottom of the screen, the combo popup should
                // open downwards in this case
                if (popupMenuLoc.y + 1 < comboLoc.y) {
                    System.err.println("popup " + popupMenuLoc
                            + " combo " + comboLoc);
                    throw new RuntimeException("ComboBox popup should open downwards");
                }
            }
        }
    }

    private static class PopupHandler extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (!popupMenu.isVisible()) {
                popupMenu.show((Component) e.getSource(), 40, 40);
            }
            isPopupOnScreen(popupMenu, fullScreenBounds);
        }
    }

    private static class PopupListener extends MouseAdapter {

        private final JPopupMenu popup;

        public PopupListener(JPopupMenu popup) {
            this.popup = popup;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            maybeShowPopup(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            maybeShowPopup(e);
        }

        private void maybeShowPopup(MouseEvent e) {
            if (e.isPopupTrigger()) {
                popup.show(e.getComponent(), e.getX(), e.getY());
                isPopupOnScreen(popup, fullScreenBounds);
            }
        }
    }

    /**
     * Tests if the popup is on the screen.
     */
    private static void isPopupOnScreen(JPopupMenu popup, Rectangle checkBounds) {
        if (!popup.isVisible()) {
            throw new RuntimeException("Popup not visible");
        }
        Dimension dim = popup.getSize();
        Point pt = popup.getLocationOnScreen();
        Rectangle bounds = new Rectangle(pt, dim);

        if (!SwingUtilities.isRectangleContainingRectangle(checkBounds, bounds)) {
            throw new RuntimeException("Popup is outside of screen bounds "
                    + checkBounds + " / " + bounds);
        }
    }

    private static void isComboPopupOnScreen(JComboBox<?> comboBox) {
        if (!comboBox.isPopupVisible()) {
            throw new RuntimeException("ComboBox popup not visible");
        }
        JPopupMenu popupMenu = (JPopupMenu) comboBox.getUI().getAccessibleChild(comboBox, 0);
        isPopupOnScreen(popupMenu, screenBounds);
    }


    private JPanel createContentPane() {
        combo1 = new JComboBox<>(numData);
        combo1.addPopupMenuListener(new ComboPopupCheckListener());

        combo2 = new JComboBox<>(dayData);
        combo2.setEditable(true);
        combo2.addPopupMenuListener(new ComboPopupCheckListener());

        popupMenu = new JPopupMenu();
        for (int i = 0; i < dayData.length; i++) {
            JMenuItem item = popupMenu.add(new JMenuItem(dayData[i], mnDayData[i]));
            item.addActionListener(this);
        }

        JTextField field = new JTextField("CTRL+down for Popup");
        // CTRL-down will show the popup.
        field.getInputMap().put(KeyStroke.getKeyStroke(
                KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK), "OPEN_POPUP");
        field.getActionMap().put("OPEN_POPUP", new PopupHandler());

        JPanel panel = new JPanel();
        panel.add(combo1);
        panel.add(combo2);
        panel.setSize(300, 200);
        panel.addMouseListener(new PopupListener(popupMenu));
        panel.add(field);

        return panel;
    }

    private JMenuBar createMenuBar() {
        JMenuBar menubar = new JMenuBar();

        menu1 = new JMenu("1 - First Menu");
        menu1.setMnemonic('1');
        createSubMenu(menu1, "1 JMenuItem", 8, null);
        menubar.add(menu1);

        menu2 = new JMenu("2 - Second Menu");
        menu2.setMnemonic('2');
        createSubMenu(menu2, "2 JMenuItem", 4, null);
        menu2.add(new JSeparator());
        menubar.add(menu2);

        submenu = new JMenu("Sub Menu");
        submenu.setMnemonic('S');
        createSubMenu(submenu, "S JMenuItem", 4, this);
        menu2.add(submenu);

        return menubar;
    }

    private static void createSubMenu(JMenu menu, String prefix, int count, ActionListener action) {
        for (int i = 0; i < count; ++i) {
            JMenuItem menuitem = new JMenuItem(prefix + i);
            menu.add(menuitem);
            if (action != null) {
                menuitem.addActionListener(action);
            }
        }
    }


    public void actionPerformed(ActionEvent evt) {
        Object obj = evt.getSource();
        if (obj instanceof JMenuItem) {
            // put the focus on the non-editable combo.
            combo1.requestFocus();
        }
    }

    private static void hidePopup(Robot robot) {
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
    }

    public static void main(String[] args) throws Throwable {
        GraphicsDevice mainScreen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                                       .getDefaultScreenDevice();
        Rectangle mainScreenBounds = mainScreen.getDefaultConfiguration()
                                               .getBounds();
        GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                                      .getScreenDevices();
        for (GraphicsDevice screen : screens) {
            if (screen == mainScreen) {
                continue;
            }

            Rectangle bounds = screen.getDefaultConfiguration()
                                     .getBounds();
            if (bounds.x < 0) {
                // The test may fail if a screen have negative origin
                throw new SkippedException("Configurations with negative screen"
                                           + " origin are not supported");
            }
            if (bounds.y >= mainScreenBounds.height) {
                // The test may fail if there's a screen to bottom of the main monitor
                throw new SkippedException("Configurations with a screen beneath"
                                           + " the main one are not supported");
            }
        }

        try {
            // Use Robot to automate the test
            Robot robot = new Robot();
            robot.setAutoDelay(50);

            SwingUtilities.invokeAndWait(TaskbarPositionTest::new);

            robot.waitForIdle();
            robot.delay(1000);

            // 1 - menu
            Util.hitMnemonics(robot, KeyEvent.VK_1);

            robot.waitForIdle();
            SwingUtilities.invokeAndWait(() -> isPopupOnScreen(menu1.getPopupMenu(), screenBounds));

            // 2 menu with sub menu
            robot.keyPress(KeyEvent.VK_RIGHT);
            robot.keyRelease(KeyEvent.VK_RIGHT);
            // Open the submenu
            robot.keyPress(KeyEvent.VK_S);
            robot.keyRelease(KeyEvent.VK_S);

            robot.waitForIdle();
            SwingUtilities.invokeAndWait(() -> isPopupOnScreen(menu2.getPopupMenu(), screenBounds));
            SwingUtilities.invokeAndWait(() -> isPopupOnScreen(submenu.getPopupMenu(), screenBounds));

            // Hit Enter to perform the action of
            // a selected menu item in the submenu
            // which requests focus on combo1, non-editable combo box
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);

            robot.waitForIdle();

            // Focus should go to combo1
            // Open combo1 popup
            robot.keyPress(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_DOWN);

            robot.waitForIdle();
            SwingUtilities.invokeAndWait(() -> isComboPopupOnScreen(combo1));
            hidePopup(robot);

            // Move focus to combo2, editable combo box
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);

            robot.waitForIdle();

            // Open combo2 popup
            robot.keyPress(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_DOWN);

            robot.waitForIdle();
            SwingUtilities.invokeAndWait(() -> isComboPopupOnScreen(combo2));
            hidePopup(robot);

            // Move focus to the text field
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);

            robot.waitForIdle();

            // Open its popup
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_CONTROL);

            robot.waitForIdle();
            SwingUtilities.invokeAndWait(() -> isPopupOnScreen(popupMenu, fullScreenBounds));
            hidePopup(robot);

            // Popup from a mouse click
            SwingUtilities.invokeAndWait(() -> {
                Point pt = panel.getLocationOnScreen();
                pt.translate(4, 4);
                robot.mouseMove(pt.x, pt.y);
            });
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);

            // Ensure popupMenu is shown within screen bounds
            robot.waitForIdle();
            SwingUtilities.invokeAndWait(() -> isPopupOnScreen(popupMenu, fullScreenBounds));
            hidePopup(robot);

            robot.waitForIdle();
            SwingUtilities.invokeAndWait(() -> {
                frame.setLocation(-30, 100);
                combo1.requestFocus();
            });

            robot.waitForIdle();

            // Open combo1 popup again
            robot.keyPress(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_DOWN);

            robot.waitForIdle();
            SwingUtilities.invokeAndWait(() -> isComboPopupOnScreen(combo1));
            hidePopup(robot);

            robot.waitForIdle();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
