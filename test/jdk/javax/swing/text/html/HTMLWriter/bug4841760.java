/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4841760
 * @summary  Tests if HTML tags are correctly shown for
             StyleEditorKit.ForegroundAction() in JTextPane output.
 * @run main bug4841760
 */

import javax.swing.JTextPane;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTMLEditorKit;

public class bug4841760 {

    public static void main(String[] args) throws Exception {
        JTextPane jep = new JTextPane();
        jep.setEditorKit(new HTMLEditorKit());
        jep.setText("<html><head></head><body><font size=3>hellojavaworld</font></body></html>");

        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setForeground(set, java.awt.Color.BLUE);
        jep.getStyledDocument().setCharacterAttributes(3, 5, set, false);

        String gotText = jep.getText();
        System.out.println("gotText: " + gotText);
        // there should be color attribute set
        // and 3 font tags
        int i = gotText.indexOf("color");
        if (i > 0) {
            i = gotText.indexOf("<font");
            if (i > 0) {
                i = gotText.indexOf("<font", i + 1);
                if (i > 0) {
                    i = gotText.indexOf("<font", i + 1);
                    if (i <= 0) {
                        throw new RuntimeException("Test failed.");
                    }
                }
            }
        }

    }
}
