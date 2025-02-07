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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.Robot;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.util.Hashtable;

/*
 * @test
 * @bug 4214550
 * @summary Tests that there is no seg fault on repeatedly showing
 *          PopupMenu by right-clicking Label, Panel or Button
 * @key headful
 * @run main ActivePopupCrashTest
 */

public class ActivePopupCrashTest {
    private static Frame f;
    private static Label l;
    private static Button b;
    private static Panel p;

    private static volatile Point labelCenter;
    private static volatile Point buttonCenter;
    private static volatile Point panelCenter;

    public static void main(String[] args) throws Exception {
        final int REPEAT_COUNT = 5;
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(50);
            EventQueue.invokeAndWait(ActivePopupCrashTest::createAndShowUI);
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                labelCenter = getCenterPoint(l);
                buttonCenter = getCenterPoint(b);
                panelCenter = getCenterPoint(p);
            });

            for (int i = 0; i < REPEAT_COUNT; i++) {
                robot.mouseMove(labelCenter.x, labelCenter.y);
                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);

                robot.mouseMove(buttonCenter.x, buttonCenter.y);
                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);

                robot.mouseMove(panelCenter.x, panelCenter.y);
                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
            }

            // To close the popup, otherwise test fails on windows with timeout error
            robot.mouseMove(panelCenter.x - 5, panelCenter.y - 5);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    private static Point getCenterPoint(Component component) {
        Point p = component.getLocationOnScreen();
        Dimension size = component.getSize();
        return new Point(p.x + size.width / 2, p.y + size.height / 2);
    }

    public static void createAndShowUI() {
        f = new Frame("ActivePopupCrashTest Test");
        MenuItem item = new MenuItem("file-1");
        item.addActionListener(ActivePopupCrashTest::logActionEvent);
        Menu m = new Menu("file");
        m.add(item);
        item = new MenuItem("file-2");
        m.add(item);
        MenuBar mb = new MenuBar();
        mb.add(m);

        f.setMenuBar(mb);
        f.setSize(200, 200);
        f.setLayout(new BorderLayout());

        l = new Label("label");
        addPopup(l, "label");
        f.add(l, BorderLayout.NORTH);

        p = new Panel();
        addPopup(p, "panel");
        f.add(p, BorderLayout.CENTER);

        b = new Button("button");
        addPopup(b, "button");
        f.add(b, BorderLayout.SOUTH);

        f.setSize(400, 300);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    static void addPopup(Component c, String name) {
        PopupMenu pm = new PopupMenu();
        MenuItem mi = new MenuItem(name + "-1");
        mi.addActionListener(ActivePopupCrashTest::logActionEvent);
        pm.add(mi);

        mi = new MenuItem(name + "-2");
        pm.add(mi);

        setHash(c, pm);
        c.add(pm);
        c.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                mouseAction("mouseClicked", e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                mouseAction("mousePressed", e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                mouseAction("mouseReleased", e);
            }
        });
    }

    static void logActionEvent(ActionEvent e) {
        System.out.println("actionPerformed, event=" + e + ", mod=" + getMods(e));
        System.out.println("command=" + e.getActionCommand());
        System.out.println("param=" + e.paramString());
        System.out.println("source=" + e.getSource());
    }

    static String getMods(ActionEvent e) { return getMods(e.getModifiers()); }

    static String getMods(MouseEvent e) { return getMods(e.getModifiers()); }

    static String getMods(int mods) {
        String modstr = "";
        if ((mods & ActionEvent.SHIFT_MASK) == ActionEvent.SHIFT_MASK) {
            modstr += (" SHIFT");
        } else if ((mods & ActionEvent.ALT_MASK) == ActionEvent.ALT_MASK) {
            modstr += (" ALT");
        } else if ((mods & ActionEvent.CTRL_MASK) == ActionEvent.CTRL_MASK) {
            modstr += (" CTRL");
        } else if ((mods & ActionEvent.META_MASK) == ActionEvent.META_MASK) {
            modstr += (" META");
        }
        return modstr;
    }

    static void mouseAction(String which, MouseEvent e) {
        Component c = e.getComponent();
        System.out.println(which + " e = " + e + " , mods = " + getMods(e) +
                " , component = " + c);
        if (e.isPopupTrigger()) {
            System.out.println("isPopup");
            PopupMenu pm = getHash(c);
            pm.show(c, c.getWidth() / 2, c.getHeight() / 2);
        }
    }

    static Hashtable<Component, PopupMenu> popupTable = new Hashtable<>();

    static void setHash(Component c, PopupMenu p) {
        popupTable.put(c, p);
    }

    static PopupMenu getHash(Component c) {
        return popupTable.get(c);
    }

}
