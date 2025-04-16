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
 * @bug 5003022
 * @summary Test that setting zero value on JProgressBar works in GTK L&F
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug5003022
*/

import java.awt.FlowLayout;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.UIManager;

public class bug5003022 {

    static final String INSTRUCTIONS = """
         There are two progress bars, they should display progress strings.
         The first progress bar should display 0% and the bar should show no progress color fill.
         The second progress bar should display 30% and the bar should show 30% progress color fill.
         If it is as described, the test PASSES, otherwise it FAILS.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .title("bug5003022 Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(60)
            .testUI(bug5003022::createUI)
            .build()
            .awaitAndCheck();
    }

    static JFrame createUI() {
        try {
            /* This will only succeed on Linux, but the test is valid for other platforms and L&Fs */
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        } catch (Exception e) {
            e.printStackTrace();
        }

        JFrame frame = new JFrame("bug5003022");
        JProgressBar pb1 = new JProgressBar();
        pb1.setValue(0);
        pb1.setStringPainted(true);

        JProgressBar pb2 = new JProgressBar();
        pb2.setValue(30);
        pb2.setStringPainted(true);

        frame.setLayout(new FlowLayout());
        frame.add(pb1);
        frame.add(pb2);

        frame.setSize(300, 300);
        return frame;
    }

}
