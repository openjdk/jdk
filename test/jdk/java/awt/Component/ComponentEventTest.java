/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.List;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Scrollbar;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;

import jdk.test.lib.Platform;

/*
 * @test
 * @key headful
 * @bug 8333403
 * @summary Test performs various operations to check components events are triggered properly.
 * @library /test/lib
 * @build jdk.test.lib.Platform
 * @run main ComponentEventTest
 */
public class ComponentEventTest {

    private static final int DELAY = 500;

    private static Frame frame;
    private static Robot robot;

    private static Component[] components;

    private static volatile Point centerPoint;

    private static volatile boolean componentHidden;
    private static volatile boolean componentShown;
    private static volatile boolean componentMoved;
    private static volatile boolean componentResized;

    private static final ComponentListener componentListener =
        new ComponentListener() {

            @Override
            public void componentShown(ComponentEvent e) {
                System.out.println("ComponentShown: " + e.getSource());
                componentShown = true;
            }

            @Override
            public void componentResized(ComponentEvent e) {
                System.out.println("ComponentResized: " + e.getSource());
                componentResized = true;
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                System.out.println("ComponentMoved: " + e.getSource());
                componentMoved = true;
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                System.out.println("ComponentHidden: " + e.getSource());
                componentHidden = true;
            }
        };

