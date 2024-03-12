/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4290656
 * @summary Tests if custom AbstractButton implementation fails with Metal L&F
 * @key headful
 */

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Robot;
import javax.swing.AbstractButton;
import javax.swing.DefaultButtonModel;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ButtonUI;

public class bug4290656 {
    static JFrame f;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                f = new JFrame("bug4290656");
                try {
                    UIManager.setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to set metal L&F.");
                }

                MyCustomButton button = new MyCustomButton();
                MyCustomToggleButton toggleButton = new MyCustomToggleButton();
                f.getContentPane().add(button, BorderLayout.NORTH);
                f.getContentPane().add(toggleButton, BorderLayout.SOUTH);

                f.setLocationRelativeTo(null);
                f.setVisible(true);
            });

            Robot r = new Robot();
            r.waitForIdle();
            r.delay(1000);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }
}

class MyCustomButton extends AbstractButton {
    private static final String uiClassID = "ButtonUI";

    public MyCustomButton() {
        setModel(new DefaultButtonModel());
        init(null, null);
    }

    public void updateUI() {
        setUI((ButtonUI) UIManager.getUI(this));
    }

    public String getUIClassID() {
        return uiClassID;
    }

    protected void paintBorder(Graphics g) {
        super.paintBorder(g);
    }
}

class MyCustomToggleButton extends AbstractButton {
    private static final String uiClassID = "ToggleButtonUI";

    public MyCustomToggleButton() {
        setModel(new DefaultButtonModel());
        init(null, null);
    }

    public void updateUI() {
        setUI((ButtonUI) UIManager.getUI(this));
    }

    public String getUIClassID() {
        return uiClassID;
    }

    protected void paintBorder(Graphics g) {
        super.paintBorder(g);
    }
}
