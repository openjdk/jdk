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

import java.awt.BorderLayout;
import java.awt.GridLayout;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/*
 * @test
 * @bug 8348936 8345728
 * @summary Verifies that VoiceOver announces the untick state of CheckBox and
 *          ToggleButton when space key is pressed. Also verifies that CheckBox
 *          and ToggleButton untick state is magnified with Screen Magnifier.
 * @requires os.family == "mac"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestJCheckBoxToggleAccessibility
 */

public class TestJCheckBoxToggleAccessibility {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """

                Testing with VoiceOver

                1. Start the VoiceOver application (Press Command + F5)
                2. Click on Frame with CheckBox and ToggleButton window to move focus
                3. Press Spacebar
                4. VO should announce the checked state
                5. Press Spacebar again
                6. VO should announce the unchecked state
                7. Press Tab to move focus to ToggleButton
                8. Repeat steps 3 to 6 and listen the announcement
                9. If announcements are incorrect, press Fail

                Stop the VoiceOver application (Press Command + F5)

                Testing with Screen Magnifier
                1. Enable Screen magnifier on the Mac
                   System Preference -> Accessibility -> Hover Text -> Enable Hover Text
                   Default Hover Text Activation Modifier is "Command" key.
                2. Move focus back to test application

                    Test CheckBox states with Screen Magnifier
                        a. Click on CheckBox to select
                        b. Press Command key and hover mouse over CheckBox
                        c. CheckBox ticked state along with label should be magnified
                        d. Keep Command button pressed and click CheckBox to deselect
                        e. CheckBox unticked state along with label should be magnified
                        f. Release Command key
                        g. If Screen Magnifier behaviour is incorrect, press Fail

                    Test ToggleButton states with Screen Magnifier
                        a. Click on ToggleButton to select
                        b. Press Command key and hover mouse over ToggleButton
                        c. Ticked state along with label should be magnified
                        d. Keep Command button pressed and click ToggleButton to deselect
                        e. Unticked state along with label should be magnified
                        f. Release Command key
                        g. If Screen Magnifier behaviour is incorrect, press Fail

                Press Pass if you are able to hear correct VoiceOver announcements and
                able to see the correct screen magnifier behaviour. """;

        PassFailJFrame.builder()
                .title("TestJCheckBoxToggleAccessibility Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .rows(25)
                .testUI(TestJCheckBoxToggleAccessibility::createUI)
                .testTimeOut(8)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        JFrame frame = new JFrame("A Frame with CheckBox and ToggleButton");
        JCheckBox cb = new JCheckBox("CheckBox", false);
        JToggleButton tb = new JToggleButton("ToggleButton");

        JPanel p = new JPanel(new GridLayout(2, 1));
        p.add(cb);
        p.add(tb);
        frame.getContentPane().add(p, BorderLayout.CENTER);
        frame.setSize(400, 400);
        return frame;
    }
}
