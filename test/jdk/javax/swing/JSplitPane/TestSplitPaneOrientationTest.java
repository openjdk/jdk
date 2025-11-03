/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4265389 8365886
 * @summary  Verifies JSplitPane support ComponentOrientation
 * @run main TestSplitPaneOrientationTest
 */

import java.awt.ComponentOrientation;
import javax.swing.JButton;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class TestSplitPaneOrientationTest {

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported LAF: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException
                 | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void testSwitchOrientation() {
        JSplitPane jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                        new JButton("Red"), new JButton("Green"));
        for (int i = 0; i < 3; i++) {
            ComponentOrientation co = jsp.getComponentOrientation();
            if (co.isLeftToRight()) {
                jsp.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            } else {
                jsp.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Left=");
            if (jsp.getLeftComponent() instanceof JButton leftButton) {
                sb.append(leftButton.getText());
            }
            sb.append(" Right=");
            if (jsp.getRightComponent() instanceof JButton rightButton) {
                sb.append(rightButton.getText());
            }
            System.out.println(sb);
        }
        if (jsp.getLeftComponent() == null) {
            throw new RuntimeException("JSplitPane repeated orientation change not working");
        }
    }

    public static void main(String[] args) throws Exception {
        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing LAF : " + laf.getClassName());

            SwingUtilities.invokeAndWait(() -> {
                setLookAndFeel(laf);
                JSplitPane jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                                new JButton("Left"), new JButton("Right"));
                jsp.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
                if (jsp.getRightComponent() instanceof JButton button) {
                    System.out.println(button.getText());
                    if (!button.getText().equals("Left")) {
                        throw new RuntimeException("JSplitPane did not support ComponentOrientation");
                    }
                }
                testSwitchOrientation();
            });
        }
    }

}

