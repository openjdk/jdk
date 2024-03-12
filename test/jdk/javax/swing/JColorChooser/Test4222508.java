/*
 * Copyright (c) 2008, 2024 Oracle and/or its affiliates. All rights reserved.
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
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JFrame;

/*
 * @test
 * @bug 4222508
 * @library /test/jdk/java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame
 * @summary Tests the color chooser disabling
 * @run main/manual Test4222508
 */
public final class Test4222508 {
    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Test4222508")
                .instructions("Click on colors in the JColorChooser. Then uncheck the checkbox \n" +
                        "and click on colors again. \n" +
                        "If the JColorChooser is disabled when the checkbox is unchecked, \n" +
                        "then pass the test.")
                .rows(5)
                .columns(40)
                .testTimeOut(10)
                .testUI(Test4222508::test)
                .build()
                .awaitAndCheck();
    }

    public static JFrame test() {
        JFrame frame = new JFrame();
        frame.setLayout(new BorderLayout());
        JColorChooser chooser = new JColorChooser();
        JCheckBox checkbox = new JCheckBox("Enable the color chooser below", true);
        checkbox.addItemListener(e -> chooser.setEnabled(checkbox.isSelected()));

        frame.getContentPane().add(chooser, BorderLayout.SOUTH);
        frame.getContentPane().add(checkbox, BorderLayout.NORTH);
        frame.pack();

        return frame;
    }

}
