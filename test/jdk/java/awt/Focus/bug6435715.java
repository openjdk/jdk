/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6435715
 * @summary JButton stops receiving the focusGained event and eventually focus is lost altogether
 * @modules java.desktop/sun.awt
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug6435715
 */

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class bug6435715 {

    private static final String INSTRUCTIONS = """
            1. after test started you will see frame with three buttons. Notice that focus is on Button2.
            2. Click on Button 3. Focus goes to Button3.
            3. Click on Button1 and quickly switch to another window. Via either alt/tab or
            clicking another window with the mouse.
            4. After a few seconds, come back to the frame. Notice that focus is around Button2
            5. Click at Button3. If focus remains at Button2 test failed, if focus is on Button3 - test passed.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug6435715 Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 5)
                .columns(35)
                .testUI(bug6435715::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame fr = new JFrame("FocusIssue");
        sun.awt.SunToolkit.setLWRequestStatus(fr, true);

        JPanel panel = new JPanel();
        final JButton b1 = new JButton("Button 1");
        final JButton b2 = new JButton("Button 2");
        final JButton b3 = new JButton("Button 3");

        panel.add(b1);
        panel.add(b2);
        panel.add(b3);

        b1.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent event) {
                synchronized (this) {
                    try {
                        wait(1000);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    b2.requestFocus();
                }
            }
        });
        fr.getContentPane().add(panel);
        fr.pack();
        return fr;
    }

}
