/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8215921
 * @summary Test that selecting a different item does send an ItemEvent
 * @key headful
 * @run main SelectNewItemTest
*/
public final class SelectNewItemTest
        extends WindowAdapter
        implements ItemListener {
    private static Frame frame;
    private static Choice choice;

    private final Robot robot;

    private final CountDownLatch windowOpened = new CountDownLatch(1);
    private final CountDownLatch mouseClicked = new CountDownLatch(1);
    private final CountDownLatch itemStateChanged = new CountDownLatch(1);

    private SelectNewItemTest() throws AWTException {
        robot = new Robot();
        robot.setAutoDelay(500);
    }

    private void createUI() {
        frame = new Frame("SelectNewItemTest");
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

    private void runTest() throws Exception {
        EventQueue.invokeAndWait(this::createUI);

        if (!windowOpened.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("Frame is not open in time");
        }
        robot.waitForIdle();

        final int selectedIndex = getSelectedIndex();

        final Rectangle choiceRect = getChoiceRect();
        robot.mouseMove(choiceRect.x + choiceRect.width - 10,
                        choiceRect.y + choiceRect.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        if (!mouseClicked.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("Mouse is not clicked in time");
        }
        robot.waitForIdle();

        robot.mouseMove(choiceRect.x + choiceRect.width / 2,
                        choiceRect.y + choiceRect.height * 3);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        if (!itemStateChanged.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("ItemEvent is received");
        }

        if (selectedIndex == getSelectedIndex()) {
            throw new RuntimeException("Selected index in Choice should've changed");
        }
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
        try {
            new SelectNewItemTest().runTest();
        } finally {
            EventQueue.invokeAndWait(SelectNewItemTest::dispose);
        }
    }

    private static void dispose() {
        if (frame != null) {
            frame.dispose();
        }
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        System.out.println("itemStateChanged: " + e);
        itemStateChanged.countDown();
    }

    @Override
    public void windowOpened(WindowEvent e) {
        System.out.println("windowActivated()");
        windowOpened.countDown();
    }

}
