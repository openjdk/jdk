/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4235215
 * @summary Tests that Toolkit.getPrintJob() do not throw NPE
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4235215
 */

import java.awt.Toolkit;
import javax.swing.JButton;
import javax.swing.JFrame;

public class bug4235215 {

    private static final String INSTRUCTIONS = """
        Press "Print Dialog" button.
        If you see a print dialog, test passes.
        Click "Cancel" button to close it.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4235215 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(bug4235215::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4235215");
        JButton button = new JButton("Print Dialog");
        button.addActionListener(ev -> {
            Toolkit.getDefaultToolkit().getPrintJob(frame, "Test Printing", null);
        });
        frame.add(button);
        frame.pack();
        return frame;
    }
}
