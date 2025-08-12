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

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4134035
 * @key headful
 * @summary Ensure default button reference is removed from the RootPane
 * when a hierarchy containing the RootPane's default button is removed
 * from the RootPane. (a memory leak)
 * @run main DefaultButtonLeak
 */

public class DefaultButtonLeak {
    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("DefaultButtonLeak");
            try {
                JPanel bigPanel = new JPanel();
                bigPanel.setLayout(new GridLayout(10, 10));
                for (int i = 0; i < 100; i++) {
                    JButton button = new JButton("Button" + i);
                    bigPanel.add(button);
                    if (i == 0) {
                        frame.getRootPane().setDefaultButton(button);
                    }
                }
                frame.add(bigPanel, BorderLayout.CENTER);
                frame.pack();
                frame.setVisible(true);
                frame.remove(bigPanel);
                if (frame.getRootPane().getDefaultButton() != null) {
                    throw new RuntimeException("RootPane default button reference not removed.");
                }
            } finally {
                frame.setVisible(false);
                frame.dispose();
            }
        });
    }
}
