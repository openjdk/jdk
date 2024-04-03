/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6524424
 * @requires (os.family == "windows")
 * @summary JSlider clicking in tracks behavior inconsistent for different tick spacings
 * @modules java.desktop/com.sun.java.swing.plaf.windows
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug6524424
 */

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import com.sun.java.swing.plaf.windows.WindowsLookAndFeel;

public class bug6524424 {
    private static final String INSTRUCTIONS = """
          1. Select a slider (do the next steps for every slider)
          2. Check that the next keyboard buttons work correctly:
             Up, Down, Left, Right, Page Up, Page Down
          3. Press left mouse button on a free space of the slider
             check if thumb moves correctly
             press Pass else press Fail.""";

    public static void main(String[] args) throws Exception {
         PassFailJFrame.builder()
                .title("Slider Behavior Instructions")
                .instructions(INSTRUCTIONS)
                .rows(7)
                .columns(30)
                .testUI(bug6524424::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        try {
            UIManager.setLookAndFeel(new WindowsLookAndFeel());
        } catch (UnsupportedLookAndFeelException ex) {
            PassFailJFrame.forceFail(ex.toString());
            return null;
        }

        TestPanel panel = new TestPanel();

        JFrame frame = new JFrame("bug6524424");

        frame.setContentPane(panel);
        frame.pack();
        return frame;
    }

    private static class TestPanel extends JPanel {

        private TestPanel() {
            super(new GridBagLayout());

            JSlider slider1 = createSlider(1, 2);
            JSlider slider2 = createSlider(2, 4);
            JSlider slider3 = createSlider(3, 6);

            addComponent(this, slider1);
            addComponent(this, slider2);
            addComponent(this, slider3);
        }

        private JSlider createSlider(int tickMinor, int tickMajor) {
            JSlider result = new JSlider();

            result.setPaintLabels(true);
            result.setPaintTicks(true);
            result.setSnapToTicks(true);
            result.setMinimum(0);
            result.setMaximum(12);
            result.setMinorTickSpacing(tickMinor);
            result.setMajorTickSpacing(tickMajor);

            return result;
        }
    }

    private static void addComponent(JPanel panel, Component component) {
        panel.add(component, new GridBagConstraints(0,
                panel.getComponentCount(), 1, 1,
                1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                new Insets(0, 0, 0, 0), 0, 0));
    }
}