    private static void initializeGUI() {
        frame = new Frame("Component Event Test");
        frame.setLayout(new FlowLayout());

        Panel panel = new Panel();
        Button button = new Button("Button");
        Label label = new Label("Label");
        List list = new List();
        list.add("One");
        list.add("Two");
        list.add("Three");
        Choice choice = new Choice();
        choice.add("Red");
        choice.add("Orange");
        choice.add("Yellow");
        Checkbox checkbox = new Checkbox("Checkbox");
        Scrollbar scrollbar = new Scrollbar(Scrollbar.HORIZONTAL, 0, 1, 0, 255);
        TextField textfield = new TextField(15);
        TextArea textarea = new TextArea(5, 15);

        components = new Component[] { panel, button, label, list, choice,
            checkbox, scrollbar, textfield, textarea, frame };

        for (int i = 0; i < components.length - 1; i++) {
            components[i].addComponentListener(componentListener);
            frame.add(components[i]);
        }
        frame.addComponentListener(componentListener);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            robot.setAutoWaitForIdle(true);

            EventQueue.invokeAndWait(ComponentEventTest::initializeGUI);
            robot.waitForIdle();
            robot.delay(DELAY);

            doTest();

            System.out.println("Test PASSED");
        } finally {
            EventQueue.invokeAndWait(ComponentEventTest::disposeFrame);
        }
    }

    private static void doTest()
        throws InvocationTargetException, InterruptedException {
        // Click the frame to ensure it gains focus
        clickFrame();

        robot.delay(DELAY);

        for (int i = 0; i < components.length; i++) {
            for (boolean state : new boolean[] { true, false }) {
                doTest(components[i], state);
            }
        }

        robot.delay(DELAY);

        System.out.println("Iconify frame");
        resetValues();
        testIconifyFrame();

        System.out.println("Deiconify frame");
        resetValues();
        testDeiconifyFrame();
    }

    private static void clickFrame()
        throws InvocationTargetException, InterruptedException {
        EventQueue.invokeAndWait(() -> {
            Point location = frame.getLocationOnScreen();
            Dimension size = frame.getSize();
            centerPoint = new Point(location.x + size.width / 2,
                location.y + size.height / 2);
        });

        robot.mouseMove(centerPoint.x, centerPoint.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void testIconifyFrame()
        throws InvocationTargetException, InterruptedException {
        EventQueue.invokeAndWait(() -> frame.setExtendedState(Frame.ICONIFIED));

        robot.waitForIdle();
        robot.delay(DELAY);
        if (componentShown || componentHidden || componentMoved
            || componentResized) {
            throw new RuntimeException(
                "ComponentEvent triggered when frame is iconified");
        }
    }

    private static void testDeiconifyFrame()
        throws InvocationTargetException, InterruptedException {
        EventQueue.invokeAndWait(() -> frame.setExtendedState(Frame.NORMAL));

        robot.waitForIdle();
        robot.delay(DELAY);

        /*
         * Because of the different behavior between MS Windows and other OS, we
         * receive native events WM_SIZE and WM_MOVE on Windows when the frame
         * state changes from iconified to normal. AWT sends these events to
         * components when it receives the events from the native system. See
         * JDK-6754618 for more information.
         */

        if (componentShown || componentHidden) {
            throw new RuntimeException(
                "FAIL: componentShown or componentHidden triggered "
                    + "when frame set to normal");
        }

        if (Platform.isWindows() && (!componentMoved || !componentResized)) {
            throw new RuntimeException(
                "FAIL: componentMoved or componentResized wasn't triggered "
                    + "when frame set to normal");
        }
        if (!Platform.isWindows() && (componentMoved || componentResized)) {
            throw new RuntimeException(
                "FAIL: componentMoved or componentResized triggered "
                    + "when frame set to normal");
        }
    }

    private static void doTest(final Component currentComponent, boolean enable)
        throws InvocationTargetException, InterruptedException {

        System.out.println("Component " + currentComponent);
        System.out.println("  enabled " + enable);

        EventQueue.invokeAndWait(() -> {
            currentComponent.setEnabled(enable);
            revalidateFrame();
        });

        robot.delay(DELAY);

        resetValues();
        EventQueue.invokeAndWait(() -> {
            currentComponent.setVisible(false);
            revalidateFrame();
        });

        robot.delay(DELAY);
        if (!componentHidden) {
            throw new RuntimeException("FAIL: ComponentHidden not triggered for"
                + currentComponent.getClass());
        }

        resetValues();
        EventQueue.invokeAndWait(() -> {
            currentComponent.setVisible(false);
            revalidateFrame();
        });

        robot.delay(DELAY);
        if (componentHidden) {
            throw new RuntimeException("FAIL: ComponentHidden triggered when "
                + "setVisible(false) called for a hidden "
                + currentComponent.getClass());
        }

        resetValues();
        EventQueue.invokeAndWait(() -> {
            currentComponent.setVisible(true);
            revalidateFrame();
        });

        robot.delay(DELAY);
        if (!componentShown) {
            throw new RuntimeException("FAIL: ComponentShown not triggered for "
                + currentComponent.getClass());
        }

        resetValues();
        EventQueue.invokeAndWait(() -> {
            currentComponent.setVisible(true);
            revalidateFrame();
        });

        robot.delay(DELAY);
        if (componentShown) {
            throw new RuntimeException("FAIL: ComponentShown triggered when "
                + "setVisible(true) called for a shown "
                + currentComponent.getClass());
        }

        resetValues();
        EventQueue.invokeAndWait(() -> {
            currentComponent.setLocation(currentComponent.getLocation().x + 1,
                currentComponent.getLocation().y);
            revalidateFrame();
        });

        robot.delay(DELAY);
        if (!componentMoved) {
            throw new RuntimeException("FAIL: ComponentMoved not triggered for "
                + currentComponent.getClass());
        }

        resetValues();
        EventQueue.invokeAndWait(() -> {
            currentComponent.setSize(currentComponent.getSize().width + 1,
                currentComponent.getSize().height);
            revalidateFrame();
        });

        robot.delay(DELAY);
        if (!componentResized) {
            throw new RuntimeException("FAIL: ComponentResized not triggered "
                + "when size increases for " + currentComponent.getClass());
        }

        resetValues();
        EventQueue.invokeAndWait(() -> {
            currentComponent.setSize(currentComponent.getSize().width - 1,
                currentComponent.getSize().height);
            revalidateFrame();
        });

        robot.delay(DELAY);
        if (!componentResized) {
            throw new RuntimeException("FAIL: ComponentResized not triggered "
                + "when size decreases for " + currentComponent.getClass());
        }

        System.out.println("\n");
    }

    private static void revalidateFrame() {
        frame.invalidate();
        frame.validate();
    }

    private static void resetValues() {
        componentShown = false;
        componentHidden = false;
        componentMoved = false;
        componentResized = false;
    }

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
    }
}
