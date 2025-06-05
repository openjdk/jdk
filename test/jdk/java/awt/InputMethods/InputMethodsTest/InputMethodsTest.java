/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.TextArea;
import java.awt.TextField;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;

/*
 * @test
 * @bug 7146572 8024122
 * @summary Check if 'enableInputMethods' works properly for TextArea and TextField on Linux platform
 * @requires (os.family == "linux")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual InputMethodsTest
*/

public class InputMethodsTest {

    private static final String INSTRUCTIONS = """
        Test run requires some Japanese input method to be installed.

        To test the JDK-7146572 fix, please follow these steps:
        1. Enable the input method.
        2. Type Japanese in the text area and the text field to the right.
        2. Press the "Disable Input Methods" button.
        3. Try typing Japanese again.
        4. If input methods are not disabled, then press fail;
           otherwise, press pass.
        """;

    static boolean inputMethodsEnabled = true;

    public static void main(String[] args) throws Exception {
        PassFailJFrame
                .builder()
                .title("InputMethodsTest Instructions")
                .instructions(INSTRUCTIONS)
                .splitUIRight(InputMethodsTest::createPanel)
                .testTimeOut(10)
                .rows(10)
                .columns(40)
                .build()
                .awaitAndCheck();
    }

    public static JComponent createPanel() {
        Box verticalBox = Box.createVerticalBox();

        TextArea textArea = new TextArea();
        verticalBox.add(textArea);

        TextField textField = new TextField();
        verticalBox.add(textField);

        JButton btnIM = new JButton();
        setBtnText(btnIM);

        btnIM.addActionListener(e -> {
            inputMethodsEnabled = !inputMethodsEnabled;
            setBtnText(btnIM);
            textArea.enableInputMethods(inputMethodsEnabled);
            textField.enableInputMethods(inputMethodsEnabled);
        });

        verticalBox.add(btnIM);
        return verticalBox;
    }

    private static void setBtnText(JButton btnIM) {
        String s = inputMethodsEnabled ? "Disable" : "Enable";
        btnIM.setText(s +  " Input Methods");
    }
}

