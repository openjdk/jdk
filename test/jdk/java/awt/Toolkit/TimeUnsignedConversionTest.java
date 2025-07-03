/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5097241
 * @summary Tests the problem of time type conversion on XToolkit. The conversion should be unsigned.
 * @requires os.family == "linux"
 * @key headful
 * @library /java/awt/regtesthelpers /test/lib
 * @build Util jtreg.SkippedException
 * @run main/othervm -Dsun.awt.disableGtkFileDialogs=true TimeUnsignedConversionTest
 */

import java.awt.Button;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jtreg.SkippedException;
import test.java.awt.regtesthelpers.Util;

public class TimeUnsignedConversionTest  {
    static Robot robot;
    static Frame frame;
    static volatile Button button;
    static volatile FileDialog dialog;
    static volatile boolean dialogShown = false;

    static final CountDownLatch passedLatch = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        if (!Toolkit.getDefaultToolkit().getClass().getName().equals("sun.awt.X11.XToolkit")) {
            throw new SkippedException("XAWT test only! Skipped.");
        }

        try {
            EventQueue.invokeAndWait(TimeUnsignedConversionTest::createAndShowGUI);
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowGUI() {
        frame = new Frame("TimeUnsignedConversionTest frame");
        button = new Button("Show Dialog");
        dialog = new FileDialog(frame, "TimeUnsignedConversionTest Dialog", FileDialog.LOAD);

        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            System.out.println(e);
            if (dialogShown && ((KeyEvent)e).getKeyCode() == KeyEvent.VK_K) {
                passedLatch.countDown();
            }
        }, KeyEvent.KEY_EVENT_MASK);

        frame.setLayout(new FlowLayout());
        frame.add(button);

        button.addActionListener(ae -> {
            if (ae.getActionCommand().equals("Show Dialog")) {
                dialog.setSize(200, 200);
                dialog.setLocationRelativeTo(frame);
                dialog.setVisible(true);
            }
        });

        frame.setSize(100, 100);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void test() throws Exception {
        robot = new Robot();
        robot.waitForIdle();

        Util.waitTillShown(button);

        robot.waitForIdle();
        robot.keyPress(KeyEvent.VK_SPACE);
        robot.delay(50);
        robot.keyRelease(KeyEvent.VK_SPACE);

        Util.waitTillShown(dialog);
        dialogShown = true;

        robot.waitForIdle();
        robot.keyPress(KeyEvent.VK_K);
        robot.delay(50);
        robot.keyRelease(KeyEvent.VK_K);

        if (!passedLatch.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("Test failed!");
        }

        System.out.println("Test passed.");
    }
}
