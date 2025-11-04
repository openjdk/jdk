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
 * @bug 4991587
 * @requires (os.family == "windows")
 * @summary Tests that disabled JButton text is positioned properly in Windows L&F
 * @modules java.desktop/com.sun.java.swing.plaf.windows
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4991587
 */

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Rectangle;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.UIManager;

import com.sun.java.swing.plaf.windows.WindowsButtonUI;

public class bug4991587 {
    static final String INSTRUCTIONS = """
        There are two buttons: enabled (left) and disabled (right).
        Ensure that the disabled button text is painted entirely
        inside the blue bounding rectangle, just like the enabled
        button (use it as an example of how this should look like).
    """;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        PassFailJFrame.builder()
                .title("bug4991587 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4991587::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame f = new JFrame("Disabled JButton Text Test");
        f.setLayout(new FlowLayout());
        f.setSize(400, 100);

        JButton button1 = new JButton("\u0114 Enabled JButton");
        button1.setUI(new MyButtonUI());
        f.add(button1);

        JButton button2 = new JButton("\u0114 Disabled JButton");
        button2.setEnabled(false);
        button2.setUI(new MyButtonUI());
        f.add(button2);

        return f;
    }

    static class MyButtonUI extends WindowsButtonUI {
        protected void paintText(Graphics g, AbstractButton b,
                                 Rectangle textRect, String text) {
            g.setColor(Color.blue);
            g.drawRect(textRect.x,
                    textRect.y,
                    textRect.width + 1, // add 1 for the shadow, otherwise it
                                        // will be painted over the textRect
                    textRect.height);
            super.paintText(g, b, textRect, text);
        }
    }
}
