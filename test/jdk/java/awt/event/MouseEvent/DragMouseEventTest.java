/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4327618 4327623 4327639 4327654 4327664 4327666 4327676 4327679 4507822
 * @summary Tests that MouseDragged and MouseReleased are triggered by Button,
 * Checkbox, Choice, Label, List, Scrollbar, TextArea, TextField
 * for Left, Middle and Right mouse buttons
 * @key headful
 * @library /lib/client /java/awt/regtesthelpers
 * @build ExtendedRobot Util
 * @run main/othervm -Dsun.java2d.uiScale=1 DragMouseEventTest
*/

import java.awt.BorderLayout;
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
import java.awt.Rectangle;
import java.awt.Scrollbar;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;

import test.java.awt.regtesthelpers.Util;

public class DragMouseEventTest {
    private static ExtendedRobot robot;
    private static DragMouseEventFrame dmef;
    private static final int DELAY = 200;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(DragMouseEventTest::createAndShowGUI);
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (dmef != null) {
                    dmef.dispose();
                }
            });
        }
    }

    private static void createAndShowGUI() {
        dmef = new DragMouseEventFrame();
        dmef.setVisible(true);
    }

    private static void test() throws Exception {
        robot = new ExtendedRobot();
        robot.waitForIdle();
        robot.delay(500);

        testComponent(dmef.scrollbar);
        testComponent(dmef.choice);
        testComponent(dmef.textarea);
        testComponent(dmef.textfield);
        testComponent(dmef.checkbox);
        testComponent(dmef.label);
        testComponent(dmef.list);
        testComponent(dmef.button);
    }

    private static void testComponent(Component component) throws Exception {
        Rectangle componentBounds = Util.invokeOnEDT(() -> {
            Point locationOnScreen = component.getLocationOnScreen();
            Dimension size = component.getSize();
            return new Rectangle(locationOnScreen, size);
        });

        Rectangle frameBounds = Util.invokeOnEDT(() -> dmef.getBounds());

        Point start = new Point(componentBounds.x + 10, componentBounds.y + 10);

        Adapter adapter = getAdapterFromComponent(component);

        testItemStateChanged(component, adapter, start, componentBounds);
        testActionListener(component, adapter, start);

        Point mid = getEndPoint(start, frameBounds, 3);
        Point end = getEndPoint(start, frameBounds, 15);

        testButtonDrag(component, adapter, MouseEvent.BUTTON1_DOWN_MASK, start, mid, end);
        testButtonDrag(component, adapter, MouseEvent.BUTTON2_DOWN_MASK, start, mid, end);
        testButtonDrag(component, adapter, MouseEvent.BUTTON3_DOWN_MASK, start, mid, end);
    }

    private static Adapter getAdapterFromComponent(Component component) {
        return (Adapter) Arrays
                .stream(component.getMouseListeners())
                .filter((m) -> m instanceof Adapter)
                .findFirst()
                .orElseThrow();
    }

    private static void testItemStateChanged(Component component,
                                             Adapter adapter,
                                             Point start,
                                             Rectangle componentBounds) {
        if (!(component instanceof Choice
                || component instanceof Checkbox
                || component instanceof List)) {
            return;
        }

        System.out.println("\ntestItemStateChanged " + component);

        adapter.reset();
        robot.mouseMove(start.x, start.y);
        robot.waitForIdle();
        robot.click();

        if (component instanceof Choice) {
            robot.mouseMove(start.x, componentBounds.y + componentBounds.height + 25);
            robot.waitForIdle();
            robot.click();
        }

        robot.waitForIdle();

        if (!adapter.itemStateChangedReceived) {
            throw new RuntimeException("itemStateChanged was not received for " + component);
        }
    }

    private static void testActionListener(Component component,
                                           Adapter adapter,
                                           Point start) {
        if (!(component instanceof Button || component instanceof List)) {
            // skip for not applicable components
            return;
        }

        System.out.println("\ntestActionListener " + component);
        adapter.reset();

        robot.mouseMove(start.x, start.y);
        robot.waitForIdle();

        if (component instanceof List) {
            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.delay(25);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
            robot.delay(25);
            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.delay(25);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(DELAY);
        } else {
            robot.click();
        }

        robot.waitForIdle();
        robot.delay(DELAY);

        if (!adapter.actionPerformedReceived) {
            throw new RuntimeException("actionPerformed was not received for " + component);
        }
    }

    private static String getButtonName(int button) {
        return switch (button) {
            case MouseEvent.BUTTON1_DOWN_MASK -> "BUTTON1";
            case MouseEvent.BUTTON2_DOWN_MASK -> "BUTTON2";
            case MouseEvent.BUTTON3_DOWN_MASK -> "BUTTON3";
            default -> throw new IllegalStateException("Unexpected value: " + button);
        };
    }

    private static void testButtonDrag(Component component,
                                      Adapter adapter,
                                      int button,
                                      Point start, Point mid, Point end) {
        String buttonName = getButtonName(button);
        System.out.printf("\n> testButtonDrag: %s on %s\n",
                buttonName, component);

        robot.mouseMove(start.x, start.y);
        robot.waitForIdle();

        robot.mousePress(button);
        robot.waitForIdle();

        System.out.printf("> gliding from (%d,%d) to (%d,%d)\n",
                start.x, start.y, mid.x, mid.y);
        robot.glide(start, mid);
        robot.waitForIdle();
        robot.delay(DELAY);


        // Catch events only after we leave the frame boundaries
        adapter.reset();

        System.out.printf("> gliding after crossing the border (%d,%d) to (%d,%d)\n",
                mid.x, mid.y, end.x, end.y);
        robot.glide(mid, end);

        robot.mouseRelease(button);
        robot.waitForIdle();
        robot.delay(DELAY);
        System.out.printf("> %s released\n", buttonName);

        boolean mouseDraggedReceived = adapter.mouseDraggedReceived;
        boolean mouseReleasedReceived = adapter.mouseReleasedReceived;

        if (component instanceof Choice) {
            // Close the popup if it is still open
            robot.keyPress(KeyEvent.VK_ESCAPE);
            robot.delay(25);
            robot.keyRelease(KeyEvent.VK_ESCAPE);
            robot.waitForIdle();
            robot.delay(DELAY);
        }


        if (!mouseDraggedReceived || !mouseReleasedReceived) {
            throw new RuntimeException(("%d: Mouse drag or release was not received\n" +
                    "mouseDraggedReceived %b mouseReleasedReceived %b")
                    .formatted(button, mouseDraggedReceived, mouseReleasedReceived));
        }
    }

    /*
     * returns the closest border point with a specified offset
     */
    private  static Point getEndPoint(Point start, Rectangle bounds, int offset) {
        int left = bounds.x;
        int right = bounds.x + bounds.width;
        int top = bounds.y;
        int bottom = bounds.y + bounds.height;

        int distanceLeft = start.x - left;
        int distanceRight = right - start.x;
        int distanceTop = start.y - top;
        int distanceBottom = bottom - start.y;

        int minDistance = Math.min(
                Math.min(distanceLeft, distanceRight),
                Math.min(distanceTop, distanceBottom)
        );

        if (minDistance == distanceLeft) {
            return new Point(left - offset, start.y);
        } else if (minDistance == distanceRight) {
            return new Point(right + offset, start.y);
        } else if (minDistance == distanceTop) {
            return new Point(start.x, top - offset);
        } else {
            return new Point(start.x, bottom + offset);
        }
    }

    private static class DragMouseEventFrame extends Frame {
        TextArea textarea = new TextArea("TextArea", 20, 30);
        Label label = new Label("Label");
        Panel panel = new Panel();
        List list = new List();
        Choice choice = new Choice();
        Button button = new Button("Button");
        TextField textfield = new TextField("TextField");
        Checkbox checkbox = new Checkbox("CheckBox");
        Scrollbar scrollbar = new Scrollbar();
        Panel centerPanel = new Panel();

        public DragMouseEventFrame() {
            setTitle("DragMouseEventTest");

            add(centerPanel, BorderLayout.CENTER);
            centerPanel.setLayout(new FlowLayout());

            add(panel, BorderLayout.NORTH);
            panel.setLayout(new FlowLayout());

            choice.add("choice item 1");
            choice.add("choice item 2");
            choice.add("choice item 3");
            panel.add(choice);

            Adapter adapter = new Adapter();
            choice.addMouseMotionListener(adapter);
            choice.addMouseListener(adapter);
            choice.addItemListener(adapter);

            adapter = new Adapter();
            panel.add(label);
            label.addMouseMotionListener(adapter);
            label.addMouseListener(adapter);

            adapter = new Adapter();
            panel.add(button);
            button.addMouseMotionListener(adapter);
            button.addMouseListener(adapter);
            button.addActionListener(adapter);

            adapter = new Adapter();
            panel.add(checkbox);
            checkbox.addMouseMotionListener(adapter);
            checkbox.addMouseListener(adapter);
            checkbox.addItemListener(adapter);

            adapter = new Adapter();
            panel.add(textfield, BorderLayout.EAST);
            textfield.addMouseMotionListener(adapter);
            textfield.addMouseListener(adapter);
            textfield.addActionListener(adapter);

            adapter = new Adapter();
            add(textarea, BorderLayout.EAST);
            textarea.addMouseMotionListener(adapter);
            textarea.addMouseListener(adapter);

            adapter = new Adapter();
            list.add("list item 1");
            list.add("list item 2");
            add(list, BorderLayout.SOUTH);
            list.addMouseMotionListener(adapter);
            list.addMouseListener(adapter);
            list.addActionListener(adapter);
            list.addItemListener(adapter);

            adapter = new Adapter();
            add(scrollbar, BorderLayout.WEST);
            scrollbar.addMouseMotionListener(adapter);
            scrollbar.addMouseListener(adapter);

            setSize(500, 400);
            setLocationRelativeTo(null);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    DragMouseEventFrame.this.dispose();
                }
            });
        }
    }

    private static class Adapter extends MouseAdapter
            implements ActionListener, ItemListener {

        public volatile boolean mouseDraggedReceived = false;
        public volatile boolean mouseReleasedReceived = false;
        public volatile boolean itemStateChangedReceived = false;
        public volatile boolean actionPerformedReceived = false;


        public void mouseDragged(MouseEvent me) {
            mouseDraggedReceived = true;
            System.out.println(me.paramString());
        }

        private void consumeIfNeeded(MouseEvent me) {
            Component c = me.getComponent();
            // do not show popup menu for the following components,
            // as it may interfere with the testing.
            if (c instanceof TextArea
                || c instanceof TextField
                || c instanceof Scrollbar) {
                if (me.isPopupTrigger()) {
                    System.out.println("CONSUMED: " + me);
                    me.consume();
                }
            }
        }

        public void mouseReleased(MouseEvent me) {
            consumeIfNeeded(me);
            mouseReleasedReceived = true;
            System.out.println(me.paramString());
        }

        public void mousePressed(MouseEvent me) {
            consumeIfNeeded(me);
            System.out.println(me.paramString());
        }

        public void mouseClicked(MouseEvent me) {
            System.out.println(me.paramString());
        }

        public void actionPerformed(ActionEvent e) {
            actionPerformedReceived = true;
            System.out.println(e.paramString());
        }

        public void itemStateChanged(ItemEvent e) {
            itemStateChangedReceived = true;
            System.out.println(e.paramString());
        }

        public void reset() {
            mouseDraggedReceived = false;
            mouseReleasedReceived = false;
            itemStateChangedReceived = false;
            actionPerformedReceived = false;
        }
    }
}
