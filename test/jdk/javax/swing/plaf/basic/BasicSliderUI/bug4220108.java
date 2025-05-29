/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4220108
 * @summary JSlider in JInternalFrame should be painted correctly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4220108
 */

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JSlider;

public class bug4220108 {
    static final String INSTRUCTIONS = """
        If you see a slider in the internal frame, then the test PASSES.
        Otherwise the test FAILS.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4220108 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4220108::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame f = new JFrame("Internal Frame Slider Test");
        f.setLayout(new FlowLayout());
        JDesktopPane desktop = new JDesktopPane();
        f.setContentPane(desktop);

        JInternalFrame iFrame =
                new JInternalFrame("Slider Frame", true, true, true, true);
        JSlider sl = new JSlider();
        iFrame.add(sl);
        iFrame.add(new JLabel("Label"), BorderLayout.SOUTH);
        desktop.add(iFrame);
        iFrame.pack();
        iFrame.setVisible(true);
        f.setSize(300, 200);
        return f;
    }
}
