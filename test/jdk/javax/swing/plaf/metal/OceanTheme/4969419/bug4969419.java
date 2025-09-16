/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4969419
 * @summary Tests that generated disabled icons have same look with Ocean
 *          and are updated when theme is switched
 * @modules java.desktop/sun.awt
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @key headful
 * @run main/manual bug4969419
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.LookAndFeel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.MetalTheme;

import sun.awt.AppContext;

public class bug4969419 {
    static final String INSTRUCTIONS = """
        When the test starts you'll see several components with icons.
        Use the bottom combo box and the "Set" button to switch between
        the Ocean theme and DefaultMetalTheme.

        1. Set the Ocean theme. Ensure all the icons are the same
           Note that they all are of the same brightness: none of them
           can be brighter or darker than the others.

        2. Switch to DefaultMetalTheme. Ensure all the icons changed
           (became slightly darker).

        3. Switch back to Ocean. Ensure all the icons changed
           (became brighter).
    """;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        PassFailJFrame.builder()
                .title("bug4969419 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4969419::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame frame = new JFrame("Metal Themes Icon Test");
        Container pane = frame.getContentPane();

        LFSwitch lfswitch = new LFSwitch(pane);
        if (!lfswitch.obtainOceanTheme()) {
            throw new RuntimeException("No Ocean theme available");
        }

        pane.setLayout(new BoxLayout(pane, BoxLayout.Y_AXIS));

        String prefix = System.getProperty("test.src",
                System.getProperty("user.dir")) + System.getProperty("file.separator");
        ImageIcon icon = new ImageIcon(prefix + "duke.gif");

        JPanel panel = new JPanel();
        JButton b = new JButton(icon);
        b.setEnabled(false);

        JLabel label = new JLabel(icon, SwingConstants.LEFT);
        label.setEnabled(false);

        JTabbedPane tp = new JTabbedPane();
        tp.addTab("", icon, new JPanel());
        tp.addTab("", icon, new JPanel());
        tp.setEnabledAt(0, false);
        tp.setEnabledAt(1, false);

        JButton sb = new JButton(icon);
        sb.setSelectedIcon(icon);
        sb.setSelected(true);
        sb.setEnabled(false);

        JToggleButton tb = new JToggleButton(icon);
        tb.setEnabled(false);

        JToggleButton stb = new JToggleButton(icon);
        stb.setSelectedIcon(icon);
        stb.setSelected(true);
        stb.setEnabled(false);

        pane.setBackground(Color.white);
        panel.setBackground(Color.white);
        b.setBackground(Color.white);
        label.setBackground(Color.white);
        tp.setBackground(Color.white);
        sb.setBackground(Color.white);
        tb.setBackground(Color.white);
        stb.setBackground(Color.white);

        panel.add(b);
        panel.add(label);
        panel.add(tp);
        panel.add(sb);
        panel.add(tb);
        panel.add(stb);

        pane.add(panel);
        pane.add(lfswitch);
        frame.setSize(400, 400);
        return frame;
    }

    static class LFSwitch extends JPanel {
        private Component target;
        static MetalTheme oceanTheme;
        JComboBox lfcombo;

        public LFSwitch(Component target) {
            this.target = target;
            setLayout(new FlowLayout());
            lfcombo = new JComboBox(lookAndFeels);
            add(lfcombo);
            JButton setLfBut = new JButton("Set");
            add(setLfBut);
            setLfBut.addActionListener(e -> setLf(lfcombo.getSelectedIndex(),
                    LFSwitch.this.target));
        }

        boolean obtainOceanTheme() {
            if (oceanTheme != null) {
                return true;
            }
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
                SwingUtilities.updateComponentTreeUI(this);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "Unexpected error: couldn't set Metal", "Error",
                        JOptionPane.ERROR_MESSAGE);
                return false;
            }
            MetalTheme theme = (MetalTheme) AppContext.getAppContext().
                    get("currentMetalTheme");
            if (theme == null || theme.getName() != "Ocean") {
                JOptionPane.showMessageDialog(this,
                        "The Ocean theme is not the default Metal theme,\n" +
                                "but this test requires it to be default.\n" +
                                "Therefore simply click PASS");
                return false;
            }
            oceanTheme = theme;
            return true;
        }

        void setLf(int idx, final Component root) {
            try {
                UIManager.setLookAndFeel((LookAndFeel) lfs[idx]);
                if (root != null) {
                    SwingUtilities.updateComponentTreeUI(root);
                }
            } catch (UnsupportedLookAndFeelException e) {
                JOptionPane.showMessageDialog(root,
                        "The selected look and feel is unsupported on this platform",
                        "Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception exc) {
                JOptionPane.showMessageDialog(root,
                        "Error setting the selected look and feel", "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        static Object[] lookAndFeels = {
                "Metal (Ocean)", "Metal (DefaultMetalTheme)",
        };
        static Object[] lfs = {
                new MetalLookAndFeel() {
                    protected void createDefaultTheme() {
                        setCurrentTheme(oceanTheme);
                    }
                },
                new MetalLookAndFeel() {
                    protected void createDefaultTheme() {
                        MetalTheme dt = new DefaultMetalTheme();
                        setCurrentTheme(dt);
                    }
                },
        };
    }
}
