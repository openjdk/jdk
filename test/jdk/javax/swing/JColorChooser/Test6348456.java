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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JFrame;
import javax.swing.colorchooser.DefaultColorSelectionModel;

/*
 * @test
 * @bug 6348456
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests model changing
 * @run main/manual Test6348456
 */

public final class Test6348456 {

    private static final DefaultColorSelectionModel WHITE =
            new DefaultColorSelectionModel(Color.WHITE);
    private static final DefaultColorSelectionModel BLACK =
            new DefaultColorSelectionModel(Color.BLACK);

    private static JColorChooser chooser;

    public static void main(String[] args) throws Exception {
        String instructions = "When test starts, you'll see that the preview is white.\n" +
                "When you swap models, you'll see that the preview color is changed.\n" +
                "Click pass if so, otherwise fail.";

        PassFailJFrame.builder()
                .title("Test6348456")
                .instructions(instructions)
                .rows(5)
                .columns(40)
                .testTimeOut(10)
                .testUI(Test6348456::test)
                .build()
                .awaitAndCheck();
    }

    public static JFrame test() {
        JFrame frame = new JFrame("JColor Swap Models Test");
        JButton button = new JButton("Swap models");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                chooser.setSelectionModel(chooser.getSelectionModel() == BLACK ? WHITE : BLACK);

            }
        });

        chooser = new JColorChooser(Color.RED);
        chooser.setSelectionModel(WHITE);

        frame.add(BorderLayout.NORTH, button);
        frame.add(BorderLayout.CENTER, chooser);
        frame.pack();

        return frame;
    }
}
