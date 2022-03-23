/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import static javax.swing.UIManager.getInstalledLookAndFeels;

/*
 * @test
 * @key headful
 * @bug 4820080
 * @summary This test confirms that the Drag color of JSplitPane divider should
 *          be the user specified one(Red here).
 * @run main JSplitPaneDragColorTest
 */
public class JSplitPaneDragColorTest {

    // Tolerance is set inorder to negate small differences in pixel color values,
    // especially in Mac machines.
    private final static int COLOR_TOLERANCE = 9;
    private static final Color EXPECTED_DRAG_COLOR = Color.RED;
    private static JFrame frame;
    private static JSplitPane pane;
    private static Robot robot;

    public static void main(String[] args) throws Exception {

        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(200);
        // Skipping NimbusLookAndFeel & GTKLookAndFeel,
        // as both are not supported for this feature - JDK-8075914, JDK-8075608
        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
                .filter(laf -> !(laf.getName().contains("GTK")
                        || laf.getName().contains("Nimbus")))
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

                Rectangle dividerRect = getDividerRect();

                // Mouse click and right drag split pane divider
                robot.mouseMove(dividerRect.x + 5, dividerRect.y + 36);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseMove(dividerRect.x + 15, dividerRect.y + 36);
                robot.mouseMove(dividerRect.x + 5, dividerRect.y + 36);

                // Get the color of one of the pixels of the splitpane divider
                // after the drag has started. Ideally it should be the
                // SplitPaneDivider.draggingColor set by user, otherwise the test fails
                final Color actualDragColor = robot.getPixelColor(dividerRect.x + 2,
                        dividerRect.y + 2);
                if (checkDragColor(actualDragColor)) {
                    System.out.println("Test passed in " + laf);
                } else {
                    System.out.print("Expected pixel color = ");
                    System.out.printf("%X", EXPECTED_DRAG_COLOR.getRGB());
                    System.out.print(", but actual color = ");
                    System.out.printf("%X", actualDragColor.getRGB());
                    System.out.println();
                    captureScreen();
                    throw new RuntimeException("Test failed, drag color is wrong in "
                            + laf);
                }
            } finally {
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                SwingUtilities.invokeAndWait(JSplitPaneDragColorTest::disposeFrame);
            }
        }
    }

    private static boolean checkDragColor(Color actualDragColor) {
        int actualRed = actualDragColor.getRed();
        int actualGreen = actualDragColor.getGreen();
        int actualBlue = actualDragColor.getBlue();
        int expectedRed = EXPECTED_DRAG_COLOR.getRed();
        int expectedGreen = EXPECTED_DRAG_COLOR.getGreen();
        int expectedBlue = EXPECTED_DRAG_COLOR.getBlue();

        final double tolerance = Math.sqrt(
                (actualRed - expectedRed) * (actualRed - expectedRed) +
                        (actualGreen - expectedGreen) * (actualGreen - expectedGreen) +
                        (actualBlue - expectedBlue) * (actualBlue - expectedBlue));
        return (tolerance <= COLOR_TOLERANCE);
    }

    private static Rectangle getDividerRect() {
        final AtomicReference<Rectangle> rect = new AtomicReference<>();
        SwingUtilities.invokeLater(() -> {
            javax.swing.plaf.basic.BasicSplitPaneUI ui =
                    (javax.swing.plaf.basic.BasicSplitPaneUI) pane.getUI();

            javax.swing.plaf.basic.BasicSplitPaneDivider divider = ui.getDivider();
            Point dividerLoc = divider.getLocationOnScreen();
            rect.set(new Rectangle(dividerLoc.x, dividerLoc.y, divider.getWidth(),
                    divider.getHeight()));
        });
        robot.waitForIdle();
        return rect.get();
    }

    private static void captureScreen() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        try {
            ImageIO.write(
                    robot.createScreenCapture(new Rectangle(0, 0,
                            screenSize.width,
                            screenSize.height)),
                    "png",
                    new File("screen1.png")
            );
        } catch (IOException ignore) {
        }
    }

    private static void createUI() {
        frame = new JFrame();
        UIManager.put("SplitPaneDivider.draggingColor", EXPECTED_DRAG_COLOR);
        JLabel l1 = new JLabel("LEFT  LABEL", JLabel.CENTER);
        JLabel l2 = new JLabel("RIGHT LABEL", JLabel.CENTER);
        pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, l1, l2);
        frame.setSize(400, 400);
        pane.setDividerSize(15);
        pane.setDividerLocation(frame.getSize().width / 2);
        frame.getContentPane().add(pane, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
    }

    private static boolean setLookAndFeel(String lafName) {
        try {
            UIManager.setLookAndFeel(lafName);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Ignoring Unsupported L&F: " + lafName);
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
