/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4243289
 * @summary Tests that TitledBorder do not draw line through its caption
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual Test4243289
 */

public class Test4243289 {
    private static JFrame frame;

    public static void main(String[] args) throws Exception {
        String testInstructions = """
                When frame starts, you'll see a panel with a TitledBorder
                with title "Panel Title". If this title is overstriken with
                the border line, test fails, otherwise it passes.
                """;
        PassFailJFrame passFailJFrame = new PassFailJFrame.Builder()
                .title("JInternalFrame Instructions")
                .instructions(testInstructions)
                .testTimeOut(5)
                .rows(4)
                .columns(35)
                .build();

        SwingUtilities.invokeAndWait(() -> {
            init();
        });
        passFailJFrame.awaitAndCheck();
    }
    public static void init() {
        frame = new JFrame("Test TitledBorder");
        Font font = new Font("Dialog", Font.PLAIN, 12); // NON-NLS: the font name
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Panel Title", // NON-NLS: the title of the border
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                font);

        JPanel panel = new JPanel();
        panel.setBorder(border);
        frame.add(panel);
        frame.setSize(300, 300);
        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.TOP_LEFT_CORNER);
        frame.setVisible(true);
    }
}
