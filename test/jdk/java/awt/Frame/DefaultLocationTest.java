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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;

/*
 * @test
 * @bug 4085599
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test default location for frame
 * @run main/manual DefaultLocationTest
 */

public class DefaultLocationTest {
    private static Frame f;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                A small frame containing the label 'Hello World' should
                appear in the upper left hand corner of the screen. The
                exact location is dependent upon the window manager.

                On Linux and Mac machines, the default location for frame
                is below the taskbar or close to top-left corner.

                Upon test completion, click Pass or Fail appropriately.""";

        PassFailJFrame passFailJFrame = new PassFailJFrame("DefaultLocationTest " +
                " Instructions", INSTRUCTIONS, 5, 10, 40);
        EventQueue.invokeAndWait(DefaultLocationTest::createAndShowUI);
        passFailJFrame.awaitAndCheck();
    }

    private static void createAndShowUI() {
        f = new Frame("DefaultLocation");
        f.add("Center", new Label("Hello World"));
        f.pack();
        PassFailJFrame.addTestWindow(f);
        PassFailJFrame.positionTestWindow(
                null, PassFailJFrame.Position.HORIZONTAL);
        f.setVisible(true);
    }
}
