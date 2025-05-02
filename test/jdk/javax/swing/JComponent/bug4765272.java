/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4765272
 * @summary REGRESSION: IAE: focusCycleRoot not focus cyle root of a Component
 * @key headful
 */

import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.FocusTraversalPolicy;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class bug4765272 {
    static boolean focusGained = false;
    static JFrame f;
    static JButton bt1;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                f = new JFrame("bug4765272");
                bt1 = new JButton("Button 1");
                JButton bt2 = new JButton("Button 2");

                JPanel p = new JPanel();
                p.setLayout(new FlowLayout());
                p.add(bt1);
                p.add(bt2);
                f.getContentPane().add(p);

                FocusTraversalPolicy policy = new FocusTraversalPolicy() {
                    @Override
                    public Component getComponentAfter(Container aContainer, Component aComponent) {
                        if (aComponent == bt1) {
                            return bt2;
                        }
                        return bt1;
                    }

                    @Override
                    public Component getComponentBefore(Container aContainer, Component aComponent) {
                        if (aComponent == bt1) {
                            return bt2;
                        }
                        return bt1;
                    }

                    @Override
                    public Component getFirstComponent(Container aContainer) {
                        return bt1;
                    }

                    @Override
                    public Component getLastComponent(Container aContainer) {
                        return bt2;
                    }

                    @Override
                    public Component getDefaultComponent(Container aContainer) {
                        return bt1;
                    }
                };

                bt1.addFocusListener(new FocusAdapter() {
                    public void focusGained(FocusEvent e) {
                        p.removeAll();
                        synchronized (this) {
                            focusGained = true;
                            this.notifyAll();
                        }
                    }
                });

                f.setLocationRelativeTo(null);
                f.setVisible(true);
            });

            Robot r = new Robot();
            r.waitForIdle();
            r.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                bt1.requestFocus();
                try {
                    if (!focusGained) {
                        Thread.sleep(5000);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }
}
