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
 * @bug 4207339
 * @summary Verifies HTML label support for MenuItems
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4207339
 */

import javax.swing.JPanel;
import javax.swing.JMenuItem;

public class bug4207339 {

    private static final String INSTRUCTIONS = """
        This tests html support in menuItem.
        A MenuItem will be shown.
        If the MenuItem is showing "big" text bigger than rest
        and "red" text in red color and the text are in multiple lines,
        and text "Yo" in blue color,
        then press Pass else press Fail.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4207339 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .splitUI(bug4207339::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JPanel createTestUI() {
        JPanel panel = new JPanel();
        JMenuItem mi = new JMenuItem("<html><center>Is this text <font size=+3>big</font>" +
                                     "and <font color=red>red</font>?" +
                                     "<br>And on multiple lines?<br><br>" +
                                     "<font size=+2 color=blue face=AvantGarde>Yo!</font>" +
                                     "Then press <em><b>PASS</b>!</center></html>");
        panel.add(mi);
        return panel;
    }

}
