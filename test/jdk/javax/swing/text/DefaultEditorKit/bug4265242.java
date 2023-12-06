/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.JTextPane;
import javax.swing.text.DefaultEditorKit;

/*
 * @test
 * @bug 4265242
 * @summary Tests endParagraphAction in JTextPane
 */

public class bug4265242 {
    public static void main(String[] args) {
        JTextPane jTextPane = new JTextPane();
        jTextPane.setText("Merry sparrow");

        Action[] actions = jTextPane.getActions();
        Action endPara = null;
        for (Action action : actions) {
            String name = (String) action.getValue(Action.NAME);
            if (name.equals(DefaultEditorKit.endParagraphAction)) {
                endPara = action;
            }
        }
        endPara.actionPerformed(new ActionEvent(jTextPane,
                ActionEvent.ACTION_PERFORMED,
                DefaultEditorKit.endParagraphAction));
    }
}
