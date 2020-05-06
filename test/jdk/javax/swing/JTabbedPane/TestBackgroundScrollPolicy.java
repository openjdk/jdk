/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;

import static javax.swing.UIManager.*;
import static javax.swing.SwingUtilities.*;

/*
 * @test
 * @key headful
 * @bug 8172269
 * @summary Tests JTabbedPane background for SCROLL_TAB_LAYOUT
 */

public class TestBackgroundScrollPolicy implements Runnable {
    private static final ArrayList<String> LIST = new ArrayList<>();
    private static final LookAndFeelInfo[] INFO = getInstalledLookAndFeels();
    private static final CountDownLatch LATCH = new CountDownLatch(INFO.length);
    private static Robot ROBOT;

    public static void main(String[] args) throws Exception {
        ROBOT = new Robot();
        invokeLater(new TestBackgroundScrollPolicy());
        LATCH.await();
        if (!LIST.isEmpty()) {
            throw new RuntimeException(LIST.toString());
        }
    }

    private static void addOpaqueError(boolean opaque) {
        LIST.add(getLookAndFeel().getName() + " background color wrong for opaque=" + opaque);
    }

    private static boolean updateLookAndFeel() {
        int index = (int) LATCH.getCount() - 1;
        if (index >= 0) {
            try {
                LookAndFeelInfo info = INFO[index];
                System.err.println("L&F: " + info.getName());
                setLookAndFeel(info.getClassName());
                return true;
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
        return false;
    }

    private JFrame frame;
    private JTabbedPane pane;

    public void run() {
        if (this.frame == null) {
            if (!updateLookAndFeel()) {
                return;
            }
            this.pane = new JTabbedPane();
            this.pane.setOpaque(false);
            this.pane.setBackground(Color.RED);
            for (int i = 0; i < 3; i++) {
                this.pane.addTab("Tab " + i, new JLabel("Content area " + i));
            }
            this.pane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
            this.frame = new JFrame(getClass().getSimpleName());
            this.frame.getContentPane().setBackground(Color.BLUE);
            this.frame.add(this.pane);
            this.frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            this.frame.setSize(400, 200);
            this.frame.setLocationRelativeTo(null);
            this.frame.setVisible(true);
        } else {
            Point point = new Point(this.pane.getWidth() - 2, 2);
            convertPointToScreen(point, this.pane);
            Color actual = ROBOT.getPixelColor(point.x, point.y);

            boolean opaque = this.pane.isOpaque();
            Color expected = opaque
                    ? this.pane.getBackground()
                    : this.frame.getContentPane().getBackground();

            System.out.println("expected " + expected + " actual " + actual);
            if (!expected.equals(actual)){
                addOpaqueError(opaque);
            }
            if (!opaque) {
                this.pane.setOpaque(true);
                this.pane.repaint();
            } else {
                this.frame.dispose();
                this.frame = null;
                this.pane = null;
                LATCH.countDown();
            }

        }
        ROBOT.delay(2000);
        SecondaryLoop secondaryLoop =
                Toolkit.getDefaultToolkit().getSystemEventQueue()
                        .createSecondaryLoop();
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                }
                secondaryLoop.exit();
                invokeLater(TestBackgroundScrollPolicy.this);
            }
        }.start();
        secondaryLoop.enter();
    }
}
