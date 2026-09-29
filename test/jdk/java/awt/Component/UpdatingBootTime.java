/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6461933 7194219
 * @summary adjust system boot time in nowMillisUTC() frequently
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual UpdatingBootTime
 */

/*
 * Test verifies that time updated by the system is correctly
 * picked up by the Java application.
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class UpdatingBootTime {
    private static JFrame initialize () {
        JFrame frame = new JFrame("Updating Boot Time Test Frame");
        frame.setLayout(new BorderLayout());
        JTextArea textOutput = new JTextArea("Events on the button:", 14, 40);
        textOutput.setLineWrap(true);
        JScrollPane textScrollPane = new JScrollPane(textOutput);
        frame.add(textScrollPane, BorderLayout.CENTER);
        Button b = new Button("Press me");
        frame.add(b, BorderLayout.NORTH);
        b.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    textOutput.append("\nEvent occurred : " + e);
                    textOutput.append("\nThe event time is : " + (new Date(e.getWhen())));
                    textOutput.append("\nThe system time is : " + (new Date()));
                    textOutput.append("\n------------------------------------");
                    textOutput.setCaretPosition(textOutput.getText().length());
                }
            });
        frame.pack();
        return frame;
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        String instructions =
                """
            1) In the test window press "Press me" button.
            2) Two timestamps should be printed.
            3) Verify that they are not differ a lot:
               it is okay to observe a 1 or 2 seconds difference.
            4) Change the system time significantly (by a month or a year)
               by using the OS abilities.
            5) Click on the button once again.
            6) Printed times should still be the same.
               Pay attention to the Month/Year if they were changed.
            7) It is okay to observe a 1 or 2 seconds difference and this is not a fail.
            8) If the difference is more then 1-2 seconds noticed press fail,
               otherwise press pass.
            """;

        PassFailJFrame.builder()
                .title("Updating Boot Time Test Instructions")
                .instructions(instructions)
                .rows((int) instructions.lines().count() + 1)
                .columns(40)
                .testUI(UpdatingBootTime::initialize)
                .build()
                .awaitAndCheck();
    }
}
