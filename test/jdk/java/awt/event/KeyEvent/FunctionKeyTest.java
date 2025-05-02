/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.Event;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Robot;
import java.awt.TextArea;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static java.awt.Event.KEY_ACTION;
import static java.awt.Event.KEY_ACTION_RELEASE;
import static java.util.concurrent.TimeUnit.SECONDS;

/*
 * @test
 * @bug 4011219
 * @summary Test for function key press/release received by Java client.
 * @key headful
 */

public final class FunctionKeyTest {
    private static Frame frame;
    private static Robot robot;

    private static final CyclicBarrier keyPress = new CyclicBarrier(2);
    private static final CyclicBarrier keyRelease = new CyclicBarrier(2);

    private static final CountDownLatch frameActivated = new CountDownLatch(1);

    private static final List<Error> failures = new ArrayList<>(4);
    private static final AtomicReference<Exception> edtException = new AtomicReference<>();

    private static void testKey(int keyCode, String keyText) throws Exception {
        robot.keyPress(keyCode);
        try {
            keyPress.await(2, SECONDS);
        } catch (TimeoutException e) {
            keyPress.reset();
            failures.add(new Error(keyText + " key press is not received", e));
        }

        robot.keyRelease(keyCode);
        try {
            keyRelease.await(2, SECONDS);
        } catch (TimeoutException e) {
            keyRelease.reset();
            failures.add(new Error(keyText + " key release is not received", e));
        }
    }

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(150);

        try {
            EventQueue.invokeAndWait(() -> {
                frame = new FunctionKeyTester();
                frame.setSize(200, 200);
                frame.setLocationRelativeTo(null);
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowActivated(WindowEvent e) {
                        System.out.println("frame.windowActivated");
                        frameActivated.countDown();
                    }
                });
                frame.setVisible(true);
            });

            if (!frameActivated.await(2, SECONDS)) {
                throw new Error("Frame wasn't activated");
            }
            robot.delay(100);

            testKey(KeyEvent.VK_F11, "F11");
            testKey(KeyEvent.VK_F12, "F12");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }

        if (!failures.isEmpty()) {
            System.err.println("Failures detected:");
            failures.forEach(System.err::println);
            if (edtException.get() != null) {
                System.err.println("\nException on EDT:");
                edtException.get().printStackTrace();
            }
            System.err.println();
            throw new RuntimeException("Test failed: " + failures.get(0).getMessage(),
                                       failures.get(0));
        }

        if (edtException.get() != null) {
            throw new RuntimeException("Test failed because of exception on EDT",
                                       edtException.get());
        }
    }

    private static final class FunctionKeyTester extends Frame {
        Label l = new Label ("NULL");
        Button b = new Button("button");
        TextArea log = new TextArea();

        FunctionKeyTester() {
            super("Function Key Test");
            this.setLayout(new BorderLayout());
            this.add(BorderLayout.NORTH, l);
            this.add(BorderLayout.SOUTH, b);
            this.add(BorderLayout.CENTER, log);
            log.setFocusable(false);
            log.setEditable(false);
            l.setBackground(Color.red);
            setSize(200, 200);
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean handleEvent(Event e) {
            String message = "e.id=" + e.id + "\n";
            System.out.print(message);
            log.append(message);

            try {
                switch (e.id) {
                    case KEY_ACTION
                            -> keyPress.await();
                    case KEY_ACTION_RELEASE
                            -> keyRelease.await();
                }
            } catch (Exception ex) {
                if (!edtException.compareAndSet(null, ex)) {
                    edtException.get().addSuppressed(ex);
                }
            }

            return super.handleEvent(e);
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean keyDown(Event e, int key) {
            l.setText("e.key=" + e.key);
            return false;
        }
    }
}
