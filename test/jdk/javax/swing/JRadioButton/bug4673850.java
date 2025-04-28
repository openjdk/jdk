/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4673850
 * @summary Tests that JRadioButton and JCheckBox checkmarks are painted entirely
 *          inside circular/rectangle checkboxes for Motif LaF.
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jtreg.SkippedException
 * @run main/manual bug4673850
 */

import java.awt.FlowLayout;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JRadioButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import jtreg.SkippedException;

public class bug4673850 {
    private static final String INSTRUCTIONS = """
            <html>
            <head>
            <style>
            p {
              font: sans-serif;
            }
            </style>
            </head>
            <body>
            <p><b>This test is for Motif LaF.</b></p>

            <p><b>
            When the test starts, you'll see 2 radio buttons and 2 check boxes
            with the checkmarks painted.</b></p>

            <p><b>
            Ensure that all the button's checkmarks are painted entirely
            within the circular/rectangle checkbox, NOT over them or outside them.
            </b></p>
            </body>
            """;

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
        } catch (Exception e) {
            throw new SkippedException("Unsupported LaF", e);
        }

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .rows(10)
                .columns(45)
                .testUI(createAndShowUI())
                .build()
                .awaitAndCheck();
    }

    private static JFrame createAndShowUI() {
        JFrame frame = new JFrame("bug4673850");
        frame.setLayout(new FlowLayout());

        JRadioButton rb = new JRadioButton("RadioButt");
        rb.setSelected(true);
        frame.add(rb);
        JRadioButton rb2 = new JRadioButton("RadioButt");
        rb2.setHorizontalTextPosition(SwingConstants.LEFT);
        rb2.setSelected(true);
        frame.add(rb2);

        JCheckBox cb = new JCheckBox("CheckBox");
        cb.setSelected(true);
        frame.add(cb);
        JCheckBox cb2 = new JCheckBox("CheckBox");
        cb2.setHorizontalTextPosition(SwingConstants.LEFT);
        cb2.setSelected(true);
        frame.add(cb2);
        frame.setSize(200, 150);
        return frame;
    }
}