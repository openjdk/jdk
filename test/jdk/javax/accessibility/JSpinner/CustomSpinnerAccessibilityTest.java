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

import java.awt.GridLayout;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerListModel;

/*
 * @test
 * @bug 8286258
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "mac")
 * @summary Checks that JSpinner with custom model announces
 *          the value every time it is changed
 * @run main/manual CustomSpinnerAccessibilityTest
 */

public class CustomSpinnerAccessibilityTest extends JPanel {
    private static final String INSTRUCTIONS = """
            1. Turn on VoiceOver
            2. In the window named "Test UI" click on the text editor inside the
               spinner component
            3. Using up and down arrows change current month
            4. Wait for the VoiceOver to finish speaking
            5. Repeat steps 3 and 4 couple more times

            If every time value of the spinner is changed VoiceOver
            announces the new value click "Pass".
            If instead the value is narrated only partially
            and the new value is never fully narrated press "Fail".
            """;

    public CustomSpinnerAccessibilityTest() {
        super(new GridLayout(0, 2));
        String[] monthStrings = new java.text.DateFormatSymbols().getMonths();
        int lastIndex = monthStrings.length - 1;
        if (monthStrings[lastIndex] == null
                || monthStrings[lastIndex].length() <= 0) {
            String[] tmp = new String[lastIndex];
            System.arraycopy(monthStrings, 0,
                    tmp, 0, lastIndex);
            monthStrings = tmp;
        }

        SpinnerListModel model = new SpinnerListModel(monthStrings);
        JLabel label = new JLabel("Month: ");
        add(label);
        JSpinner spinner = new JSpinner(model);
        label.setLabelFor(spinner);
        add(spinner);
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Custom Spinner Accessibility Test")
                .instructions(INSTRUCTIONS)
                .testUI(CustomSpinnerAccessibilityTest::new)
                .build()
                .awaitAndCheck();
    }
}
