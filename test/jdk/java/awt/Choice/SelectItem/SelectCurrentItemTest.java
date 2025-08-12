/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/*
 * @test
 * @bug 4902933 8197810
 * @summary Test that selecting the current item does not send an ItemEvent
 * @key headful
 * @run main SelectCurrentItemTest
*/
public class SelectCurrentItemTest
        extends WindowAdapter
        implements ItemListener {
    private static Frame frame;
    private static Choice choice;

    private final Robot robot;

    private final CountDownLatch windowOpened = new CountDownLatch(1);
    private final CountDownLatch mouseClicked = new CountDownLatch(1);

    protected final CountDownLatch itemStateChanged = new CountDownLatch(1);

    protected SelectCurrentItemTest() throws AWTException {
        robot = new Robot();
        robot.setAutoDelay(250);
    }

    private void createUI() {
        frame = new Frame(getClass().getName());
        frame.setLayout(new BorderLayout());

        choice = new Choice();
        for (int i = 0; i < 10; i++) {
            choice.add("Choice Item " + i);
        }
        choice.addItemListener(this);
        choice.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                System.out.println("mouseClicked()");
                mouseClicked.countDown();
            }
        });

        frame.add(choice, BorderLayout.CENTER);

        frame.addWindowListener(this);

        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.pack();
        frame.setVisible(true);
    }

    protected final void runTest()
            throws InterruptedException, InvocationTargetException {
        try {
           doTest();
        } finally {
            EventQueue.invokeAndWait(this::dispose);
        }
    }

    private void doTest()
            throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(this::createUI);

        if (!windowOpened.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("Frame is not open in time");
        }
        robot.waitForIdle();

        final int initialIndex = getSelectedIndex();

        final Rectangle choiceRect = getChoiceRect();

        // Open the choice popup
        robot.mouseMove(choiceRect.x + choiceRect.width - 10,
                        choiceRect.y + choiceRect.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        if (!mouseClicked.await(500, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("Mouse is not clicked in time");
        }
        robot.waitForIdle();

        // Click an item in the choice popup
        final Point pt = getClickLocation(choiceRect);
        robot.mouseMove(pt.x, pt.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        robot.waitForIdle();

        checkItemStateChanged();

        final int currentIndex = getSelectedIndex();
        System.out.println("initialIndex = " + initialIndex);
        System.out.println("currentIndex = " + currentIndex);
        checkSelectedIndex(initialIndex, currentIndex);
    }

    protected void checkItemStateChanged() throws InterruptedException {
        if (itemStateChanged.await(500, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("ItemEvent is received but unexpected");
        }
    }

    protected void checkSelectedIndex(final int initialIndex,
                                      final int currentIndex) {
        if (initialIndex != currentIndex) {
            throw new RuntimeException("Selected index in Choice should not change");
        }
    }

    /**
     * {@return the location for clicking choice popup to select an item}
     * @param choiceRect the bounds of the Choice component
     */
    protected Point getClickLocation(final Rectangle choiceRect) {
        // Click on the first item in the popup, it's the selected item
        return new Point(choiceRect.x + choiceRect.width / 2,
                         choiceRect.y + choiceRect.height + 3);
    }

    private int getSelectedIndex()
            throws InterruptedException, InvocationTargetException {
        AtomicInteger index = new AtomicInteger();
        EventQueue.invokeAndWait(() -> index.set(choice.getSelectedIndex()));
        return index.get();
    }

    private Rectangle getChoiceRect()
            throws InterruptedException, InvocationTargetException {
        AtomicReference<Rectangle> rect = new AtomicReference<>();
        EventQueue.invokeAndWait(
                () -> rect.set(new Rectangle(choice.getLocationOnScreen(),
                                             choice.getSize())));
        return rect.get();
    }

    public static void main(String... args) throws Exception {
        new SelectCurrentItemTest().runTest();
    }

    private void dispose() {
        if (frame != null) {
            frame.dispose();
        }
    }

    @Override
    public final void itemStateChanged(ItemEvent e) {
        System.out.println("itemStateChanged: " + e);
        itemStateChanged.countDown();
    }

    @Override
    public final void windowOpened(WindowEvent e) {
        System.out.println("windowActivated()");
        windowOpened.countDown();
    }

}
