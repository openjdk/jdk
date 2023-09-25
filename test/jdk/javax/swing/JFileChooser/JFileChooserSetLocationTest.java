/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
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
 * @bug 4390885
 * @summary This test checks CCC #4390885, which verifies that it should be
 *          possible to set the location of the JFileChooser.
 * @run main JFileChooserSetLocationTest
 */
public class JFileChooserSetLocationTest {

    public static final String SHOW_DIALOG_OUTSIDE_THE_PANEL =
            "ShowFileChooser OUTSIDE the Panel";
    public static final String SHOW_DIALOG_OVER_THE_PANEL =
            "ShowFileChooser OVER the Panel";
    public static final String SHOW_SAVE_DIALOG_OVER_THE_PANEL =
            "ShowSaveDialog";
    private static final int TOLERANCE_LEVEL = 6;
    private static final int xOut = 75;
    private static final int yOut = 75;
    private static Robot robot;
    private static JPanel panel;
    private static MyFileChooser fileChooser;
    private static int xIn;
    private static int yIn;
    private static JButton btn;
    private static JButton btn1;
    private static JButton btn2;
    private static JFrame frame;

    public static void main(String[] s) throws Exception {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(200);

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
                AtomicReference<Dimension> dim = new AtomicReference<>();
                SwingUtilities.invokeAndWait(() -> {
                    pt.set(panel.getLocationOnScreen());
                    dim.set(panel.getSize());
                });
                Point panelLoc = pt.get();
                Dimension panelDim = dim.get();
                xIn = (panelLoc.x + panelDim.width) / 2;
                yIn = (panelLoc.y + panelDim.height) / 2;

                Point dest = getCenterPointOf(btn);

                robot.mouseMove(dest.x, dest.y);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                robot.waitForIdle();

                Point actualPos = getActualLocation(fileChooser);

                // Case 1 :  Verifying that the location of JFileChooser
                // 'Show Dialog' is correctly set outside the frame at (25,25)
                verify(xOut, actualPos.x, yOut, actualPos.y);

                hitKeys(KeyEvent.VK_ESCAPE);

                dest = getCenterPointOf(btn1);
                robot.mouseMove(dest.x, dest.y);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                actualPos = getActualLocation(fileChooser);

                // Case 2 :  Verifying that the location of JFileChooser
                // 'Show Dialog' is correctly set inside the test frame
                verify(xIn, actualPos.x, yIn, actualPos.y);

                hitKeys(KeyEvent.VK_ESCAPE);

                dest = getCenterPointOf(btn2);
                robot.mouseMove(dest.x, dest.y);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                actualPos = getActualLocation(fileChooser);

                //  Case 3 :  Verifying that the location of JFileChooser
                //  'Save Dialog' is correctly set inside the test frame
                verify(xIn, actualPos.x, yIn, actualPos.y);

                hitKeys(KeyEvent.VK_ESCAPE);

                System.out.println("Test Passed, All cases passed for " + laf);
            } finally {
                SwingUtilities.invokeAndWait(
                        JFileChooserSetLocationTest::disposeFrame);
            }
        }
    }

    private static Point getCenterPointOf(final Component comp)
            throws Exception {

        AtomicReference<Point> pt = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> pt.set(comp.getLocationOnScreen()));
        Point loc = pt.get();
        loc.translate(comp.getWidth() / 2, comp.getHeight() / 2);
        return loc;
    }

    private static Point getActualLocation(final MyFileChooser fcoo)
            throws Exception {
        AtomicReference<Point> pt = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> pt.set(fcoo.getDialogLocation()));
        return pt.get();
    }

    public static void verify(int x1, int x2, int y1, int y2) throws Exception {
        System.out.println("verify " + x1 + "==" + x2 + "; " + y1 + "==" + y2);
        if ((Math.abs(x1 - x2) < TOLERANCE_LEVEL) &&
            (Math.abs(y1 - y2) < TOLERANCE_LEVEL)) {
            System.out.println("Test passed");
        } else {
            GraphicsConfiguration gc = GraphicsEnvironment.
                    getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
            Rectangle gcBounds = gc.getBounds();
            BufferedImage bufferedImage = robot.createScreenCapture(
                    new Rectangle(gcBounds));
            ImageIO.write(bufferedImage, "png",new File("FailureImage.png"));
            throw new RuntimeException(
                    "Test Failed, setLocation() is not working properly");
        }
    }

    private static void hitKeys(int... keys) {
        for (int key : keys) {
            robot.keyPress(key);
        }

        for (int i = keys.length - 1; i >= 0; i--) {
            robot.keyRelease(keys[i]);
        }
    }

    public static void createUI() {
        frame = new JFrame();
        panel = new JPanel();
        btn = new JButton(SHOW_DIALOG_OUTSIDE_THE_PANEL);
        btn1 = new JButton(SHOW_DIALOG_OVER_THE_PANEL);
        btn2 = new JButton(SHOW_SAVE_DIALOG_OVER_THE_PANEL);
        ActionListener actionListener = actionEvent -> {
            String btnAction = actionEvent.getActionCommand();
            if (btnAction.equals(SHOW_DIALOG_OUTSIDE_THE_PANEL)) {
                fileChooser = new MyFileChooser(xOut, yOut);
                fileChooser.showOpenDialog(panel);
            } else if (btnAction.equals(SHOW_DIALOG_OVER_THE_PANEL)) {
                fileChooser = new MyFileChooser(xIn, yIn);
                fileChooser.showOpenDialog(panel);
            } else if (btnAction.equals(SHOW_SAVE_DIALOG_OVER_THE_PANEL)) {
                fileChooser = new MyFileChooser(xIn, yIn);
                fileChooser.showSaveDialog(panel);
            }
        };
        btn.addActionListener(actionListener);
        btn1.addActionListener(actionListener);
        btn2.addActionListener(actionListener);
        panel.add(btn);
        panel.add(btn1);
        panel.add(btn2);

        frame.add(panel);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    private static boolean setLookAndFeel(String lafName) {
        try {
            System.out.println("Testing " + lafName);
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

    private static class MyFileChooser extends JFileChooser {
        JDialog dialog;
        int x, y;

        public MyFileChooser(int x, int y) {
            super();
            this.x = x;
            this.y = y;
        }

        protected JDialog createDialog(Component parent)
                throws HeadlessException {

            dialog = super.createDialog(parent);

            System.out.println(
                    "createDialog and set location to (" + x + ", " + y + ")");
            dialog.setLocation(x, y);

            return dialog;
        }

        public Point getDialogLocation() {
            return dialog.getLocation();
        }

    }

}
