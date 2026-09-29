/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4401853
 * @summary Tests that pasting null to TextArea and TextField on Solaris/Linux
 *          removes selected text; doing it on Windows to TextArea does nothing,
 *          to TextField removes selected text.
 * @key headful
 * @run main PasteNullToTextComponentsTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextArea;
import java.awt.TextComponent;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class PasteNullToTextComponentsTest {

    private static final int NATIVE_EVENT_PROCESSING_TIMEOUT = 500;
    private static final int WAIT_TIMEOUT = 3000;

    private boolean failed;

    private static final boolean isOSWindows =
            System.getProperty("os.name").startsWith("Windows");

    private final Object LOCK = new Object();

    private Robot robot;

    private Frame frame;
    private TextArea ta;
    private TextField tf;
    private Component initialFocusComp;

    private final String beg = "a";
    private final String sel = "b";
    private final String end = "c";
    private final String text = beg + sel + end;
    private final String begEnd = beg + end;

    private boolean initialFocusGained;

    public void init() {
        ta = new TextArea(text, 3, text.length() + 3);
        tf = new TextField(text, text.length() + 3);
        initialFocusComp = new Button("Initially focused button");

        frame = new Frame();
        frame.add(initialFocusComp, BorderLayout.NORTH);
        frame.add(ta, BorderLayout.CENTER);
        frame.add(tf, BorderLayout.SOUTH);
        frame.setSize(200, 200);

        FocusListener fl = new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                System.out.println(e + "; source class=" + e.getSource().getClass());
                synchronized (LOCK) {
                    TextComponent tc = (TextComponent) e.getComponent();
                    tc.select(1, 2);
                    robot.keyPress(KeyEvent.VK_CONTROL);
                    robot.keyPress(KeyEvent.VK_V);
                    robot.keyRelease(KeyEvent.VK_V);
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                    tc.removeFocusListener(this);
                    LOCK.notifyAll();
                }
            }
        };
        ta.addFocusListener(fl);
        tf.addFocusListener(fl);

        initialFocusComp.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                System.out.println(e + "; source class=" + e.getSource().getClass());
                synchronized (LOCK) {
                    initialFocusGained = true;
                    LOCK.notifyAll();
                }
            }
        });

        setClipboardContents(Toolkit.getDefaultToolkit().getSystemClipboard(),
                new StringSelection(null), null);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public void start() throws Exception {
        robot = new Robot();
        robot.waitForIdle();
        robot.delay(500);

        Point iniFocusPoint = initialFocusComp.getLocationOnScreen();
        synchronized (LOCK) {
            if (!initialFocusGained) {
                robot.mouseMove(iniFocusPoint.x + 3, iniFocusPoint.y + 3);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                LOCK.wait(WAIT_TIMEOUT);
            }
        }

        initialFocusComp.requestFocusInWindow();
        robot.waitForIdle();

        synchronized (LOCK) {
            ta.requestFocusInWindow();
            LOCK.wait(WAIT_TIMEOUT);
        }

        // wait until native control process key event (C^V)
        robot.waitForIdle();
        robot.delay(NATIVE_EVENT_PROCESSING_TIMEOUT);

        synchronized (LOCK) {
            tf.requestFocusInWindow();
            LOCK.wait(WAIT_TIMEOUT);
        }

        // wait until native control process key event (C^V)
        robot.waitForIdle();
        robot.delay(NATIVE_EVENT_PROCESSING_TIMEOUT);

        String taText = ta.getText();
        String tfText = tf.getText();

        System.err.println("TextArea text=" + taText +
                " TextField text=" + tfText);

        boolean taSelDeleted = begEnd.equals(taText);
        boolean taSelRemained = text.equals(taText);
        boolean tfSelDeleted = begEnd.equals(tfText);

        System.out.println("taSelDeleted = " + taSelDeleted);
        System.out.println("taSelRemained = " + taSelRemained);
        System.out.println("tfSelDeleted = " + tfSelDeleted);

        if (isOSWindows
                ? !(taSelRemained && tfSelDeleted)
                : !(taSelDeleted && tfSelDeleted)) {
            failed = true;
        }

        if (!initialFocusGained) {
            System.err.println("Initial component did not gain focus");
            failed = false;
        }

        if (failed) {
            throw new RuntimeException("test failed: wrong behavior of text " +
                    "component on pasting null");
        } else {
            System.err.println("test passed");
        }
    }


    private static void setClipboardContents(Clipboard cb,
                                             Transferable contents,
                                             ClipboardOwner owner) {
        synchronized (cb) {
            boolean set = false;
            while (!set) {
                try {
                    cb.setContents(contents, owner);
                    set = true;
                } catch (IllegalStateException ise) {
                    try { Thread.sleep(100); }
                    catch (InterruptedException e) { e.printStackTrace(); }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        PasteNullToTextComponentsTest app = new PasteNullToTextComponentsTest();
        try {
            EventQueue.invokeAndWait(app::init);
            app.start();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (app.frame != null) {
                    app.frame.dispose();
                }
            });
        }
    }
}
