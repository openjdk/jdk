/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4342129
  @summary Unable to scroll in scrollpane for canvas
  @key headful
*/

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.ScrollPane;

import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

public class ComponentScrollTest {
    public ScrollPane scrollpane;
    public Frame frame;
    public volatile int count = 0;

    public static void main(String[] args) throws Exception {
        ComponentScrollTest cst = new ComponentScrollTest();
        cst.init();
        cst.start();
    }

    public void init() throws Exception {
        EventQueue.invokeAndWait(() -> {
            scrollpane = new ScrollPane();
            frame = new Frame("Component Scroll Test");
            scrollpane.add(new Component() {
                public Dimension getPreferredSize() {
                    return new Dimension(500, 500);
                }

                public void paint(Graphics g) {
                    g.drawLine(0, 0, 500, 500);
                }
            });
            frame.add(scrollpane);
            scrollpane.getVAdjustable().addAdjustmentListener(new AdjustmentListener() {
                @Override
                public void adjustmentValueChanged(AdjustmentEvent adjustmentEvent) {
                    count++;
                    scrollpane.getVAdjustable().setValue(20);
                }
            });
            frame.pack();
            frame.setVisible(true);
        });
    }

    public void start() throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                scrollpane.getVAdjustable().setValue(20);
            });

            Thread.sleep(1000);

            System.out.println("Count = " + count);
            if (count > 50) {
                throw new RuntimeException();
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
