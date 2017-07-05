/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4449413
 * @summary Tests that checkbox and radiobuttons' check marks are visible when background is black
 * @author Ilya Boyandin
 * @library ../../../../lib/testlibrary
 * @build jdk.testlibrary.OSInfo
 * @run applet/manual=yesno bug4449413.html
 */

import javax.swing.*;
import javax.swing.plaf.metal.*;
import java.awt.event.*;
import java.awt.*;
import jdk.testlibrary.OSInfo;

public class bug4449413 extends JApplet {

    @Override
    public void init() {

        try {

            if (OSInfo.getOSType() == OSInfo.OSType.MACOSX) {
                UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            }

            final MetalTheme oceanTheme = (MetalTheme) sun.awt.AppContext.getAppContext().get("currentMetalTheme");


            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run() {
                    getContentPane().setLayout(new FlowLayout());
                    final JPanel panel = new JPanel();

                    JCheckBox box = new JCheckBox("Use Ocean theme", true);
                    getContentPane().add(box);
                    box.addItemListener(new ItemListener() {

                        @Override
                        public void itemStateChanged(ItemEvent e) {
                            if (e.getStateChange() == ItemEvent.SELECTED) {
                                MetalLookAndFeel.setCurrentTheme(oceanTheme);
                            } else {
                                MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
                            }
                            SwingUtilities.updateComponentTreeUI(panel);
                        }
                    });

                    getContentPane().add(panel);
                    panel.setLayout(new GridLayout(4, 6, 10, 15));
                    for (int k = 0; k <= 3; k++) {
                        for (int j = 1; j >= 0; j--) {
                            AbstractButton b = createButton(j, k);
                            panel.add(b);
                        }
                    }
                }
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static AbstractButton createButton(int enabled, int type) {
        AbstractButton b = null;
        switch (type) {
            case 0:
                b = new JRadioButton("RadioButton");
                break;
            case 1:
                b = new JCheckBox("CheckBox");
                break;
            case 2:
                b = new JRadioButtonMenuItem("RBMenuItem");
                break;
            case 3:
                b = new JCheckBoxMenuItem("CBMenuItem");
                break;
        }
        b.setBackground(Color.black);
        b.setForeground(Color.white);
        b.setEnabled(enabled == 1);
        b.setSelected(true);
        return b;
    }
}
