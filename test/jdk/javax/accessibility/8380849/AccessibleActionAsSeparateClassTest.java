/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

/*
 * @test
 * @key headful
 * @bug 8380849
 * @summary manual test for VoiceOver activating an AccessibleAction
 * @requires os.family == "mac"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual AccessibleActionAsSeparateClassTest
 */

public class AccessibleActionAsSeparateClassTest {

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = "INSTRUCTIONS:\n" +
                "1. Open VoiceOver\n" +
                "2. Move the VoiceOver cursor gray circle.\n" +
                "3. Press CTRL + ALT + SPACE to click the button.\n\n" +
                "Expected behavior: the ellipse should change to green.";

        PassFailJFrame.builder()
                .title("AccessibleActionAsSeparateClassTest Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(AccessibleActionAsSeparateClassTest::createUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createUI() {
        JFrame f = new JFrame();
        f.add(new CustomComponent());
        f.pack();
        f.setVisible(true);
        return f;
    }

    /**
     * This is a custom JComponent that uses AccessibleRole.PUSH_BUTTON.
     * Its AccessibleContext identifes an AccessibleAction that is NOT
     * the same object as the AccessibleContext.
     */
    static class CustomComponent extends JComponent implements Accessible {
        boolean clickedSuccessfully = false;

        public CustomComponent() {
            setPreferredSize(new Dimension(120, 120));
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(clickedSuccessfully ? Color.green : Color.gray);
            g.fillOval(0, 0, getWidth(), getHeight());
        }

        @Override
        public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleJComponent() {
                    AccessibleAction action = new AccessibleAction() {
                        @Override
                        public int getAccessibleActionCount() {
                            return 1;
                        }

                        @Override
                        public String getAccessibleActionDescription(int i) {
                            if (i == 0) {
                                return UIManager.getString(
                                        "AbstractButton.clickText");
                            } else {
                                return null;
                            }
                        }

                        @Override
                        public boolean doAccessibleAction(int i) {
                            if (i == 0) {
                                clickedSuccessfully = true;
                                repaint();
                                return true;
                            } else {
                                return false;
                            }
                        }
                    };

                    @Override
                    public AccessibleRole getAccessibleRole() {
                        return AccessibleRole.PUSH_BUTTON;
                    }

                    @Override
                    public AccessibleAction getAccessibleAction() {
                        return action;
                    }
                };
            }
            return accessibleContext;
        }
    }
}
