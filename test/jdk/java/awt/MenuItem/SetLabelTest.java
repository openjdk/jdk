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

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/*
 * @test
 * @key headful
 * @bug 4234266
 * @summary To test setLabel functionality on Windows.
 * @requires (os.family == "windows")
 * @run main/othervm -Dsun.java2d.uiScale=1 SetLabelTest
 */

public class SetLabelTest implements ActionListener {
    private static Robot robot;
    private static Frame frame;
    private static MenuBar mb;
    private static Point frameLoc;
    private static boolean passed = true;
    private static final String[][] newLabels = {new String[]{"New Menu-1", "New MI-1"},
                                                 new String[]{"New PM-1", "New PMI-1"}};

    public static void main(String[] args) throws Exception {
        SetLabelTest obj = new SetLabelTest();
        obj.start();
    }

    public void start() throws Exception {
        try {
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(50);

            EventQueue.invokeAndWait(this::createTestUI);
            robot.delay(1000);
            EventQueue.invokeAndWait(() -> frameLoc = frame.getLocationOnScreen());

            // First Menu
            robot.mouseMove(frameLoc.x + 35, frameLoc.y + 35);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(300);
            robot.mouseMove(frameLoc.x + 35, frameLoc.y + 90);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(500);
            captureScreen("Img_1");

            //Second Menu
            robot.mouseMove(frameLoc.x + 130, frameLoc.y + 35);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(300);
            robot.mouseMove(frameLoc.x + 130, frameLoc.y + 90);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(500);
            captureScreen("Img_2");

            if(!checkLabels()) {
                captureScreen("Img_Failed");
                throw new RuntimeException("Test Failed! setLabel does not work");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private void createTestUI() {
        frame = new Frame("Menu & MenuItem SetLabel Test");
        mb = new MenuBar();

        //Menu 1
        Menu menu1 = new Menu("Menu1");
        MenuItem mi1 = new MenuItem("MI-1");
        mi1.addActionListener(this);
        menu1.add(mi1);
        menu1.addSeparator();
        MenuItem mi2 = new MenuItem("Change Menu1");
        mi2.addActionListener(this);
        menu1.add(mi2);
        mb.add(menu1);

        //Popup menu
        PopupMenu pm = new PopupMenu("PopupMenu1");
        MenuItem pm1 = new MenuItem("PMI-1");
        pm1.addActionListener(this);
        pm.add(pm1);
        pm.addSeparator();
        MenuItem pm2 = new MenuItem("Change Menu2");
        pm2.addActionListener(this);
        pm.add(pm2);
        mb.add(pm);

        frame.setMenuBar(mb);
        frame.setSize(300, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void changeLabels(int menuIndex, String[] labels) {
            Menu m1 = mb.getMenu(menuIndex);
            m1.setLabel(labels[0]);
            MenuItem mItem1 = m1.getItem(0);
            mItem1.setLabel(labels[1]);
    }

    private static boolean checkLabels() {
        for (int i = 0; i < 2; i++) {
            Menu m1 = mb.getMenu(i);
            String menuLabel = m1.getLabel();
            String menuItemLabel = m1.getItem(0).getLabel();
            if (!(menuLabel.equals(newLabels[i][0])
                    && menuItemLabel.equals(newLabels[i][1]))) {
                passed = false;
                break;
            }
        }
        return passed;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals("Change Menu1")) {
            changeLabels(0, newLabels[0]);
        } else {
            changeLabels(1, newLabels[1]);
        }
    }

    private static void captureScreen(String filename) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        try {
            ImageIO.write(
                    robot.createScreenCapture(new Rectangle(0, 0, screenSize.width, screenSize.height)),
                    "png",
                    new File(filename + ".png")
            );
        } catch (IOException ignored) {
        }
    }
}
