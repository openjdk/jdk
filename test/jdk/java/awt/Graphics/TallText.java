/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4844952
 * @summary test large text draws properly to the screen
 * @key headful
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TallText
 */

import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.lang.reflect.InvocationTargetException;

public class TallText extends Frame {
    static String INSTRUCTIONS = """
            There should be a window called "Tall Text Test" that contains text "ABCDEFGHIJ".
            Test should be properly displayed: no missing letters
            and all letters fit within the frame without overlapping.
            If all letters are properly displayed press "Pass", otherwise press "Fail".
            """;

    public TallText() {
        setSize(800, 200);
        setTitle("Tall Text Test");
    }

    public void paint(Graphics g) {
        Font font = new Font("dialog", Font.PLAIN, 99);
        g.setFont(font);
        g.setColor(Color.black);
        g.drawString("ABCDEFGHIJ", 10, 150);
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Tall Text Instructions")
                .instructions(INSTRUCTIONS)
                .testUI(TallText::new)
                .build()
                .awaitAndCheck();
    }
}
