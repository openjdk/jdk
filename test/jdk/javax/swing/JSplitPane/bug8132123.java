/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

/*
 * @test
 * @bug 8132123
 * @summary MultiResolutionCachedImage unnecessarily creates base image
 *          to get its size
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual/othervm -Dsun.java2d.uiScale=2 bug8132123
 */

public class bug8132123 {

    private static final String INSTRUCTIONS = """
            Verify that JSplitPane uses high-resolution system icons for
             the one-touch expanding buttons on HiDPI displays.

            If the display does not support HiDPI mode press PASS.

            1. Run the test on HiDPI Display.
            2. Check that the one-touch expanding buttons on the JSplitPane are painted
            correctly. If so, press PASS, else press FAIL. """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("JSplitPane Instructions")
                .instructions(INSTRUCTIONS)
                .rows(10)
                .columns(40)
                .testUI(bug8132123::init)
                .build()
                .awaitAndCheck();
    }

    public static JFrame init() {
        JFrame frame = new JFrame("Test SplitPane");
        JPanel left = new JPanel();
        left.setBackground(Color.GRAY);
        JPanel right = new JPanel();
        right.setBackground(Color.GRAY);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                left, right);
        splitPane.setOneTouchExpandable(true);
        frame.setLayout(new BorderLayout());
        frame.setSize(250, 250);
        frame.getContentPane().add(splitPane);
        return frame;
    }
}
