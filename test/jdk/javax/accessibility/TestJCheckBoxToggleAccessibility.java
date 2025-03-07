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
                <html><body>
                <p><b>Testing with VoiceOver</b></p>

                <ol>
                  <li>Start the VoiceOver application
                      (Press <kbd>Command</kbd> + <kbd>F5</kbd>)
                  <li>Click on the <i>Frame with CheckBox and ToggleButton</i>
                      window to move focus
                  <li>Press <kbd>Spacebar</kbd>
                  <li>VO should announce the checked state
                  <li>Press <kbd>Spacebar</kbd> again
                  <li>VO should announce the unchecked state
                  <li>Press <kbd>Tab</kbd> to move focus to <i>ToggleButton</i>
                  <li>Repeat steps 3 to 6 and listen the announcement
                  <li>If announcements are incorrect, press <b>Fail</b>
                  <li>Stop the VoiceOver application
                      (Press <kbd>Command</kbd> + <kbd>F5</kbd> again)
                </ol>

                <p><b>Testing with Screen Magnifier</b></p>
                <ol style="margin-bottom: 0">
                  <li>Enable Screen magnifier on the Mac:
                   <b>System Settings</b> -> <b>Accessibility</b> ->
                   <b>Hover Text</b> -> Enable <b>Hover Text</b><br>
                   Default Hover Text Activation Modifier is <kbd>Command</kbd> key
                  <li>Move focus back to the test application and perform the following tests

                  <ul style="margin-bottom: 0">
                    <li>Test <i>CheckBox</i> states with Screen Magnifier
                      <ol style="list-style-type: lower-alpha; margin-top: 0; margin-bottom: 0">
                        <li>Click on <i>CheckBox</i> to select it
                        <li>Press the <kbd>Command</kbd> key and
                            hover mouse over <i>CheckBox</i>
                        <li>CheckBox ticked state along with its label should be magnified
                        <li>Keep the <kbd>Command</kbd> key pressed and
                            click <i>CheckBox</i> to deselect it
                        <li>CheckBox unticked state along with its label should be magnified
                        <li>Release the <kbd>Command</kbd> key
                        <li>If Screen Magnifier behaviour is incorrect, press <b>Fail</b>
                      </ol>
                    <li>Test <i>ToggleButton</i> states with Screen Magnifier
                      <ol style="list-style-type: lower-alpha; margin-top: 0; margin-bottom: 0">
                        <li>Click on <i>ToggleButton</i> to select it
                        <li>Press the <kbd>Command</kbd> key and
                            hover mouse over <i>ToggleButton</i>
                        <li>Ticked state along with label should be magnified
                        <li>Keep the <kbd>Command</kbd> button pressed and
                            click <i>ToggleButton</i> to deselect it
                        <li>Unticked state along with its label should be magnified
                        <li>Release the <kbd>Command</kbd> key
                        <li>If Screen Magnifier behaviour is incorrect, press <b>Fail</b>
                      </ol>
                  </ul>
                  <li>Disable <b>Hover Text</b> (optionally) in the Settings
                </ol>

                <p>Press <b>Pass</b> if you are able to hear correct VoiceOver announcements and
                able to see the correct screen magnifier behaviour.</p></body></html>""";

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
