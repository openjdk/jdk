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

import javax.swing.JFrame;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;

/*
 * @test
 * @bug 8286204
 * @summary Verifies that VoiceOver announces the JSpinner's value correctly
 * @requires os.family == "mac"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestJSpinnerAccessibility
 */

public class TestJSpinnerAccessibility {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Test UI contains a JSpinner with minimum value 0, maximum value 20
                and current value 5. On press of up / down arrow, value will be
                incremented / decremented by 1.

                Follow these steps to test the behaviour:

                1. Start the VoiceOver (Press Command + F5) application
                2. Move focus on test window if it is not focused
                3. Press Up / Down arrow to increase / decrease Spinner value
                4. VO should announce correct values in terms of percentage
                   (e.g. For JSpinner's value 10, VO should announce 50%)
                5. Press Pass if you are able to hear correct announcements
                   else Fail""";

        PassFailJFrame.builder()
                .title("TestJSpinnerAccessibility Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(TestJSpinnerAccessibility::createUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        JFrame frame = new JFrame("A Frame with JSpinner");
        SpinnerModel spinnerModel = new SpinnerNumberModel(5, 0, 20, 1);
        JSpinner spinner = new JSpinner(spinnerModel);
        frame.getContentPane().add(spinner, BorderLayout.CENTER);
        frame.setSize(200, 100);
        return frame;
    }
}
