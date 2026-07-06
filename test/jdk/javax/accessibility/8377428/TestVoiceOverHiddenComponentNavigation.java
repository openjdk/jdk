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

import javax.accessibility.AccessibleComponent;
import javax.accessibility.AccessibleContext;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

/*
 * @test
 * @key headful
 * @bug 8377428
 * @summary manual test for VoiceOver reading hidden components
 * @requires os.family == "mac"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestVoiceOverHiddenComponentNavigation
 */

public class TestVoiceOverHiddenComponentNavigation {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Test UI contains four rows. Each row contains a JButton.
                Two of the rows are hidden, and two are visible.

                Follow these steps to test the behaviour:

                1. Start the VoiceOver (Press Command + F5) application
                2. Move VoiceOver cursor to one of the visible buttons.
                3. Press CTRL + ALT + LEFT to move the VoiceOver cursor back
                4. Repeat step 3 until you reach the "Close" button.

                If VoiceOver ever references a "Hidden Button": then this test
                fails.
                """;

        PassFailJFrame.builder()
                .title("TestVoiceOverHiddenComponentNavigation Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(TestVoiceOverHiddenComponentNavigation::createUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.add(createRow("Hidden Button", "Row 1", false, false));
        rows.add(createRow("Hidden Button", "Row 2", false, true));
        rows.add(createRow("Visible Button", "Row 3", true, false));
        rows.add(createRow("Visible Button", "Row 4", true, true));

        JFrame frame = new JFrame("A Frame hidden JButtons");
        frame.getContentPane().add(rows);
        frame.pack();
        return frame;
    }

    /**
     * Create a row to add to this demo frame.
     *
     * @param buttonText the button name/text
     * @param panelAXName the panel accessible name
     * @param isVisible whether JPanel.isVisible() should be true
     * @param useNullAXComponent if true then
     *                           AccessibleJPanel.getAccessibleComponent
     *                           returns null. This was added to test a
     *                           particular code path.
     * @return a row for the demo frame
     */
    private static JPanel createRow(String buttonText, String panelAXName,
                                    boolean isVisible,
                                    boolean useNullAXComponent) {
        JPanel returnValue = new JPanel() {
            @Override
            public AccessibleContext getAccessibleContext() {
                if (accessibleContext == null) {
                    accessibleContext = new AccessibleJPanel() {
                        @Override
                        public AccessibleComponent getAccessibleComponent() {
                            if (useNullAXComponent) {
                                return null;
                            } else {
                                return super.getAccessibleComponent();
                            }
                        }
                    };
                    accessibleContext.setAccessibleName(panelAXName);
                }
                return accessibleContext;
            }
        };
        returnValue.setVisible(isVisible);
        JButton button = new JButton(buttonText);
        returnValue.add(button);
        return returnValue;
    }
}