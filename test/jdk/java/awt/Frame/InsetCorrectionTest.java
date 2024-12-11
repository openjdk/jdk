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
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 4091426
 * @key headful
 * @summary Test inset correction when setVisible(true) BEFORE setSize(), setLocation()
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual InsetCorrectionTest
 */

public class InsetCorrectionTest {
    private static final String INSTRUCTIONS = """
            There is a frame of size 300x300 at location (100,100).
            It has a menubar with one menu, 'File', but the frame
            is otherwise empty.  In particular, there should be no
            part of the frame that is not shown in the background color.
            Upon test completion, click Pass or Fail appropriately.
            """;

    private static InsetCorrection testFrame;

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> testFrame = new InsetCorrection());

        try {
            PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                    .title("InsetCorrectionTest Instructions")
                    .instructions(INSTRUCTIONS)
                    .columns(45)
                    .logArea(3)
                    .build();
            EventQueue.invokeAndWait(() ->
                    PassFailJFrame.log("frame location: " + testFrame.getBounds()));
            passFailJFrame.awaitAndCheck();
        } finally {
            EventQueue.invokeAndWait(testFrame::dispose);
        }
    }

    static class InsetCorrection extends Frame
            implements ActionListener {
        MenuBar mb;
        Menu file;
        MenuItem cause_bug_b;

        public InsetCorrection() {
            super("InsetCorrection");
            mb = new MenuBar();
            file = new Menu("File");
            mb.add(file);
            cause_bug_b = new MenuItem("cause bug");
            file.add(cause_bug_b);
            setMenuBar(mb);
            cause_bug_b.addActionListener(this);

            // Making the frame visible before setSize and setLocation()
            // are being called causes sometimes strange behaviour with
            // JDK1.1.5G. The frame is then sometimes to large and the
            // excess areas are drawn in black. This only happens
            // sometimes.
            setVisible(true);
            setSize(300, 300);
            setLocation(100, 100);
        }

        public void actionPerformed(ActionEvent e) {
            setVisible(false);
            setVisible(true);
        }
    }
}
