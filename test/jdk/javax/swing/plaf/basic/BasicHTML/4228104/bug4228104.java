/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4228104
 * @summary Tests work of BODY BACKGROUND tag in HTML renderer
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4228104
 */

import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JLabel;

public class bug4228104 {
    static final String INSTRUCTIONS = """
        There should be an image displaying dukes under the rows of digits.
        If you can see it, the test PASSES. Otherwise, the test FAILS.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4228104 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4228104::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame f = new JFrame("Background HTML Text Test");
        String dir = System.getProperty("test.src",
                System.getProperty("user.dir"));
        String htmlText1 =
                "<html><BODY BACKGROUND=\"file:" + dir
                        + "/duke.gif\">\n" +
                        "<br>111111111111111111" +
                        "<br>111111111111111111" +
                        "<br>111111111111111111" +
                        "<br>111111111111111111" +
                        "<br>111111111111111111" +
                        "<br>111111111111111111" +
                        "<br>111111111111111111" +
                        "<br>111111111111111111" +
                        "<br>111111111111111111";
        JLabel button1 = new JLabel(htmlText1);
        f.add(button1, BorderLayout.NORTH);
        f.setSize(200, 200);
        return f;
    }
}
