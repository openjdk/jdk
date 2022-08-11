/*
 * Copyright (c) 1999, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.InvocationTargetException;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4209065
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary To test if the style of the text on the tab matches the description.
 * @run main/manual bug4209065
 */

public final class bug4209065 {

    private static JFrame frame;
    private static final String text =
            "If the style of the text on the tabs matches the descriptions," +
                    "\npress PASS.\n\nNOTE: where a large font is used, the" +
                    " text may be larger\nthan the tab height but this is OK" +
                    " and NOT a failure.";

    public static void createAndShowGUI() {

        frame = new JFrame("JTabbedPane");
        JTabbedPane tp = new JTabbedPane();

        tp.addTab("<html><center><font size=+3>big</font></center></html>",
                new JLabel());
        tp.addTab("<html><center><font color=red>red</font></center></html>",
                new JLabel());
        tp.addTab("<html><center><em><b>Bold Italic!</b></em></center></html>",
                new JLabel());

        frame.getContentPane().add(tp);
        frame.setSize(400, 400);

        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame,
                PassFailJFrame.Position.HORIZONTAL);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame passFailJFrame = new PassFailJFrame("JTabbedPane " +
                "Test Instructions", text, 5, 19, 35);
        SwingUtilities.invokeAndWait(bug4209065::createAndShowGUI);
        passFailJFrame.awaitAndCheck();
    }
}
