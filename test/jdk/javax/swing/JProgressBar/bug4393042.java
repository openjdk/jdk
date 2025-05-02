/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4393042
 * @summary JProgressBar should update painting when maximum value is very large
 * @key headful
 */

import java.awt.Graphics;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class bug4393042 extends JProgressBar {

    static final int MAXIMUM = Integer.MAX_VALUE - 100;
    static volatile int value = 0;
    static volatile bug4393042 progressBar;
    static JFrame frame;
    static volatile int paintCount = 0;

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        System.out.println("paint count=" + (++paintCount));
    }

    public bug4393042(int min, int max) {
       super(min, max);
    }

    public static void main(String[] args) throws Exception {

       UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");

       try {
           SwingUtilities.invokeAndWait(bug4393042::createUI);

           value = 0;
           for (int i = 0; i <= 10; i++) {
               Thread.sleep(1000);
               SwingUtilities.invokeAndWait(() -> {
                  progressBar.setValue(value);
               });
               value += MAXIMUM / 10;
           }

       } finally {
           SwingUtilities.invokeAndWait(() -> {
              if (frame != null) {
                  frame.dispose();
              }
           });
        }
        if (paintCount < 10 || paintCount > 100) {
            throw new RuntimeException("Unexpected paint count : " + paintCount);
        }
    }

    static void createUI() {
        frame = new JFrame("bug4393042");
        progressBar = new bug4393042(0, MAXIMUM);
        progressBar.setStringPainted(true);
        progressBar.setValue(0);
        frame.add(progressBar);
        frame.setSize(400, 200);
        frame.setVisible(true);
    }
}
