/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.List;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @key headful
 * @bug 8302525
 * @summary Test performs various mouse and key operations to check events are getting triggered properly.
 * @run main MouseAndKeyEventStressTest
 */
public class MouseAndKeyEventStressTest {

    private volatile static int mouseButtonTypes[] =
        { InputEvent.BUTTON1_DOWN_MASK, InputEvent.BUTTON2_DOWN_MASK,
            InputEvent.BUTTON3_DOWN_MASK };
    private volatile static String mouseButtonNames[] =
        { "BUTTON1", "BUTTON2", "BUTTON3" };

    private static Frame frame;
    private volatile static Canvas canvas;
    private volatile static Button button;
    private volatile static List list;
    private volatile static Choice choice;
    private volatile static Checkbox checkbox;
    private volatile static Component[] components;

    private volatile static boolean keyPressed;
    private volatile static boolean keyReleased;
    private volatile static boolean mousePressed;
    private volatile static boolean mouseReleased;
    private volatile static boolean actionPerformed;
    private volatile static boolean itemEventPerformed;

    private volatile static Robot robot;
    private volatile static Point compAt;
    private volatile static Dimension compSize;

    private static void initializeGUI() {
        frame = new Frame("Test Frame");
        frame.setLayout(new FlowLayout());
        canvas = new Canvas();
        canvas.setSize(50, 50);
        canvas.setBackground(Color.red);
        button = new Button("Button");
        list = new List();
        list.add("One");
        list.add("Two");
        list.add("Three");
        choice = new Choice();
        for (int i = 0; i < 8; i++) {
            choice.add("Choice " + i);
        }
        choice.select(3);
        checkbox = new Checkbox("Checkbox");

        components = new Component[] { canvas, button, list, choice, checkbox };

        button.addActionListener((actionEvent) -> {
            actionPerformed = true;
            System.out.println("button Got an actionEvent: " + actionEvent);
        });
        checkbox.addItemListener((itemEvent) -> {
            itemEventPerformed = true;
            System.out.println("checkbox Got a ItemEvent: " + itemEvent);
        });
        list.addItemListener((itemEvent) -> {
            itemEventPerformed = true;
            System.out.println("List Got a  ItemEvent: " + itemEvent);
        });
        choice.addItemListener((itemEvent) -> {
            itemEventPerformed = true;
            System.out.println("Choice Got a  ItemEvent: " + itemEvent);
        });
        for (int i = 0; i < components.length; i++) {
            components[i].addKeyListener(new KeyAdapter() {

                public void keyPressed(KeyEvent ke) {
                    System.out.println("Got a  keyPressedSource: " + ke);
                    keyPressed = true;
                }

                public void keyReleased(KeyEvent ke) {
                    System.out.println("Got a  keyReleasedSource: " + ke);
                    keyReleased = true;
                }
            });
            components[i].addMouseListener(new MouseAdapter() {

                public void mousePressed(MouseEvent me) {
                    mousePressed = true;
                    System.out.println("Got a  mousePressSource: " + me);
                }

                public void mouseReleased(MouseEvent me) {
                    mouseReleased = true;
                    System.out.println("Got a  mouseReleaseSource: " + me);
                }

            });
            frame.add(components[i]);
        }

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(MouseAndKeyEventStressTest::initializeGUI);
            doTest();
        } finally {
            EventQueue.invokeAndWait(MouseAndKeyEventStressTest::disposeFrame);
        }
    }

    private static void doTest() throws Exception {
        robot = new Robot();
        robot.setAutoDelay(100);
        robot.waitForIdle();

        canvasMouseKeyTest();
        buttonMouseKeyTest();
        listMouseKeyTest();
        choiceMouseKeyTest();
        checkboxMouseKeyTest();

        System.out.println("Test passed!");
    }

    private static void canvasMouseKeyTest() throws Exception {
        Component component = canvas;
        robot.waitForIdle();

        for (int i = 0; i < mouseButtonTypes.length; i++) {
            resetValues();
            EventQueue.invokeAndWait(() -> {
                compAt = component.getLocationOnScreen();
                compSize = component.getSize();
            });

            robot.mouseMove(compAt.x + compSize.width / 2,
                compAt.y + compSize.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.waitForIdle();
            if (!mousePressed) {
                throw new RuntimeException(
                    "FAIL: Moving focus. mousePressed event did not occur for "
                        + component.getClass());
            }

            resetValues();
            robot.keyPress(KeyEvent.VK_A);
            robot.waitForIdle();
            if (!keyPressed) {
                throw new RuntimeException(
                    "FAIL: keyPressed event " + "did not occur for "
                        + component.getClass() + " for key A");
            }

            resetValues();
            robot.mousePress(mouseButtonTypes[i]);
            robot.waitForIdle();
            if (!mousePressed) {
                throw new RuntimeException(
                    "FAIL: mousePressed event did not occur for "
                        + component.getClass() + " for " + mouseButtonNames[i]);
            }

            resetValues();
            robot.mouseRelease(mouseButtonTypes[i]);
            robot.waitForIdle();
            if (!mouseReleased) {
                throw new RuntimeException(
                    "FAIL: mouseReleased event did not occur for "
                        + component.getClass() + " for " + mouseButtonNames[i]);
            }

            resetValues();
            robot.keyRelease(KeyEvent.VK_A);
            robot.waitForIdle();
            if (!keyReleased) {
                throw new RuntimeException("FAIL: keyReleased event "
                    + "did not occur for " + component.getClass());
            }

            keyType(KeyEvent.VK_ESCAPE);
            robot.waitForIdle();
        }
        System.out.println("Test passed:" + component);
    }

    private static void buttonMouseKeyTest() throws Exception {
        Component component = button;
        robot.waitForIdle();

        for (int i = 0; i < mouseButtonTypes.length; i++) {
            resetValues();
            EventQueue.invokeAndWait(() -> {
                compAt = component.getLocationOnScreen();
                compSize = component.getSize();
            });

            robot.mouseMove(compAt.x + compSize.width / 2,
                compAt.y + compSize.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.waitForIdle();
            if (!mousePressed) {
                throw new RuntimeException(
                    "FAIL: Moving focus. mousePressed event did not occur for "
                        + component.getClass());
            }

            resetValues();
            robot.keyPress(KeyEvent.VK_A);
            robot.waitForIdle();
            if (!keyPressed) {
                throw new RuntimeException(
                    "FAIL: keyPressed event " + "did not occur for "
                        + component.getClass() + " for key A");
            }

            resetValues();
            robot.mousePress(mouseButtonTypes[i]);
            robot.waitForIdle();
            if (!mousePressed) {
                throw new RuntimeException(
                    "FAIL: mousePressed event did not occur for "
                        + component.getClass() + " for " + mouseButtonNames[i]);
            }

            resetValues();
            robot.mouseRelease(mouseButtonTypes[i]);
            robot.waitForIdle();

            if (!actionPerformed) {
                throw new RuntimeException(
                    "FAIL: action event did not occur for "
                        + component.getClass() + " for " + mouseButtonNames[i]);
            }

            if (!mouseReleased) {
                throw new RuntimeException(
                    "FAIL: mouseReleased event did not occur for "
                        + component.getClass() + " for " + mouseButtonNames[i]);
            }

            resetValues();
            robot.keyRelease(KeyEvent.VK_A);
            robot.waitForIdle();
            if (!keyReleased) {
                throw new RuntimeException("FAIL: keyReleased event "
                    + "did not occur for " + component.getClass());
            }

            keyType(KeyEvent.VK_ESCAPE);
            robot.waitForIdle();
        }
        System.out.println("Test passed:" + component);
    }

    private static void listMouseKeyTest() throws Exception {
        Component component = list;
        robot.waitForIdle();

        for (int i = 0; i < mouseButtonTypes.length; i++) {
            resetValues();
            EventQueue.invokeAndWait(() -> {
                compAt = component.getLocationOnScreen();
                compSize = component.getSize();
            });

            robot.mouseMove(compAt.x + compSize.width / 2,
                compAt.y + compSize.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.waitForIdle();
            if (!mousePressed) {
                throw new RuntimeException(
                    "FAIL: Moving focus. mousePressed event did not occur for "
                        + component.getClass());
            }

            resetValues();
            robot.keyPress(KeyEvent.VK_A);
            robot.waitForIdle();
            if (!keyPressed) {
                throw new RuntimeException(
                    "FAIL: keyPressed event " + "did not occur for "
                        + component.getClass() + " for key A");
            }

            resetValues();
            robot.mousePress(mouseButtonTypes[i]);
            robot.waitForIdle();
            if (!mousePressed) {
                throw new RuntimeException(
                    "FAIL: mousePressed event did not occur for "
                        + component.getClass() + " for " + mouseButtonNames[i]);
            }

            resetValues();
            robot.mouseRelease(mouseButtonTypes[i]);
            robot.waitForIdle();

            if (!itemEventPerformed) {
                throw new RuntimeException("FAIL: Item event did not occur for "
                    + component.getClass() + " for " + mouseButtonNames[i]);
            }

            if (!mouseReleased) {
                throw new RuntimeException(
                    "FAIL: mouseReleased event did not occur for "
                        + component.getClass() + " for " + mouseButtonNames[i]);
            }

            resetValues();
            robot.keyRelease(KeyEvent.VK_A);
            robot.waitForIdle();
            if (!keyReleased) {
                throw new RuntimeException("FAIL: keyReleased event "
                    + "did not occur for " + component.getClass());
            }

            keyType(KeyEvent.VK_ESCAPE);
            robot.waitForIdle();
        }
        System.out.println("Test passed:" + component);
    }

    private static void choiceMouseKeyTest() throws Exception {
        Component component = choice;
        robot.waitForIdle();

        for (int i = 0; i < mouseButtonTypes.length; i++) {
            resetValues();
            EventQueue.invokeAndWait(() -> {
                compAt = component.getLocationOnScreen();
                compSize = component.getSize();
            });

            robot.mouseMove(compAt.x + compSize.width / 2,
                compAt.y + compSize.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.waitForIdle();
            if (!mousePressed) {
                throw new RuntimeException(
                    "FAIL: Moving focus. mousePressed event did not occur for "
                        + component.getClass());
            }

            if (component instanceof Choice) {
                keyType(KeyEvent.VK_ESCAPE);
            }

            resetValues();
            robot.keyPress(KeyEvent.VK_A);
            robot.waitForIdle();
            if (!keyPressed) {
                throw new RuntimeException(
                    "FAIL: keyPressed event " + "did not occur for "
                        + component.getClass() + " for key A");
            }

            resetValues();
            robot.mousePress(mouseButtonTypes[i]);
            robot.waitForIdle();
            if (!mousePressed) {
                throw new RuntimeException(
                    "FAIL: mousePressed event did not occur for "
                        + component.getClass() + " for " + mouseButtonNames[i]);
            }

            resetValues();
            boolean isMac =
                System.getProperty("os.name").toLowerCase().contains("os x");
            if (isMac) {
                // Choice's pop-up menu is drawn in front of choice. So
                // choice can not get mouse events generated by robot,
                // that's why test is made to dispath event.
                MouseEvent me =
                    new MouseEvent(choice, MouseEvent.MOUSE_RELEASED,
                        System.currentTimeMillis(), mouseButtonTypes[i],
                        compSize.width, compSize.height, 1, false);
                choice.dispatchEvent(me);
            } else {
                robot.mouseRelease(mouseButtonTypes[i]);
            }
            robot.waitForIdle();

            if (!mouseReleased) {
                throw new RuntimeException(
                    "FAIL: mouseReleased event did not occur for "
                        + component.getClass() + " for " + mouseButtonNames[i]);
            }

            robot.mouseMove(compAt.x + compSize.width / 2,
                compAt.y + compSize.height + 30);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.waitForIdle();

            if (!itemEventPerformed) {
                throw new RuntimeException("FAIL: Item event did not occur for "
                    + component.getClass() + " for " + mouseButtonNames[i]);
            }

            resetValues();
            robot.keyRelease(KeyEvent.VK_A);
            robot.waitForIdle();
            if (!keyReleased) {
                throw new RuntimeException("FAIL: keyReleased event "
                    + "did not occur for " + component.getClass());
            }

            keyType(KeyEvent.VK_ESCAPE);
            robot.waitForIdle();
        }
        System.out.println("Test passed:" + component);
    }

    private static void checkboxMouseKeyTest() throws Exception {
        Component component = checkbox;
        robot.waitForIdle();
        for (int i = 0; i < mouseButtonTypes.length; i++) {

            resetValues();
            EventQueue.invokeAndWait(() -> {
                compAt = component.getLocationOnScreen();
                compSize = component.getSize();
            });

            robot.mouseMove(compAt.x + compSize.width / 2,
                compAt.y + compSize.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.waitForIdle();
            if (!mousePressed) {
                throw new RuntimeException(
                    "FAIL: Moving focus. mousePressed event did not occur for "
                        + component.getClass());
            }

            resetValues();
            robot.keyPress(KeyEvent.VK_A);
            robot.waitForIdle();
            if (!keyPressed) {
                throw new RuntimeException(
                    "FAIL: keyPressed event " + "did not occur for "
                        + component.getClass() + " for key A");
            }

            resetValues();
            robot.mousePress(mouseButtonTypes[i]);
            robot.waitForIdle();
            if (!mousePressed) {
                throw new RuntimeException(
                    "FAIL: mousePressed event did not occur for "
                        + component.getClass() + " for " + mouseButtonNames[i]);
            }

            resetValues();
            robot.mouseRelease(mouseButtonTypes[i]);
            robot.waitForIdle();
            if (!mouseReleased) {
                throw new RuntimeException(
                    "FAIL: mouseReleased event did not occur for "
                        + component.getClass() + " for " + mouseButtonNames[i]);
            }

            if (!itemEventPerformed) {
                throw new RuntimeException("FAIL: Item event did not occur for "
                    + component.getClass() + " for " + mouseButtonNames[i]);
            }

            resetValues();
            robot.keyRelease(KeyEvent.VK_A);
            robot.waitForIdle();
            if (!keyReleased) {
                throw new RuntimeException("FAIL: keyReleased event "
                    + "did not occur for " + component.getClass());
            }

            keyType(KeyEvent.VK_ESCAPE);
            robot.waitForIdle();
        }
        System.out.println("Test passed:" + component);
    }

    private static void resetValues() {
        keyPressed = false;
        keyReleased = false;
        mousePressed = false;
        mouseReleased = false;
    }

    private static void keyType(int key) throws Exception {
        robot.keyPress(key);
        robot.keyRelease(key);
    }

    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
    }

}
