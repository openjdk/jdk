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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/*
 * @test
 * @key headful
 * @bug 4234266
 * @summary To test setLabel functionality on Windows.
 * @requires (os.family == "windows")
 */

public class SetLabelTest {
    private static Robot robot;
    private static Frame frame;
    private static MenuBar mb;
    private static PopupMenu pm;
    private static Point frameLoc;
    private static StringBuffer errorLog = new StringBuffer();

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(50);

            EventQueue.invokeAndWait(() -> createTestUI());
            robot.delay(1000);
            EventQueue.invokeAndWait(() -> frameLoc = frame.getLocationOnScreen());

            checkMenu();
            checkPopupMenu();

            if (!errorLog.isEmpty()) {
                throw new RuntimeException("Before & After screenshots are same." +
                        " Test fails for the following case(s):\n" + errorLog);
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createTestUI() {
        frame = new Frame("Menu SetLabel Test");
        frame.setUndecorated(true);
        mb = new MenuBar();

        //Menu 1
        Menu menu1 = new Menu("Menu1");
        MenuItem mi1 = new MenuItem("MI-1");
        menu1.add(mi1);
        menu1.addSeparator();
        MenuItem mi2 = new MenuItem("MI-2");
        menu1.add(mi2);
        mb.add(menu1);

        //Popup menu
        pm = new PopupMenu();
        MenuItem pm1 = new MenuItem("PMI-1");
        pm.add(pm1);
        frame.add(pm);

        frame.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() != InputEvent.BUTTON1_DOWN_MASK) {
                    showPopup(e);
                }
            }

            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    pm.show(e.getComponent(),
                            e.getX(), e.getY());
                }
            }
        });
        frame.setMenuBar(mb);
        frame.setSize(300, 200);
        frame.setLocation(500,500);
        frame.setVisible(true);
    }

    private static void checkMenu() throws IOException {
        BufferedImage beforeImgMenu = robot.createScreenCapture(frame.getBounds());
        Menu m1 = mb.getMenu(0);
        m1.setLabel("New Menu");
        MenuItem mItem1 = m1.getItem(0);
        mItem1.setLabel("New Menu Item");
        robot.delay(200);
        BufferedImage afterImgMenu = robot.createScreenCapture(frame.getBounds());

        if (compareImages(beforeImgMenu, afterImgMenu)) {
            ImageIO.write(beforeImgMenu, "png", new File("MenuBefore.png"));
            ImageIO.write(afterImgMenu, "png", new File("MenuAfter.png"));
            errorLog.append("Menu case\n");
        }
    }

    private static void checkPopupMenu() throws IOException {
        BufferedImage beforeImgPopup = robot.createScreenCapture(frame.getBounds());
        robot.mouseMove(frameLoc.x + (frame.getWidth() / 2),
                frameLoc.y + (frame.getHeight() /2));
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        robot.delay(200);
        pm.getItem(0).setLabel("Changed Label");
        BufferedImage afterImgPopup = robot.createScreenCapture(frame.getBounds());
        robot.delay(300);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        if (compareImages(beforeImgPopup, afterImgPopup)) {
            ImageIO.write(beforeImgPopup, "png", new File("PopupBefore.png"));
            ImageIO.write(afterImgPopup, "png", new File("PopupAfter.png"));
            errorLog.append("Popup case\n");
        }
    }

    private static boolean compareImages(BufferedImage beforeImg, BufferedImage afterImg) {
        for (int x = 0; x < beforeImg.getWidth(); x++) {
            for (int y = 0; y < beforeImg.getHeight(); y++) {
                if (beforeImg.getRGB(x, y) != afterImg.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }
}
