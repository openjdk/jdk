/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4942457
 * @key headful
 * @summary Verifies that filtering of resize events on native level works.
 * I.E.after Frame is shown no additional resize events are generated.
 * @library /java/awt/patchlib ../../regtesthelpers
 * @build java.desktop/java.awt.Helper
 * @build Util
 * @run main NoResizeEvent
 */

import test.java.awt.regtesthelpers.Util;

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class NoResizeEvent {
    //Mutter can send window insets too late, causing additional resize events.
    private static final boolean IS_MUTTER = Util.getWMID() == Util.MUTTER_WM;
    private static final int RESIZE_COUNT_LIMIT = IS_MUTTER ? 5 : 3;
    private static Frame frame;
    static int resize_count = 0;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> createUI());
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
            if (resize_count > RESIZE_COUNT_LIMIT) {
                throw new RuntimeException("Resize event arrived: "
                    + resize_count + " times.");
            }
        }
    }

    private static void createUI() {
        frame = new Frame("NoResizeEvent");
        frame.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                System.out.println(e);
                resize_count++;
            }
        });
        frame.setVisible(true);

        try {
            Thread.sleep(3000);
        } catch (InterruptedException ie) {
        }
        System.out.println("Resize count: " + resize_count);
    }
}
