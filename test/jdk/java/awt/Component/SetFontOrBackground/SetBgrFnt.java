/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4906548 4921849
 * @summary Checks that setFont and setBackground have immediate effect
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SetBgrFnt
 */

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.lang.reflect.InvocationTargetException;

public class SetBgrFnt extends Frame {
    static final String INSTRUCTIONS = """
            1. Press a button marked 'Switch fonts'
               fonts in three components below (a Button, a Checkbox
               and a Label) must change immediately.

            2. Press a button marked 'Switch background'
               background of three components and canvas must change.
               MacOS is an exception - AWT buttons on macOS so not
               change color so on macOS only canvas, checkbox
               and a label should change background.

            If this is the behavior that you observe press Pass,
            otherwise press Fail.
            """;
    Label la;
    Button bu, bu1, bu2;
    Checkbox cb;
    Font font1, font2;
    Canvas ca;
    boolean bToggleFont = true;
    boolean bToggleBg = true;

    public SetBgrFnt() {
        bu = new Button("Switch fonts");
        bu1 = new Button("Switch background");
        bu2 = new Button("I'm a button");
        cb = new Checkbox("Checkbox I am");
        la = new Label("I am a label");
        ca = new Canvas();
        font1 = new Font("Serif", Font.ITALIC, 22);
        font2 = new Font("SansSerif", Font.PLAIN, 10);
        la.setFont(font1);
        cb.setFont(font1);
        bu2.setFont(font1);
        bu.addActionListener(ae -> {
            if (bToggleFont) {
                la.setFont(font2);
                cb.setFont(font2);
                bu2.setFont(font2);
            } else {
                la.setFont(font1);
                cb.setFont(font1);
                bu2.setFont(font1);
            }
            bToggleFont = !bToggleFont;
        });

        bu1.addActionListener(ae -> {
            if (bToggleBg) {
                ca.setBackground(Color.YELLOW);
                setBackground(Color.YELLOW);
            } else {
                ca.setBackground(Color.GREEN);
                setBackground(Color.GREEN);
            }
            bToggleBg = !bToggleBg;
        });

        setLayout(new GridLayout(8, 1));
        add(bu);
        add(bu1);
        add(new Label());
        add("South", la);
        add("South", bu2);
        add("South", cb);
        add("South", ca);
        pack();
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Set Font and Background Test")
                .testUI(SetBgrFnt::new)
                .instructions(INSTRUCTIONS)
                .columns(40)
                .build()
                .awaitAndCheck();
    }
}
